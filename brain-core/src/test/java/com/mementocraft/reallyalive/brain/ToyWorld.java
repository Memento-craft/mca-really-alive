package com.mementocraft.reallyalive.brain;

/**
 * A flat test world with player-like walking physics (same acceleration, friction and turn caps as
 * vanilla walking), a goal that can be seen and smelt, and pleasure for getting closer to it.
 */
final class ToyWorld {
    static final float GROUND_ACCEL = 0.098f;
    static final float FRICTION = 0.546f;
    static final float FOV_H = 100f;

    final Rng rng;
    float x, z, yaw, pitch, vx, vz;
    float goalX, goalZ;
    float lastDist;
    static final float NOT_SEEN = -2f;
    float lastFacing = NOT_SEEN;
    int reached;

    ToyWorld(long seed) {
        rng = new Rng(seed);
        placeGoal();
    }

    void placeGoal() {
        float a = rng.nextFloat() * 360f;
        float d = 4f + rng.nextFloat() * 6f;
        goalX = x + (float) -Math.sin(Math.toRadians(a)) * d;
        goalZ = z + (float) Math.cos(Math.toRadians(a)) * d;
        lastDist = dist();
    }

    float dist() {
        return (float) Math.hypot(goalX - x, goalZ - z);
    }

    /** Angle from where the body faces to the goal, -180..180, positive means the goal is to the right. */
    float bearing() {
        float toGoal = (float) Math.toDegrees(Math.atan2(-(goalX - x), goalZ - z));
        float b = toGoal - yaw;
        while (b > 180f) b -= 360f;
        while (b < -180f) b += 360f;
        return b;
    }

    static final float FOV_V = 70f;
    static final float EYE = 1.62f;
    static final float GOAL_HEIGHT = 1.8f;
    static final float RANGE = 24f;

    /** Vertical angle of a ray row's centre, degrees, positive looking down (like pitch). */
    private float rowAngle(int y) {
        return pitch + ((y + 0.5f) / SenseLayout.VISION_H - 0.5f) * FOV_V;
    }

    /** True when some part of the goal falls inside the field of view, sideways and up-down. */
    boolean goalInView() {
        if (Math.abs(bearing()) >= FOV_H / 2f) {
            return false;
        }
        float d = Math.max(0.1f, dist());
        float top = (float) Math.toDegrees(Math.atan2(EYE - GOAL_HEIGHT, d));
        float bottom = (float) Math.toDegrees(Math.atan2(EYE, d));
        return bottom > pitch - FOV_V / 2f && top < pitch + FOV_V / 2f;
    }

    void sense(SensoryFrame f) {
        f.clear();
        float d = dist();
        float bearing = bearing();
        float top = (float) Math.toDegrees(Math.atan2(EYE - GOAL_HEIGHT, Math.max(0.1f, d)));
        float bottom = (float) Math.toDegrees(Math.atan2(EYE, Math.max(0.1f, d)));
        int goalCol = -1;
        if (Math.abs(bearing) < FOV_H / 2f) {
            goalCol = (int) ((bearing + FOV_H / 2f) / FOV_H * SenseLayout.VISION_W);
            goalCol = Math.max(0, Math.min(SenseLayout.VISION_W - 1, goalCol));
        }
        for (int y = 0; y < SenseLayout.VISION_H; y++) {
            float a = rowAngle(y);
            boolean groundSeen = a > 0.5f && EYE / Math.tan(Math.toRadians(a)) < RANGE;
            float groundDist = groundSeen ? (float) (EYE / Math.tan(Math.toRadians(a))) : RANGE;
            for (int x = 0; x < SenseLayout.VISION_W; x++) {
                if (x == goalCol && a >= top && a <= bottom && d < groundDist) {
                    f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = 1f - d / RANGE;
                    f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_CREATURE)] = 1f;
                } else if (groundSeen) {
                    f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = 1f - groundDist / RANGE;
                    f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_SOLID)] = 1f;
                }
            }
        }
        f.set(OlfactoryChannel.GOAL, 1f / (1f + d / 4f));
        f.set(FeelChannel.ON_GROUND, 1f);
        f.set(FeelChannel.HEALTH, 1f);
        f.set(FeelChannel.HEAD_UP, -pitch / 90f);
        f.set(FeelChannel.HEAD_DOWN, pitch / 90f);
        float sin = (float) Math.sin(Math.toRadians(yaw));
        float cos = (float) Math.cos(Math.toRadians(yaw));
        float fwd = -vx * sin + vz * cos;
        float left = vx * cos + vz * sin;
        f.set(FeelChannel.VEL_FORWARD, fwd * 4f);
        f.set(FeelChannel.VEL_BACKWARD, -fwd * 4f);
        f.set(FeelChannel.VEL_LEFT, left * 4f);
        f.set(FeelChannel.VEL_RIGHT, -left * 4f);
        float approach = lastDist - d;
        boolean inView = goalInView();
        float facing = inView ? 1f - Math.abs(bearing) / (FOV_H / 2f) : NOT_SEEN;
        // While it can see what it wants, getting closer and turning toward it feel good and the
        // opposite feels bad. Spotting it is a small pleasure. (Making the loss of sight hurt
        // taught brains never to look at it at all.)
        float feeling = 0f;
        if (inView && lastFacing != NOT_SEEN) {
            feeling = approach * 4f + (facing - lastFacing) * 4f;
        } else if (inView) {
            feeling = 0.3f; // spotted it
        }
        f.pleasure = Math.max(0f, feeling);
        f.pain = Math.max(0f, -feeling);
        lastDist = d;
        lastFacing = facing;
        if (d > 16f) {
            placeGoal(); // lost it; something new to want
        }
        if (d < 1f) {
            f.pleasure = 1f;
            reached++;
            placeGoal();
        }
    }

    /** Applies one tick of player controls; returns how much closer the goal got. */
    float act(MotorCommand c) {
        float before = dist();
        yaw += c.yawDelta();
        pitch = Math.max(-90f, Math.min(90f, pitch + c.pitchDelta()));
        float fwd = c.forward() * 0.98f;
        float strafe = c.strafe() * 0.98f;
        if (c.sneak()) {
            fwd *= 0.3f;
            strafe *= 0.3f;
        }
        float accel = GROUND_ACCEL * (c.sprint() && c.forward() > 0 && !c.sneak() ? 1.3f : 1f);
        float len = (float) Math.hypot(fwd, strafe);
        if (len > 1f) {
            fwd /= len;
            strafe /= len;
        }
        float sin = (float) Math.sin(Math.toRadians(yaw));
        float cos = (float) Math.cos(Math.toRadians(yaw));
        vx += (strafe * cos - fwd * sin) * accel;
        vz += (fwd * cos + strafe * sin) * accel;
        x += vx;
        z += vz;
        vx *= FRICTION;
        vz *= FRICTION;
        return before - dist();
    }
}
