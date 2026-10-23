package com.mementocraft.reallyalive.brain;

/**
 * A block world for training without Minecraft: a grid of block columns with steps, walls, water
 * and drops, and a body that moves by vanilla's player rules (walk and air acceleration, friction,
 * gravity, a 0.42 jump, 0.6 step-up, sneak edge stop, swimming and climbing out of water, fall
 * damage). The eyes cast rays into the blocks the way the game's eyes do.
 */
final class BlockWorld {
    static final int SIZE = 40;
    static final float GROUND_ACCEL = 0.098f;
    static final float AIR_ACCEL = 0.0196f;
    static final float WATER_ACCEL = 0.0196f;
    static final float GROUND_FRICTION = 0.546f;
    static final float AIR_FRICTION = 0.91f;
    static final float WATER_DRAG = 0.8f;
    static final float GRAVITY = 0.08f;
    static final float JUMP = 0.42f;
    static final float STEP = 0.6f;
    static final float FOV_H = 100f;
    static final float FOV_V = 70f;
    static final float RANGE = 24f;
    static final float NO_WATER = -100f;
    /** Ticks allowed per course, 20 seconds. */
    static final int COURSE_TICKS = 400;

    final Rng rng;
    /** Top of the solid column at each cell. */
    final float[][] top = new float[SIZE][SIZE];
    /** Water surface at each cell, or NO_WATER. */
    final float[][] water = new float[SIZE][SIZE];

    final float width;
    final float standHeight;
    float x, y, z, vx, vy, vz, yaw, pitch;
    boolean onGround = true;
    boolean horizontalCollision;
    boolean sneaking;
    float fallDistance;
    float health = 20f;
    int underWaterTicks;

    float goalX, goalY, goalZ;
    float lastDist;
    float lastFacing = ToyWorld.NOT_SEEN;
    int reached;
    int reachedHigh;
    int falls;

    // Hands: dig the block in the crosshair by holding attack, place a held block with use.
    static final float REACH = 4.5f;
    /** Dirt dug by hand: 0.75 seconds, as in vanilla. */
    static int DIG_TICKS = 15;
    /** Vanilla places at most one block every 4 ticks while use is held. */
    static final int USE_COOLDOWN = 4;
    int blocks;
    int digI = -1, digK = -1;
    int digTicks;
    int useCooldown;
    int dug, placed;
    float damageTaken;
    float pendingPain;
    float pendingPleasure;
    /** Pleasure for picking up an item; 0 turns the collecting motive off. */
    static float COLLECT_PLEASURE = 0f;
    /** Pleasure per tick of watching a block crack while digging it; 0 turns it off. */
    static float DIG_PLEASURE = 0f;

    BlockWorld(long seed, float width, float height) {
        rng = new Rng(seed);
        this.width = width;
        this.standHeight = height;
        x = SIZE / 2f + 0.5f;
        z = SIZE / 2f + 0.5f;
        placeGoal();
    }

    BlockWorld(long seed) {
        this(seed, 0.6f, 1.8f);
    }

    // ---------------------------------------------------------------- courses

    /** What stands between the villager and each new goal. */
    enum Course { FLAT, STEP_UP, LOW_WALL, POND, HIGH_WALL, DIG_THROUGH, BUILD_UP }

    /** Courses that need only moving. */
    static final Course[] MOVING = {Course.FLAT, Course.STEP_UP, Course.LOW_WALL, Course.POND, Course.HIGH_WALL};
    static Course[] courses = MOVING;
    Course course = Course.FLAT;
    int courseTicks;
    final int[] tried = new int[Course.values().length];
    final int[] done = new int[Course.values().length];

    /** Flat ground walled in at the far edge. */
    private void clear() {
        for (int i = 0; i < SIZE; i++) {
            for (int k = 0; k < SIZE; k++) {
                water[i][k] = NO_WATER;
                boolean border = i == 0 || k == 0 || i == SIZE - 1 || k == SIZE - 1;
                top[i][k] = border ? 4f : 0f;
            }
        }
    }

