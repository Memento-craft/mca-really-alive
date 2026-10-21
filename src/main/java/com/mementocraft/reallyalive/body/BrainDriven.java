package com.mementocraft.reallyalive.body;

/**
 * Added to every living entity by mixin. True while a brain, not the vanilla or MCA AI, is moving
 * the entity, so the player-movement rules apply to it.
 */
public interface BrainDriven {
    boolean reallyalive$isDriven();

    void reallyalive$setDriven(boolean driven);

    static boolean isDriven(Object entity) {
        return entity instanceof BrainDriven d && d.reallyalive$isDriven();
    }
}
