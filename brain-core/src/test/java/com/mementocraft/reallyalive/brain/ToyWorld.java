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
    float x, z, yaw, vx, vz;
    float goalX, goalZ;
    float lastDist;
    float lastFacing;
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

    void sense(SensoryFrame f) {
        f.clear();
        float d = dist();
        float bearing = bearing();
        if (Math.abs(bearing) < FOV_H / 2f) {
            int col = (int) ((bearing + FOV_H / 2f) / FOV_H * SenseLayout.VISION_W);
            col = Math.max(0, Math.min(SenseLayout.VISION_W - 1, col));
            for (int y = 3; y < 7; y++) {
                f.vision[SenseLayout.visionIndex(col, y, SenseLayout.CH_NEAR)] = 1f / (1f + d / 4f);
                f.vision[SenseLayout.visionIndex(col, y, SenseLayout.CH_CREATURE)] = 1f;
            }
        }
        for (int c = 0; c < SenseLayout.VISION_W; c++) {
            f.vision[SenseLayout.visionIndex(c, 9, SenseLayout.CH_SOLID)] = 1f;
            f.vision[SenseLayout.visionIndex(c, 9, SenseLayout.CH_NEAR)] = 0.8f;
        }
        f.set(OlfactoryChannel.GOAL, 1f / (1f + d / 4f));
        f.set(FeelChannel.ON_GROUND, 1f);
        f.set(FeelChannel.HEALTH, 1f);
        float sin = (float) Math.sin(Math.toRadians(yaw));
        float cos = (float) Math.cos(Math.toRadians(yaw));
        float fwd = -vx * sin + vz * cos;
        float left = vx * cos + vz * sin;
        f.set(FeelChannel.VEL_FORWARD, fwd * 4f);
        f.set(FeelChannel.VEL_BACKWARD, -fwd * 4f);
        f.set(FeelChannel.VEL_LEFT, left * 4f);
        f.set(FeelChannel.VEL_RIGHT, -left * 4f);
        float approach = lastDist - d;
        float facing = (float) Math.cos(Math.toRadians(bearing));
        // Getting closer feels good, and so does turning toward what it wants (an orienting drive).
        float feeling = approach * 4f + (facing - lastFacing) * 10f;
        f.pleasure = Math.max(0f, feeling);
        // Losing sight of or moving away from what it wants is unpleasant.
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