    /**
     * A new course: the villager is set down in the middle of flat ground (an episode, like waking up
     * somewhere new) and a goal appears 5 to 9 blocks away, with one obstacle in between.
     */
    void placeGoal() {
        clear();
        x = SIZE / 2f + 0.5f;
        z = SIZE / 2f + 0.5f;
        y = 0f;
        vx = vy = vz = 0f;
        fallDistance = 0f;
        onGround = true;
        course = courses[rng.nextInt(courses.length)];
        courseTicks = 0;
        tried[course.ordinal()]++;

        float a = rng.nextFloat() * 360f;
        float d = 5f + rng.nextFloat() * 4f;
        float dx = (float) -Math.sin(Math.toRadians(a));
        float dz = (float) Math.cos(Math.toRadians(a));
        goalX = (float) Math.floor(x + dx * d) + 0.5f;
        goalZ = (float) Math.floor(z + dz * d) + 0.5f;
        goalY = 0f;
        float mid = d * 0.55f;
        switch (course) {
            case STEP_UP -> {
                // The goal stands on a wide platform one block up: walk up to it and jump.
                for (int i = -3; i <= 3; i++) {
                    for (int k = -3; k <= 3; k++) {
                        setTop(goalX + i, goalZ + k, 1f);
                    }
                }
                goalY = 1f;
            }
            case LOW_WALL -> wall(dx, dz, mid, 4, 1f);
            case HIGH_WALL -> wall(dx, dz, mid, 2, 3f);
            // A wall too high to jump and too long to walk around: dig it down.
            case DIG_THROUGH -> wall(dx, dz, mid, 8, 2f);
            case BUILD_UP -> {
                // The goal stands on a platform two blocks up: place a block to make a step.
                for (int i = -3; i <= 3; i++) {
                    for (int k = -3; k <= 3; k++) {
                        setTop(goalX + i, goalZ + k, 2f);
                    }
                }
                goalY = 2f;
                blocks = Math.max(blocks, 4);
            }
            case POND -> {
                // A strip of water two blocks deep across the way: swim over and climb out.
                for (int w = -1; w <= 1; w++) {
                    for (int l = -5; l <= 5; l++) {
                        float px = x + dx * (mid + w) + dz * l;
                        float pz = z + dz * (mid + w) - dx * l;
                        setTop(px, pz, -2f);
                        setWater(px, pz, -0.1f);
                    }
                }
            }
            default -> { }
        }
        lastDist = dist();
        lastFacing = ToyWorld.NOT_SEEN;
    }

    /** A wall across the way, halfLength blocks to each side. */
    private void wall(float dx, float dz, float mid, int halfLength, float h) {
        for (int l = -halfLength; l <= halfLength; l++) {
            for (float t = 0f; t <= 1f; t += 0.5f) {
                setTop(x + dx * (mid + t) + dz * l, z + dz * (mid + t) - dx * l, h);
            }
        }
    }

    private void setTop(float px, float pz, float h) {
        int i = (int) Math.floor(px), k = (int) Math.floor(pz);
        if (i > 0 && k > 0 && i < SIZE - 1 && k < SIZE - 1) {
            top[i][k] = h;
        }
    }

    private void setWater(float px, float pz, float level) {
        int i = (int) Math.floor(px), k = (int) Math.floor(pz);
        if (i > 0 && k > 0 && i < SIZE - 1 && k < SIZE - 1) {
            water[i][k] = level;
        }
    }

    float topAt(float px, float pz) {
        int i = (int) Math.floor(px), k = (int) Math.floor(pz);
        if (i < 0 || k < 0 || i >= SIZE || k >= SIZE) {
            return 10f;
        }
        return top[i][k];
    }

    float waterAt(float px, float pz) {
        int i = (int) Math.floor(px), k = (int) Math.floor(pz);
        if (i < 0 || k < 0 || i >= SIZE || k >= SIZE) {
            return NO_WATER;
        }
        return water[i][k];
    }

