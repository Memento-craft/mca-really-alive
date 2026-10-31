package com.mementocraft.reallyalive.brain;

/**
 * Exactly the inputs a player has: move keys, jump, sneak, sprint, mouse look and the two mouse
 * buttons, all at once, plus a word said in chat.
 *
 * @param forward    +1 forward key, -1 back key, 0 neither
 * @param strafe     +1 left key, -1 right key, 0 neither (vanilla's sign for sideways input)
 * @param yawDelta   degrees to turn this tick, positive turns right
 * @param pitchDelta degrees to tilt this tick, positive looks down
 * @param attack     left mouse button held
 * @param use        right mouse button held
 * @param inventory  inventory key (E) held
 * @param hotbar     mouse wheel turned to the next thing it carries
 * @param say        lexicon slot of the word said this tick, or -1 for silence
 */
public record MotorCommand(int forward, int strafe, boolean jump, boolean sneak, boolean sprint,
                           float yawDelta, float pitchDelta, boolean attack, boolean use,
                           boolean inventory, boolean hotbar, int say) {
    public static final MotorCommand IDLE = new MotorCommand(0, 0, false, false, false, 0f, 0f);

    /** Keys and mouse look only, both buttons up. */
    public MotorCommand(int forward, int strafe, boolean jump, boolean sneak, boolean sprint,
                        float yawDelta, float pitchDelta) {
        this(forward, strafe, jump, sneak, sprint, yawDelta, pitchDelta, false, false, false, false, -1);
    }

    /** Keys, mouse look and buttons, saying nothing. */
    public MotorCommand(int forward, int strafe, boolean jump, boolean sneak, boolean sprint,
                        float yawDelta, float pitchDelta, boolean attack, boolean use) {
        this(forward, strafe, jump, sneak, sprint, yawDelta, pitchDelta, attack, use, false, false, -1);
    }

    /** Keys, mouse look, buttons, the inventory key and the mouse wheel, saying nothing. */
    public MotorCommand(int forward, int strafe, boolean jump, boolean sneak, boolean sprint,
                        float yawDelta, float pitchDelta, boolean attack, boolean use, boolean inventory, boolean hotbar) {
        this(forward, strafe, jump, sneak, sprint, yawDelta, pitchDelta, attack, use, inventory, hotbar, -1);
    }

    /** The same keys, saying the given word (or -1 for nothing). */
    public MotorCommand saying(int word) {
        return new MotorCommand(forward, strafe, jump, sneak, sprint, yawDelta, pitchDelta, attack, use, inventory, hotbar, word);
    }

    /**
     * The same keys as they work while a screen is open: the mouse and its buttons, sneak (shift)
     * and the inventory key still do something, the movement keys do not.
     */
    public MotorCommand inScreen() {
        return new MotorCommand(0, 0, false, sneak, false, yawDelta, pitchDelta, attack, use, inventory, false, say);
    }

    /** Short key readout for debugging, like a keystroke display. */
    public String keys() {
        StringBuilder sb = new StringBuilder();
        sb.append(forward > 0 ? 'W' : '-');
        sb.append(strafe > 0 ? 'A' : '-');
        sb.append(forward < 0 ? 'S' : '-');
        sb.append(strafe < 0 ? 'D' : '-');
        sb.append(jump ? " JUMP" : "");
        sb.append(sneak ? " SNEAK" : "");
        sb.append(sprint ? " SPRINT" : "");
        sb.append(attack ? " ATTACK" : "");
        sb.append(use ? " USE" : "");
        sb.append(inventory ? " INV" : "");
        sb.append(hotbar ? " WHEEL" : "");
        sb.append(say >= 0 ? " SAY#" + say : "");
        sb.append(String.format(" yaw%+.1f pitch%+.1f", yawDelta, pitchDelta));
        return sb.toString();
    }
}
