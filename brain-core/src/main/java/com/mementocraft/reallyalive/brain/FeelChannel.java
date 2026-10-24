package com.mementocraft.reallyalive.brain;

/** Body sense ("Feely" in the notebook): touch, balance and the state of the body. Values are 0..1. */
public enum FeelChannel {
    ON_GROUND,
    IN_WATER,
    UNDER_WATER,
    IN_LAVA,
    ON_CLIMBABLE,
    TOUCH_FRONT,
    TOUCH_BACK,
    TOUCH_LEFT,
    TOUCH_RIGHT,
    TOUCH_HEAD,
    VEL_FORWARD,
    VEL_BACKWARD,
    VEL_LEFT,
    VEL_RIGHT,
    VEL_UP,
    VEL_DOWN,
    HEAD_UP,
    HEAD_DOWN,
    FALL_DISTANCE,
    HEALTH,
    HUNGER,
    SNEAKING,
    SPRINTING,
    SWIMMING,
    /** The hand holds a block that could be placed. */
    HOLDING_BLOCK,
    /** The crosshair rests on a block within arm's reach (a player sees its outline). */
    TARGET_IN_REACH,
    /** How far digging the targeted block has got (a player sees it crack). */
    DIG_PROGRESS;

    public static final int COUNT = values().length;
}