    /** Highest block top under the body's footprint. */
    float floorUnder(float px, float pz) {
        float h = width / 2f - 1e-4f;
        float m = -100f;
        for (int i = (int) Math.floor(px - h); i <= (int) Math.floor(px + h); i++) {
            for (int k = (int) Math.floor(pz - h); k <= (int) Math.floor(pz + h); k++) {
                m = Math.max(m, topAt(i + 0.5f, k + 0.5f));
            }
        }
        return m;
    }

    float height() {
        return sneaking ? standHeight * 1.5f / 1.8f : standHeight;
    }

    float eye() {
        return height() * 0.9f;
    }

    boolean inWater() {
        return y < waterAt(x, z);
    }

    boolean underWater() {
        return y + eye() < waterAt(x, z);
    }

    /** True when the body at (px, py, pz) would overlap no block. */
    boolean fits(float px, float py, float pz) {
        return floorUnder(px, pz) <= py + 1e-4f;
    }

    // ---------------------------------------------------------------- goal

    float dist() {
        return (float) Math.sqrt(sq(goalX - x) + sq(goalY - y) + sq(goalZ - z));
    }

    float flatDist() {
        return (float) Math.hypot(goalX - x, goalZ - z);
    }

    float bearing() {
        float toGoal = (float) Math.toDegrees(Math.atan2(-(goalX - x), goalZ - z));
        float b = toGoal - yaw;
        while (b > 180f) b -= 360f;
        while (b < -180f) b += 360f;
        return b;
    }

    // ---------------------------------------------------------------- senses

    /** Half the angle between neighbouring rays, so nothing can hide between two of them. */
    static final float COL_SPREAD = (float) Math.tan(Math.toRadians(FOV_H / SenseLayout.VISION_W / 2f));
    static final float ROW_SPREAD = (float) Math.tan(Math.toRadians(FOV_V / SenseLayout.VISION_H / 2f));

    private boolean goalHit(float px, float py, float pz, float s) {
        float r = 0.3f + s * COL_SPREAD;
        float v = s * ROW_SPREAD;
        return Math.abs(px - goalX) < r && Math.abs(pz - goalZ) < r && py >= goalY - v && py <= goalY + 1.8f + v;
    }

    boolean goalSeen;

    void sense(SensoryFrame f) {
        f.clear();
        goalSeen = false;
        float ex = x, ey = y + eye(), ez = z;
        for (int row = 0; row < SenseLayout.VISION_H; row++) {
            float p = Math.max(-90f, Math.min(90f, pitch + ((row + 0.5f) / SenseLayout.VISION_H - 0.5f) * FOV_V));
            for (int col = 0; col < SenseLayout.VISION_W; col++) {
                float yw = yaw + ((col + 0.5f) / SenseLayout.VISION_W - 0.5f) * FOV_H;
                float cp = (float) Math.cos(Math.toRadians(p));
                float dx = (float) -Math.sin(Math.toRadians(yw)) * cp;
                float dy = (float) -Math.sin(Math.toRadians(p));
                float dz = (float) Math.cos(Math.toRadians(yw)) * cp;
                float near = 0f, solid = 0f, creature = 0f;
                for (float s = 0.1f; s < RANGE; s += 0.2f) {
                    float px = ex + dx * s, py = ey + dy * s, pz = ez + dz * s;
                    if (goalHit(px, py, pz, s)) {
                        near = 1f - s / RANGE;
                        creature = 1f;
                        goalSeen = true;
                        break;
                    }
                    if (py < topAt(px, pz)) {
                        near = 1f - s / RANGE;
                        solid = 1f;
                        break;
                    }
                    if (py < waterAt(px, pz) && ey >= waterAt(px, pz)) {
                        near = 1f - s / RANGE;
                        solid = 0.4f;
                        break;
                    }
                }
                f.vision[SenseLayout.visionIndex(col, row, SenseLayout.CH_NEAR)] = near;
                f.vision[SenseLayout.visionIndex(col, row, SenseLayout.CH_SOLID)] = solid;
                f.vision[SenseLayout.visionIndex(col, row, SenseLayout.CH_CREATURE)] = creature;
            }
        }
        f.set(OlfactoryChannel.GOAL, 1f / (1f + dist() / 4f));
        f.set(OlfactoryChannel.WATER, waterAt(x, z) != NO_WATER ? 0.5f : 0f);

        f.set(FeelChannel.ON_GROUND, onGround ? 1f : 0f);
        f.set(FeelChannel.IN_WATER, inWater() ? 1f : 0f);
        f.set(FeelChannel.UNDER_WATER, underWater() ? 1f : 0f);
        float sin = (float) Math.sin(Math.toRadians(yaw));
        float cos = (float) Math.cos(Math.toRadians(yaw));
        float fx = -sin, fz = cos;
        float probe = 0.05f;
        f.set(FeelChannel.TOUCH_FRONT, fits(x + fx * probe, y, z + fz * probe) ? 0f : 1f);
        f.set(FeelChannel.TOUCH_BACK, fits(x - fx * probe, y, z - fz * probe) ? 0f : 1f);
        f.set(FeelChannel.TOUCH_LEFT, fits(x + fz * probe, y, z - fx * probe) ? 0f : 1f);
        f.set(FeelChannel.TOUCH_RIGHT, fits(x - fz * probe, y, z + fx * probe) ? 0f : 1f);
        float fwd = vx * fx + vz * fz;
        float left = vx * fz - vz * fx;
        f.set(FeelChannel.VEL_FORWARD, fwd * 4f);
        f.set(FeelChannel.VEL_BACKWARD, -fwd * 4f);
        f.set(FeelChannel.VEL_LEFT, left * 4f);
        f.set(FeelChannel.VEL_RIGHT, -left * 4f);
        f.set(FeelChannel.VEL_UP, vy * 2.5f);
        f.set(FeelChannel.VEL_DOWN, -vy * 2.5f);
        f.set(FeelChannel.HEAD_UP, -pitch / 90f);
        f.set(FeelChannel.HEAD_DOWN, pitch / 90f);
        f.set(FeelChannel.FALL_DISTANCE, fallDistance / 10f);
        f.set(FeelChannel.HEALTH, health / 20f);
        f.set(FeelChannel.SNEAKING, sneaking ? 1f : 0f);
        f.set(FeelChannel.SWIMMING, inWater() ? 1f : 0f);
        f.set(FeelChannel.HOLDING_BLOCK, blocks > 0 ? 1f : 0f);
        f.set(FeelChannel.TARGET_IN_REACH, target() != null ? 1f : 0f);
        f.set(FeelChannel.DIG_PROGRESS, digTicks / (float) DIG_TICKS);

        // Same feelings as the game: nearing and facing a goal it can see feel good, spotting it is a
        // small pleasure, damage hurts.
        float d = dist();
        float b = bearing();
        float facing = goalSeen ? 1f - Math.abs(b) / (FOV_H / 2f) : ToyWorld.NOT_SEEN;
        float feeling = 0f;
        if (goalSeen && lastFacing != ToyWorld.NOT_SEEN) {
            feeling = (lastDist - d) * 4f + (facing - lastFacing) * 4f;
        } else if (goalSeen) {
            feeling = 0.3f;
        }
        // No feelings for nearing a goal it cannot see: rewarding scent-following made every move
        // away from an unseen goal hurt, and brains stopped walking at all.
        f.pleasure = Math.max(0f, feeling) + pendingPleasure;
        pendingPleasure = 0f;
        f.pain = Math.max(0f, -feeling) + pendingPain;
        pendingPain = 0f;
        lastDist = d;
        lastFacing = facing;
        courseTicks++;
        if (flatDist() < 0.8f && Math.abs(y - goalY) < 0.5f) {
            f.pleasure = 1f;
            reached++;
            done[course.ordinal()]++;
            if (goalY >= 1f) {
                reachedHigh++;
            }
            placeGoal();
        } else if (courseTicks > COURSE_TICKS) {
            placeGoal(); // gave up; somewhere new
        }
        f.pleasure = Math.min(1f, f.pleasure);
        f.pain = Math.min(1f, f.pain);
    }

    // ---------------------------------------------------------------- body

    /** One tick of player controls, in vanilla's order: jump, accelerate, move, then drag. */
    void act(MotorCommand c) {
        yaw += c.yawDelta();
        pitch = Math.max(-90f, Math.min(90f, pitch + c.pitchDelta()));
        boolean wasSneaking = sneaking;
        sneaking = c.sneak();
        if (wasSneaking && !sneaking && !fits(x, y, z)) {
            sneaking = true; // no room to stand up
        }
        boolean wet = inWater();
        float sin = (float) Math.sin(Math.toRadians(yaw));
        float cos = (float) Math.cos(Math.toRadians(yaw));
        boolean sprint = c.sprint() && c.forward() > 0 && !sneaking;

        if (c.jump()) {
            if (wet) {
                vy += 0.04f;
            } else if (onGround) {
                vy = JUMP;
                if (sprint) {
                    vx += -sin * 0.2f;
                    vz += cos * 0.2f;
                }
            }
        }

        float fwd = c.forward() * 0.98f;
        float strafe = c.strafe() * 0.98f;
        if (sneaking) {
            fwd *= 0.3f;
            strafe *= 0.3f;
        }
        float len = (float) Math.hypot(fwd, strafe);
        if (len > 1f) {
            fwd /= len;
            strafe /= len;
        }
        float accel = wet ? WATER_ACCEL : onGround ? GROUND_ACCEL : AIR_ACCEL;
        if (sprint && !wet) {
            accel *= 1.3f;
        }
        vx += (strafe * cos - fwd * sin) * accel;
        vz += (fwd * cos + strafe * sin) * accel;

        hands(c);
        move();

        if (wet && horizontalCollision && fits(x + pushX, y + STEP, z + pushZ)) {
            vy = 0.3f; // climbing out of water against a bank
        }
        if (wet) {
            vx *= WATER_DRAG;
            vz *= WATER_DRAG;
            vy = vy * 0.8f - 0.02f;
            fallDistance = 0f;
            underWaterTicks = underWater() ? underWaterTicks + 1 : 0;
            if (underWaterTicks > 300 && underWaterTicks % 20 == 0) {
                hurt(2f); // drowning
            }
        } else {
            underWaterTicks = 0;
            float f = onGround ? GROUND_FRICTION : AIR_FRICTION;
            vx *= f;
            vz *= f;
            vy = (vy - GRAVITY) * 0.98f;
        }
    }

    /**
     * The block in the crosshair within reach: {column i, column k, i of the column in front of the
     * face that was hit, k of it, 1 if the top face was hit}. Columns of blocks stand for the world.
     */
    int[] target() {
        float ex = x, ey = y + eye(), ez = z;
        float cp = (float) Math.cos(Math.toRadians(pitch));
        float dx = (float) -Math.sin(Math.toRadians(yaw)) * cp;
        float dy = (float) -Math.sin(Math.toRadians(pitch));
        float dz = (float) Math.cos(Math.toRadians(yaw)) * cp;
        int pi = (int) Math.floor(ex), pk = (int) Math.floor(ez);
        for (float s = 0.05f; s <= REACH; s += 0.05f) {
            float px = ex + dx * s, py = ey + dy * s, pz = ez + dz * s;
            int i = (int) Math.floor(px), k = (int) Math.floor(pz);
            if (py < topAt(px, pz)) {
                boolean topFace = i == pi && k == pk || py > topAt(px, pz) - 0.05f - 0.05f * Math.abs(dy);
                return new int[] {i, k, pi, pk, topFace ? 1 : 0};
            }
            pi = i;
            pk = k;
        }
        return null;
    }

    private void hands(MotorCommand c) {
        int[] t = target();
        if (c.attack() && t != null && t[0] > 0 && t[1] > 0 && t[0] < SIZE - 1 && t[1] < SIZE - 1 && top[t[0]][t[1]] > -2f) {
            if (t[0] != digI || t[1] != digK) {
                digI = t[0];
                digK = t[1];
                digTicks = 0;
            }
            pendingPleasure += DIG_PLEASURE;
            if (++digTicks >= DIG_TICKS) {
                top[digI][digK] -= 1f; // the column loses its top block
                blocks++;
                dug++;
                pendingPleasure += COLLECT_PLEASURE; // picked up what was dug
                digTicks = 0;
                digI = -1;
            }
        } else {
            digTicks = 0;
            digI = -1;
        }
        if (useCooldown > 0) {
            useCooldown--;
        }
        if (c.use() && t != null && blocks > 0 && useCooldown == 0) {
            int i = t[4] == 1 ? t[0] : t[2], k = t[4] == 1 ? t[1] : t[3];
            boolean inside = i > 0 && k > 0 && i < SIZE - 1 && k < SIZE - 1;
            float newTop = inside ? top[i][k] + 1f : 0f;
            boolean sideOk = inside && (t[4] == 1 || top[i][k] < top[t[0]][t[1]]);
            if (sideOk && !overlapsBody(i, k, newTop) && water[i][k] == NO_WATER) {
                top[i][k] = newTop;
                blocks--;
                placed++;
                useCooldown = USE_COOLDOWN;
            }
        }
    }

    /** True when a block filling column (i, k) up to newTop would be inside the body. */
    private boolean overlapsBody(int i, int k, float newTop) {
        float h = width / 2f;
        boolean overX = x + h > i && x - h < i + 1;
        boolean overZ = z + h > k && z - h < k + 1;
        return overX && overZ && newTop > y + 1e-4f;
    }

    private float pushX, pushZ;

    private void move() {
        // Vertical first.
        float floor = floorUnder(x, z);
        float ny = y + vy;
        boolean landed = false;
        if (ny <= floor) {
            ny = floor;
            landed = vy <= 0f;
            vy = 0f;
        }
        if (!inWater() && ny < y) {
            fallDistance += y - ny;
        }
        y = ny;
        boolean wasOnGround = onGround;
        onGround = landed;
        if (onGround && fallDistance > 0f) {
            if (waterAt(x, z) == NO_WATER || y >= waterAt(x, z)) {
                float dmg = (float) Math.ceil(fallDistance - 3f);
                if (dmg > 0f) {
                    hurt(dmg);
                    falls++;
                }
            }
            fallDistance = 0f;
        }

        // Then each horizontal axis, stepping up low blocks while on the ground.
        horizontalCollision = false;
        pushX = vx;
        pushZ = vz;
        boolean canStep = onGround || wasOnGround;
        float nx = x + vx;
        if (!tryMoveTo(nx, z, canStep)) {
            vx = 0f;
            horizontalCollision = true;
        }
        float nz = z + vz;
        if (!tryMoveTo(x, nz, canStep)) {
            vz = 0f;
            horizontalCollision = true;
        }
    }

    private boolean tryMoveTo(float nx, float nz, boolean canStep) {
        float floor = floorUnder(nx, nz);
        if (sneaking && onGround && floor < y - STEP) {
            return false; // sneaking stops at the edge
        }
        if (floor <= y + 1e-4f) {
            x = nx;
            z = nz;
            return true;
        }
        if (canStep && floor <= y + STEP) {
            x = nx;
            z = nz;
            y = floor;
            return true;
        }
        return false;
    }

    private void hurt(float amount) {
        health -= amount;
        damageTaken += amount;
        pendingPain += amount / 4f;
        if (health <= 0f) {
            health = 20f; // respawn on the spot; the brain keeps learning
        }
    }

    private static float sq(float v) {
        return v * v;
    }
}
