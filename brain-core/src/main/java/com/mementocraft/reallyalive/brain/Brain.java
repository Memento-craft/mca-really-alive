package com.mementocraft.reallyalive.brain;

import java.util.Arrays;
import java.util.Set;

/**
 * One villager's brain: a spiking neural network of leaky integrate-and-fire neurons split into the
 * lobes of the notebook diagram. It learns while it runs:
 *
 * <ul>
 *   <li>Spike-timing plasticity strengthens synapses whose neurons fire together, scaled by a
 *       dopamine-like signal from the emotion lobe (reward strengthens, pain weakens).</li>
 *   <li>Each neuron adapts its threshold toward a healthy firing rate. A neuron that stays far too
 *       quiet or far too busy loses health; at zero its connections are deleted and it is reborn,
 *       reaching out to dull, under-connected neurons. Neurons that often fire together cement and
 *       lose health more slowly.</li>
 *   <li>The cerebellum predicts what the body will feel next; the prediction error trains it and
 *       feeds curiosity.</li>
 *   <li>Neurons are added as the villager matures, in the lobes that are most active.</li>
 * </ul>
 *
 * <p>The output is never a single chosen action: every motor pool is read every tick and decoded
 * into the full set of player controls.
 *
 * <p>Not thread-safe. One thread may step a brain at a time.
 */
public final class Brain {
    static final float THRESHOLD_START = 1.0f;
    static final float THRESHOLD_MIN = 0.3f;
    static final float THRESHOLD_MAX = 4.0f;
    static final float THRESHOLD_ADAPT = 0.05f;
    static final int REFRACTORY_TICKS = 2;
    static final float TRACE_DECAY = 0.6f;
    static final float FAST_RATE_DECAY = 0.7f;
    static final float SLOW_RATE_DECAY = 0.995f;
    static final float TARGET_RATE = 0.05f;
    static final float RATE_LOW = 0.003f;
    static final float RATE_HIGH = 0.25f;
    static final float HEALTH_DECAY = 0.0005f;
    static final float HEALTH_RECOVER = 0.002f;
    static final float CEMENT_GAIN = 0.002f;
    /** Per tick, an unhealthy neuron slowly loses the protection cementing gave it. */
    static final float CEMENT_LOSS = 0.999f;
    static final float W_INIT = 0.3f;
    static final float W_MAX = 2.0f;
    static final float W_PRUNE = 0.02f;
    /** Synaptic scaling: total excitatory weight a neuron may receive before all of it is scaled down. */
    static final float IN_WEIGHT_MAX = 4f;
    static final float A_PLUS = 1.0f;
    static final float A_MINUS = 1.05f;
    static final float ELIG_DECAY = 0.8f;
    static final float HEBB_BASE = 0.15f;
    /**
     * Scales reward learning on cerebellum-to-motor synapses. Larger steps let one unlucky moment wipe
     * an inborn reflex in a few ticks; smaller ones learn too slowly to see within a day.
     */
    static final float MOTOR_LEARNING_BOOST = 0.25f;
    /**
     * The same for synapses from the cerebellum, the learned pathway. Inborn reflex arcs come straight
     * from receptors and keep the smaller step; with that small step for everything, a learned key
     * such as placing a block faded once babbling did.
     */
    static final float PATHWAY_BOOST = 0.5f;
    /** How strongly a reward that beats expectations turns into dopamine. Too high and it clips. */
    static final float DOPAMINE_GAIN = 1.5f;
    static final float MOTOR_ELIG_DECAY = 0.9f;
    /** How fast that memory of an action fades once the input that saw it falls quiet. */
    static final float QUIET_ELIG_DECAY = 0.97f;
    static final float QUIET_ELIG_MIN = 1e-3f;
    /**
     * How fast the learned pathway's memory of an action fades while its input stays active. At 0.9
     * a reward 40 ticks later was lost unless the input happened to fall quiet (2 of 6 brains
     * learned it); at 0.97 villagers in game held jump nonstop.
     */
    static final float PATHWAY_ELIG_DECAY = 0.95f;
    static final float TRACE_ACTIVE = 0.01f;
    static final float RECEPTOR_MAX_P = 0.6f;
    static final float INHIBITORY_FRACTION = 0.2f;
    /** Inhibitory synapses count this many times over, so excitation and inhibition balance. */
    static final float INHIBITORY_GAIN = 4f;
    static final float CURIOSITY_GAIN = 4f;
    static final float BABBLE_GAIN = 0.8f;
    /** Ticks of living after which babbling has faded to its adult level: three Minecraft days. */
    static final long EXPERIENCE_TICKS = 72_000L;
    static final int MAINTENANCE_INTERVAL = 20;
    static final int MAX_RESETS_PER_TICK = 4;
    /**
     * Smell receptors per scent. With 4, a smell alone hardly reached the concept lobe and words
     * for smells (flowers, bread) and for what tells a cow from a zombie could not be learned.
     */
    static final int OLFACTORY_PER_CHANNEL = 12;
    static final int FEELY_PER_CHANNEL = 2;
    static final int EMOTION_PER_CHANNEL = 8;
    static final int CANDIDATES = 3;
    static final float REFLEX_WEIGHT = 2.0f;
    static final float ORIENT_WEIGHT = 0.8f;
    static final float PLACING_WEIGHT = 2.0f;
    static final float CLIMB_WEIGHT = 1.5f;
    /**
     * An open screen gently drives the inventory key, the urge to get back to the world: without it a
     * babbling brain that toggles the key at random sat in its inventory half its life, standing still.
     * Weak, so a screen stays open long enough to do something on it; learning can change it.
     */
    static final float LEAVE_SCREEN_WEIGHT = 0.35f;
    /**
     * How much the inventory key and the mouse wheel babble, against the other keys. A player
     * presses them now and then, not as often as they walk or look about.
     */
    static final float INVENTORY_BABBLE = 0.3f;
    static final float WHEEL_BABBLE = 0.5f;
    /**
     * Neurons sit on a sheet in their lobe, and connections between lobes favour neurons at the
     * same place on the sheet. This keeps maps intact, like the retinotopic maps of a real visual
     * system: what is seen on the left stays on the left side of each lobe.
     */
    static final float TOPO_SIGMA = 0.1f;
    static final int GRID = 8;

    static final byte ROLE_INTERIOR = 0;
    static final byte ROLE_RECEPTOR = 1;
    static final byte ROLE_MOTOR = 2;
    /** A neuron of a speech pool: one pool per word feature, driven by Wernicke's area. */
    static final byte ROLE_SPEECH = 3;

    /** Hearing receptors per sound kind and per word, in Wernicke's area. */
    static final int HEARING_PER_CHANNEL = 3;
    /** How fast the hearing-to-voice links learn: what was heard around the time a word was said. */
    static final float SPEECH_LEARNING = 10f;
    static final float SPEECH_BABBLE_GAIN = 1.5f;
    /** Chance per tick, at full babbling, of trying out a word. */
    static final float BABBLE_WORD_RATE = 0.03f;
    /** How much more readily a word just heard from someone else comes to the voice. */
    static final float PRIMED = 1.25f;
    static final float HEARING_VOICE_WEIGHT = 0.1f;
    /** A word is said when at least SAY_FEATURES of its feature pools fire this much... */
    static final float SAY_ON = 0.3f;
    static final int SAY_FEATURES = 3;
    /** ...and not within this many ticks of the last word (about two and a half words a second). */
    static final int VOICE_COOLDOWN = 8;
    /**
     * How hard the word Wernicke's language area expects next pushes its speech pools while the
     * villager has something to say.
     */
    static final float LANGUAGE_DRIVE = 1.2f;
    /** The urge to answer: full for a player, halving with each line villagers trade without one. */
    static final float URGE_MIN = 0.2f;
    static final float URGE_DECAY = 0.995f;
    static final int MAX_LINE_WORDS = 24;
    /** How strongly it wants to say something it noticed, against URGE_MIN. */
    static final float REMARK_URGE = 0.5f;
    /** Ticks after a remark before it makes another. */
    static final int REMARK_COOLDOWN = 200;
    /** Ticks after the last line heard or said before it speaks up on its own. */
    static final int REMARK_QUIET = 100;
    /** How far into a line it must get to what it noticed, or it does not say it. */
    static final int REMARK_WORDS = 5;
    /** Ways of starting a line it weighs before giving up on a remark. */
    static final int REMARK_OPENERS = 48;
    /** Ways of starting an answer it weighs. */
    static final int REPLY_OPENERS = 24;
    /** A line thought through has at least this many words (counting punctuation). */
    static final int LINE_MIN_WORDS = 3;
    /** How much a line loses for being just as natural out of nowhere as in answer. */
    static final float RELEVANCE = 0.1f;
    /** How much saying what the concept system holds counts toward picking a line. */
    static final float MEANING = 4f;
    /** How much an answer's words going with the words of the line it answers count. */
    static final float TOPIC = 1f;
    /** How much the tone of an answer's words going with the line it answers counts. */
    static final float TONE = 3f;
    /** How strongly a question must cue a line told earlier (summed telling-word weight), and how much its words are lifted. */
    static final float TOLD_CUE = Float.parseFloat(System.getProperty("reallyalive.toldCue", "5"));
    /** How many lines back a told line can be. */
    static final int TOLD_LINES = 8;
    static final int TOLD_QUESTION_WORDS = 10;
    static final float TOLD_WEIGHT = Float.parseFloat(System.getProperty("reallyalive.toldWeight", "3"));
    static final float TOLD_LIFT = (float) Math.exp(Float.parseFloat(System.getProperty("reallyalive.toldLift", "3")));
    /**
     * How strongly what the concept system holds picks among the words the language area offers:
     * each word's chance is multiplied by e^(gain x how much more than usual its meaning is present).
     */
    static final float GROUNDING_GAIN = 15f;
    static final float GROUNDING_MAX = 6f;
    /** Words weighed one word ahead, and how much the best word after each counts. */
    static final int PLAN_CANDIDATES = 8;
    static final float PLAN_WEIGHT = 1f;
    /** Meaning cells: the Concept lobe, helped by the frontal part, Memory and Emotion. */
    static final LobeId[] MEANING_LOBES = {LobeId.CONCEPT, LobeId.FRONTAL, LobeId.MEMORY, LobeId.EMOTION};
    /** How long each kind of meaning cell's activity is held for the word links, per tick kept. */
    static final float[] MEANING_KEEP = {0.95f, 0.98f, 0.95f, 0.95f};

    /** Flattened input layout: vision, body, efference copy, smell, emotion, sounds, words, colour. */
    static final int IN_VISION = 0;
    static final int IN_FEEL = IN_VISION + SenseLayout.VISION_SIZE;
    static final int IN_EFFERENCE = IN_FEEL + FeelChannel.COUNT;
    static final int IN_SMELL = IN_EFFERENCE + MotorChannel.COUNT;
    static final int IN_EMOTION = IN_SMELL + OlfactoryChannel.COUNT;
    static final int IN_SOUND = IN_EMOTION + SenseLayout.EMOTION_RECEPTORS;
    static final int IN_WORDS = IN_SOUND + SoundChannel.COUNT;
    static final int IN_COLOUR = IN_WORDS + SenseLayout.WORD_FEATURES;
    static final int IN_FOCUS = IN_COLOUR + SenseLayout.COLOUR_RECEPTORS;
    static final int IN_HELD = IN_FOCUS + SenseLayout.THING_FEATURES;
    static final int INPUT_SIZE = IN_HELD + SenseLayout.THING_FEATURES;
    /** Receptors per feature of the thing in focus and the thing in hand. */
    static final int THING_PER_FEATURE = 2;

    final Genome genome;
    final Rng rng;

    // Neurons, structure of arrays.
    int n;
    byte[] lobe;
    byte[] role;
    boolean[] inhibitory;
    float[] potential;
    float[] threshold;
    float[] trace;
    float[] fastRate;
    float[] slowRate;
    float[] health;
    float[] cement;
    byte[] refractory;
    boolean[] fired;
    boolean[] firedPrev;
    boolean[] plastic;
    float[] synInput;
    float[] posX;
    float[] posY;
    float[] extInput;
    int[] inDegree;
    /** For receptors, which slot of the input vector drives them; -1 otherwise. */
    int[] inputSlot;
    SynapseList[] out;

    final IntList[] members = new IntList[LobeId.values().length];
    final IntList[] targetable = new IntList[LobeId.values().length];
    final IntList[][] memberGrid = new IntList[LobeId.values().length][GRID * GRID];
    final IntList[][] targetGrid = new IntList[LobeId.values().length][GRID * GRID];
    final int[][] motorPools = new int[MotorChannel.COUNT][SenseLayout.MOTOR_POOL_SIZE];
    final int[][] emotionPools = new int[SenseLayout.EMOTION_RECEPTORS][EMOTION_PER_CHANNEL];
    final int[][] speechPools = new int[SenseLayout.WORD_FEATURES][SenseLayout.SPEECH_POOL_SIZE];
    final float[] speechRates = new float[SenseLayout.WORD_FEATURES];
    final float[] speechBabble = new float[SenseLayout.WORD_FEATURES];
    int voiceCooldown;
    /** Wernicke's word-sequence area: the English it grew up with, plus what it heard since. */
    LanguageArea language = EnglishBase.newArea();
    /** Picks the words a brain tries out when it babbles, apart from the rest of its randomness. */
    final Rng voiceRng;
    /** The word the language area expects next, by vocabulary number, or -1. */
    int expected = -1;
    float replyUrge;
    int villagerLines;
    int lineWords;
    /** How near someone to talk to is, this tick. */
    float company;
    long lastLineTick = Long.MIN_VALUE / 2;
    long nextRemark;
    /** Links between words and the meaning cells of the concept system, both ways. */
    ConceptLinks links = new ConceptLinks();
    /** Exchanges this villager heard or took part in. */
    ExchangeMemory.Own talk = new ExchangeMemory.Own();
    /** The last line heard before this one ended, and whether it was its own. */
    private int[] lastLine;
    /** The words of the line being heard, whoever says it. */
    private final java.util.List<Integer> lineSoFar = new java.util.ArrayList<>();
    private boolean lineSoFarOwn;
    private boolean lastLineOwn;
    private int[] meaning = new int[0];
    private float[] meaningKeep = new float[0];
    private int meaningCount = -1;
    private float[] boost = new float[1024];
    private float[] fit = new float[1024];
    private final int[] planWord = new int[PLAN_CANDIDATES];
    /** The line it thought through and is saying, by vocabulary number, or null; and how far it got. */
    private int[] plan;
    private int planAt;
    /** Its last few lines, so it does not say the same thing again and again. */
    private final int[][] said = new int[8][];
    private int saidNext;
    private final float[] planScore = new float[PLAN_CANDIDATES];

    final float[] input = new float[INPUT_SIZE];
    final float[] poolRates = new float[MotorChannel.COUNT];
    final float[] babble = new float[MotorChannel.COUNT];
    final float[] emotionRates = new float[SenseLayout.EMOTION_RECEPTORS];
    final MotorDecoder decoder = new MotorDecoder();
    final ForwardModel forwardModel;
    final Critic critic;
    // Not the frontal lobe: its slowly fading activity, read by the critic, blurred when a reward
    // came and a reward seconds after a press was no longer learned (DelayedRewardTest).
    static final LobeId[] CRITIC_LOBES = {LobeId.CONCEPT, LobeId.EMOTION, LobeId.MEMORY};
    private final int[][] criticInputs = new int[CRITIC_LOBES.length][];
    private final int[] criticCounts = new int[CRITIC_LOBES.length];
    private final int[] resetQueue = new int[MAX_RESETS_PER_TICK];

    float maturity;
    float babbleScale = 1f;
    float dopamine;
    float dopamineBaseline;
    float surprise;
    long ticks;
    long resets;
    MotorCommand lastCommand = MotorCommand.IDLE;

    Brain(Genome genome, long seed, int capacity) {
        this.genome = genome;
        this.rng = new Rng(seed);
        this.voiceRng = new Rng(seed * 31 + 7);
        allocate(Math.max(64, capacity));
        for (int l = 0; l < members.length; l++) {
            members[l] = new IntList(64);
            targetable[l] = new IntList(64);
            for (int c = 0; c < GRID * GRID; c++) {
                memberGrid[l][c] = new IntList(8);
                targetGrid[l][c] = new IntList(8);
            }
        }
        this.forwardModel = new ForwardModel(lobe.length);
        this.critic = new Critic(lobe.length);
    }

    /** A newborn brain with its receptors, motor pools and an initial, randomly wired interior. */
    public static Brain create(Genome genome, long seed, float maturity) {
        Brain b = new Brain(genome, seed, (int) genome.get(Genome.ADULT_NEURONS) + 256);
        b.maturity = clamp(maturity, 0f, 1f);
        b.buildFixedNeurons();
        int interior = Math.max(0, b.targetSize() - b.n);
        float totalWeight = 0f;
        for (LobeId l : LobeId.values()) {
            if (l.growable()) {
                totalWeight += genome.lobeWeight(l);
            }
        }
        for (LobeId l : LobeId.values()) {
            if (!l.growable()) {
                continue;
            }
            int count = Math.round(interior * genome.lobeWeight(l) / totalWeight);
            for (int i = 0; i < count; i++) {
                b.addNeuron(l, ROLE_INTERIOR);
            }
        }
        for (int i = 0; i < b.n; i++) {
            b.wireOut(i);
        }
        b.wireReflexes();
        b.wireHearingToVoice();
        return b;
    }

    /**
     * Inborn reflex arcs, present before any learning, like a baby's reflexes. Feeling the head
     * tipped down drives the look-up pool and the reverse, so a newborn's gaze settles near the
     * horizon instead of drifting to the floor or sky. A creature seen to one side drives turning
     * toward it. The placing reflex lifts the body when the front of it bumps into something, and
     * being in water drives the jump pool, which is how a player swims up. A ladder seen to one side
     * turns the gaze toward it like a creature does, and holding on to a ladder drives walking
     * into it, which is how a player climbs. An open screen drives the inventory key a little, the
     * urge to get back to the world, so screens get closed again. Learning can still
     * strengthen, weaken or override these synapses.
     */
    private void wireReflexes() {
        int headDown = IN_FEEL + FeelChannel.HEAD_DOWN.ordinal();
        int headUp = IN_FEEL + FeelChannel.HEAD_UP.ordinal();
        int touchFront = IN_FEEL + FeelChannel.TOUCH_FRONT.ordinal();
        int inWater = IN_FEEL + FeelChannel.IN_WATER.ordinal();
        int onClimbable = IN_FEEL + FeelChannel.ON_CLIMBABLE.ordinal();
        int[] jump = motorPools[MotorChannel.JUMP.ordinal()];
        for (int i = 0; i < n; i++) {
            if (inputSlot[i] == touchFront || inputSlot[i] == inWater) {
                float w = inputSlot[i] == touchFront ? PLACING_WEIGHT : REFLEX_WEIGHT;
                for (int m : jump) {
                    connect(i, m, w);
                }
            }
            wireHandReflexes(i);
            if (inputSlot[i] == onClimbable) {
                // Climbing: holding a ladder drives walking into it, which is how a player climbs.
                for (int m : motorPools[MotorChannel.FORWARD.ordinal()]) {
                    connect(i, m, CLIMB_WEIGHT);
                }
            }
            int slot = inputSlot[i] - IN_VISION;
            int channel = slot % SenseLayout.VISION_CHANNELS;
            if (slot >= 0 && slot < SenseLayout.VISION_SIZE
                    && (channel == SenseLayout.CH_CREATURE
                        || channel == SenseLayout.CH_CLIMBABLE)) {
                // Orienting: a creature or a ladder seen off to one side pulls the gaze that way,
                // harder the further out it is.
                int column = slot / SenseLayout.VISION_CHANNELS % SenseLayout.VISION_W;
                float offset = (column + 0.5f) / SenseLayout.VISION_W * 2f - 1f;
                MotorChannel turn = offset < 0 ? MotorChannel.YAW_LEFT : MotorChannel.YAW_RIGHT;
                for (int m : motorPools[turn.ordinal()]) {
                    connect(i, m, ORIENT_WEIGHT * Math.abs(offset));
                }
            }
            int[] pool = inputSlot[i] == headDown ? motorPools[MotorChannel.PITCH_UP.ordinal()]
                    : inputSlot[i] == headUp ? motorPools[MotorChannel.PITCH_DOWN.ordinal()] : null;
            if (pool != null) {
                for (int m : pool) {
                    connect(i, m, REFLEX_WEIGHT);
                }
            }
        }
    }

    /**
     * The reflex of the hands, added after the first brains were saved: an open screen drives the
     * inventory key.
     */
    private void wireHandReflexes(int i) {
        if (role[i] != ROLE_RECEPTOR) {
            return;
        }
        if (inputSlot[i] == IN_FEEL + FeelChannel.SCREEN_OPEN.ordinal()) {
            for (int m : motorPools[MotorChannel.INVENTORY.ordinal()]) {
                connect(i, m, LEAVE_SCREEN_WEIGHT);
            }
        }
    }

    /**
     * Every heard-word receptor reaches every speech pool weakly, like the bundle of fibres joining
     * the hearing and speaking areas of a real brain. Which links matter is learned by babbling and
     * hearing oneself.
     */
    private void wireHearingToVoice() {
        for (int i = 0; i < n; i++) {
            int slot = inputSlot[i] - IN_WORDS;
            if (role[i] != ROLE_RECEPTOR || slot < 0 || slot >= SenseLayout.WORD_FEATURES) {
                continue;
            }
            for (int[] pool : speechPools) {
                for (int j : pool) {
                    connect(i, j, HEARING_VOICE_WEIGHT * rng.nextFloat());
                }
            }
        }
    }

    private void buildFixedNeurons() {
        for (int i = 0; i < SenseLayout.VISION_SIZE; i++) {
            receptor(LobeId.OCCIPITAL, IN_VISION + i);
        }
        for (int c = 0; c < FeelChannel.COUNT; c++) {
            for (int k = 0; k < FEELY_PER_CHANNEL; k++) {
                receptor(LobeId.FEELY, IN_FEEL + c);
            }
        }
        for (int c = 0; c < MotorChannel.COUNT; c++) {
            for (int k = 0; k < FEELY_PER_CHANNEL; k++) {
                receptor(LobeId.FEELY, IN_EFFERENCE + c);
            }
        }
        for (int c = 0; c < OlfactoryChannel.COUNT; c++) {
            for (int k = 0; k < OLFACTORY_PER_CHANNEL; k++) {
                receptor(LobeId.OLFACTORY, IN_SMELL + c);
            }
        }
        for (int c = 0; c < SenseLayout.EMOTION_RECEPTORS; c++) {
            for (int k = 0; k < EMOTION_PER_CHANNEL; k++) {
                emotionPools[c][k] = receptor(LobeId.EMOTION, IN_EMOTION + c);
            }
        }
        for (int c = 0; c < SoundChannel.COUNT; c++) {
            for (int k = 0; k < HEARING_PER_CHANNEL; k++) {
                receptor(LobeId.WERNICKE, IN_SOUND + c);
            }
        }
        for (int w = 0; w < SenseLayout.WORD_FEATURES; w++) {
            for (int k = 0; k < HEARING_PER_CHANNEL; k++) {
                receptor(LobeId.WERNICKE, IN_WORDS + w);
            }
        }
        buildColourReceptors();
        buildThingReceptors();
        // The Motor lobe's sheet: controls in the lower half, the voice in the upper half.
        for (int c = 0; c < MotorChannel.COUNT; c++) {
            buildMotorPool(c);
        }
        for (int w = 0; w < SenseLayout.WORD_FEATURES; w++) {
            for (int k = 0; k < SenseLayout.SPEECH_POOL_SIZE; k++) {
                speechPools[w][k] = addNeuron(LobeId.MOTOR, ROLE_SPEECH, rng.nextFloat(),
                        Wiring.Band.VOICE.into(rng.nextFloat()));
            }
        }
    }

    private void buildMotorPool(int c) {
        for (int k = 0; k < SenseLayout.MOTOR_POOL_SIZE; k++) {
            motorPools[c][k] = addNeuron(LobeId.MOTOR, ROLE_MOTOR, rng.nextFloat(),
                    Wiring.Band.KEYS.into(rng.nextFloat()));
        }
    }

    private void buildColourReceptors() {
        for (int i = 0; i < SenseLayout.COLOUR_RECEPTORS; i++) {
            receptor(LobeId.OCCIPITAL, IN_COLOUR + i);
        }
    }

    /**
     * What the crosshair rests on is seen at the centre of the eye, so it sits at the middle of the
     * occipital sheet; the thing in hand is felt, in Feely.
     */
    private void buildThingReceptors() {
        for (int c = 0; c < SenseLayout.THING_FEATURES; c++) {
            for (int k = 0; k < THING_PER_FEATURE; k++) {
                receptor(LobeId.OCCIPITAL, IN_FOCUS + c);
                receptor(LobeId.FEELY, IN_HELD + c);
            }
        }
    }

    /**
     * Brains saved before a sense existed (colour vision, the newer body channels and keys, the
     * thing in focus and in hand) get its receptors, wired in as if they had always been there.
     */
    void addMissingReceptors() {
        boolean[] has = new boolean[INPUT_SIZE];
        for (int i = 0; i < n; i++) {
            if (role[i] == ROLE_RECEPTOR && inputSlot[i] >= 0 && inputSlot[i] < INPUT_SIZE) {
                has[inputSlot[i]] = true;
            }
        }
        int first = n;
        for (int c = 0; c < FeelChannel.COUNT; c++) {
            for (int k = 0; k < FEELY_PER_CHANNEL && !has[IN_FEEL + c]; k++) {
                receptor(LobeId.FEELY, IN_FEEL + c);
            }
        }
        for (int c = 0; c < MotorChannel.COUNT; c++) {
            for (int k = 0; k < FEELY_PER_CHANNEL && !has[IN_EFFERENCE + c]; k++) {
                receptor(LobeId.FEELY, IN_EFFERENCE + c);
            }
        }
        if (!has[IN_COLOUR]) {
            buildColourReceptors();
        }
        if (!has[IN_FOCUS]) {
            buildThingReceptors();
        }
        for (int i = first; i < n; i++) {
            wireOut(i);
            wireHandReflexes(i);
        }
    }

    /**
     * Moves the receptors of a brain saved with fewer body channels and keys to where those inputs
     * are now: the newer channels were added at the end of the body sense and of the efference copy,
     * so everything after them moved along.
     */
    void remapLegacyInputs(int feelCount, int motorCount) {
        int oldEfference = IN_FEEL + feelCount;
        int oldSmell = oldEfference + motorCount;
        int shiftEfference = FeelChannel.COUNT - feelCount;
        int shiftRest = shiftEfference + MotorChannel.COUNT - motorCount;
        for (int i = 0; i < n; i++) {
            int slot = inputSlot[i];
            if (role[i] != ROLE_RECEPTOR || slot < oldEfference) {
                continue;
            }
            inputSlot[i] = slot < oldSmell ? slot + shiftEfference : slot + shiftRest;
        }
    }

    /** Brains saved before a key existed get its motor pool, reached from the cerebellum. */
    void addMissingMotorPools(int have) {
        for (int c = have; c < MotorChannel.COUNT; c++) {
            buildMotorPool(c);
            for (int i : motorPools[c]) {
                wireIn(i);
            }
        }
    }

    private int receptor(LobeId l, int slot) {
        float x = rng.nextFloat();
        float y = rng.nextFloat();
        if (slot >= IN_FOCUS && slot < IN_HELD) {
            x = 0.5f + (rng.nextFloat() - 0.5f) * 0.2f;
            y = 0.5f + (rng.nextFloat() - 0.5f) * 0.2f;
        } else if (slot >= IN_COLOUR && slot < IN_FOCUS) {
            int cell = (slot - IN_COLOUR) / SenseLayout.COLOUR_CHANNELS;
            x = (cell % SenseLayout.COLOUR_CELLS_W + 0.5f) / SenseLayout.COLOUR_CELLS_W;
            y = (cell / SenseLayout.COLOUR_CELLS_W + 0.5f) / SenseLayout.COLOUR_CELLS_H;
        } else if (l == LobeId.OCCIPITAL) {
            int ray = (slot - IN_VISION) / SenseLayout.VISION_CHANNELS;
            x = (ray % SenseLayout.VISION_W + 0.5f) / SenseLayout.VISION_W;
            y = (ray / SenseLayout.VISION_W + 0.5f) / SenseLayout.VISION_H;
        }
        int i = addNeuron(l, ROLE_RECEPTOR, x, y);
        inputSlot[i] = slot;
        return i;
    }

    // ---------------------------------------------------------------- the tick

    /** Senses one tick, thinks, learns, and returns the controls to hold this tick. */
    public MotorCommand step(SensoryFrame frame) {
        ticks++;
        int[] cb = members[LobeId.CEREBELLUM.ordinal()].data;
        int cbCount = members[LobeId.CEREBELLUM.ordinal()].size;
        forwardModel.learn(frame.feel, cb, cbCount, fastRate, cerebellumNorm(cb, cbCount));
        surprise = surprise * 0.9f + forwardModel.surprise * 0.1f;

        fillInput(frame);
        listen(frame);
        deliverSpikes();
        applyEvoked();
        driveMotorBabble();
        driveLanguage();
        integrate();
        trackMeaning();
        updateDopamine();
        learn();
        homeostasis();
        if (ticks % MAINTENANCE_INTERVAL == 0) {
            maintenance();
        }
        readMotorPools();
        lastCommand = decoder.decode(poolRates);
        int word = decodeVoice();
        if (word >= 0) {
            lastCommand = lastCommand.saying(word);
            lineWords++;
        }
        forwardModel.predict(cb, cbCount, fastRate);
        return lastCommand;
    }

    private float cerebellumNorm(int[] cb, int count) {
        float s = 0f;
        for (int i = 0; i < count; i++) {
            float r = fastRate[cb[i]];
            s += r * r;
        }
        return s;
    }

    private void fillInput(SensoryFrame f) {
        System.arraycopy(f.vision, 0, input, IN_VISION, SenseLayout.VISION_SIZE);
        System.arraycopy(f.feel, 0, input, IN_FEEL, FeelChannel.COUNT);
        System.arraycopy(poolRates, 0, input, IN_EFFERENCE, MotorChannel.COUNT);
        System.arraycopy(f.smell, 0, input, IN_SMELL, OlfactoryChannel.COUNT);
        input[IN_EMOTION + SenseLayout.EMO_PLEASURE] = SensoryFrame.clamp01(f.pleasure);
        input[IN_EMOTION + SenseLayout.EMO_PAIN] = SensoryFrame.clamp01(f.pain);
        input[IN_EMOTION + SenseLayout.EMO_CURIOSITY] = SensoryFrame.clamp01(surprise * CURIOSITY_GAIN);
        System.arraycopy(f.sound, 0, input, IN_SOUND, SoundChannel.COUNT);
        System.arraycopy(f.words, 0, input, IN_WORDS, SenseLayout.WORD_FEATURES);
        System.arraycopy(f.focus, 0, input, IN_FOCUS, SenseLayout.THING_FEATURES);
        System.arraycopy(f.held, 0, input, IN_HELD, SenseLayout.THING_FEATURES);
        for (int cy = 0; cy < SenseLayout.COLOUR_CELLS_H; cy++) {
            for (int cx = 0; cx < SenseLayout.COLOUR_CELLS_W; cx++) {
                for (int c = 0; c < SenseLayout.COLOUR_CHANNELS; c++) {
                    float sum = 0f;
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dx = 0; dx < 2; dx++) {
                            sum += f.colour[SenseLayout.colourIndex(cx * 2 + dx, cy * 2 + dy, c)];
                        }
                    }
                    input[IN_COLOUR + (cy * SenseLayout.COLOUR_CELLS_W + cx) * SenseLayout.COLOUR_CHANNELS + c] =
                            SensoryFrame.clamp01(sum / 4f);
                }
            }
        }
    }

    private void deliverSpikes() {
        boolean[] t = firedPrev;
        firedPrev = fired;
        fired = t;
        for (int i = 0; i < n; i++) {
            if (!firedPrev[i]) {
                continue;
            }
            SynapseList s = out[i];
            float sign = inhibitory[i] ? -INHIBITORY_GAIN : 1f;
            for (int k = 0; k < s.size; k++) {
                synInput[s.target[k]] += sign * s.weight[k];
            }
        }
    }

    /**
     * Words heard this tick go through Wernicke's language area in order. Others' words also teach
     * it; its own words only move it along, so it knows where it is in its own sentence. When someone
     * else finishes a line, the villager wants to answer: always a player, and villagers ever less
     * the longer they talk among themselves, so two villagers don't chatter forever.
     */
    private void listen(SensoryFrame f) {
        company = f.company;
        for (String w : f.named) {
            links.learn(language.vocabulary.id(w), meaning, Math.max(0, meaningCount), ConceptLinks.NAME_RATE);
        }
        if (f.heardWords.isEmpty()) {
            return;
        }
        for (int i = 0; i < f.heardWords.size(); i++) {
            String w = f.heardWords.get(i);
            boolean own = f.heardOwn.get(i);
            if (!w.equals(WordCode.TURN) && !lineSoFar.isEmpty() && lineSoFarOwn != own) {
                // Someone else spoke up after words it cooed and never ended (or the other way
                // round): those were no line, and are not part of this one.
                language.dropUnended();
            }
            language.hear(w, !own);
            if (!own) {
                plan = null;
            } else if (plan != null && !w.equals(WordCode.TURN)) {
                // Following the line it planned; a slip of the tongue drops the plan.
                if (planAt < plan.length && plan[planAt] == language.vocabulary.find(w)) {
                    planAt++;
                } else {
                    plan = null;
                }
            }
            if (!w.equals(WordCode.TURN)) {
                if (!lineSoFar.isEmpty() && lineSoFarOwn != own) {
                    lineSoFar.clear();
                }
                lineSoFarOwn = own;
                if (lineSoFar.size() < MAX_LINE_WORDS * 2) {
                    lineSoFar.add(language.vocabulary.find(w));
                }
                // Others' words are linked to what is around while they are said, and bring up
                // what they mean. Its own words do neither, or it would teach itself its mistakes.
                if (!own) {
                    int id = language.vocabulary.find(w);
                    primed[primedNext++ % primed.length] = id;
                    links.learn(id, meaning, Math.max(0, meaningCount), ConceptLinks.TALK_RATE);
                    links.evoke(id);
                }
                continue;
            }
            rememberExchange(own);
            lastLineTick = ticks;
            plan = null;
            if (own) {
                replyUrge = 0f;
            } else {
                // Someone finished a line: the cooing stops, the answer comes next.
                Arrays.fill(speechBabble, 0f);
                boolean player = f.sound[SoundChannel.PLAYER_VOICE.ordinal()] > 0f;
                villagerLines = player ? 0 : villagerLines + 1;
                replyUrge = player ? 1f : (float) Math.pow(0.5, villagerLines);
            }
            lineWords = 0;
        }
        if (replyUrge >= URGE_MIN && lineWords == 0 && plan == null && language.atTurnStart()) {
            // Someone finished a line: think the answer through before starting it.
            plan = thinkThrough(REPLY_OPENERS, -1, 0);
            planAt = 0;
        }
        expected = plan != null ? planned() : groundedWord(false);
    }

    /**
     * A line just ended: if it answered another line said not long before, the pair is kept as an
     * exchange, whoever said them, unless both were its own.
     */
    private void rememberExchange(boolean own) {
        // The whole line, not just the last words the language area keeps in mind.
        int[] line = lineSoFar.stream().mapToInt(Integer::intValue).toArray();
        lineSoFar.clear();
        ExchangeMemory memory = ExchangeMemory.shared();
        if (memory != null && lastLine != null && !(own && lastLineOwn) && ticks - lastLineTick < EXCHANGE_GAP
                && !(own && aboutTheMoment(line))) {
            talk.remember(lastLine, line, memory);
        }
        lastLine = line;
        lastLineOwn = own;
        if (!own) {
            heardLines[heardLinesNext++ % heardLines.length] = line;
        }
    }

    /**
     * Whether its own line named something it perceives right now ("i see a zombie"): that was
     * about the moment, not about itself, and next time the answer should come from what is there.
     */
    private boolean aboutTheMoment(int[] line) {
        groundedWord(false);
        for (int w : line) {
            if (w < fit.length && Math.log(fit[w]) > MOMENT_FIT) {
                return true;
            }
        }
        return false;
    }

    /** The next word of the planned line, or the end of the turn once it is all said. */
    private int planned() {
        return planAt < plan.length ? plan[planAt] : language.vocabulary.find(WordCode.TURN);
    }

    /**
     * The word to say next: of the words the language area expects, the one whose meaning the
     * concept system holds most, beyond how much it usually does. A word with no links (most small
     * words) is weighed by the language area alone. The frontal part plans one word ahead: a word is
     * also weighed by how well the best word that could follow it says what is held in mind, so
     * "a" wins over "you" after "i see" when what it sees is a zombie. Nothing is picked here for
     * good; this only sets which word's speech pools get pushed.
     */
    int groundedWord(boolean learn) {
        float[] s = language.scores();
        int words = language.vocabulary.size();
        if (boost.length < words) {
            boost = new float[words * 2];
            fit = new float[words * 2];
        }
        Arrays.fill(boost, 0, words, 0f);
        links.presence(boost, words, learn);
        boolean anyMeaning = false;
        // Measured from the most present meaning, so a word that fits only a little less than the
        // best does not get the same lift just because both are past the cap.
        float most = 0f;
        for (int w = 0; w < words; w++) {
            most = Math.max(most, boost[w]);
        }
        float shift = Math.max(0f, GROUNDING_GAIN * most - GROUNDING_MAX);
        for (int w = 0; w < words; w++) {
            fit[w] = (float) Math.exp(clamp(GROUNDING_GAIN * boost[w] - shift, -GROUNDING_MAX, GROUNDING_MAX));
            anyMeaning |= boost[w] != 0f;
        }
        // What it was told earlier in the talk and is being asked about now comes to mind too.
        for (int w : recalledFocus) {
            if (w < words) {
                fit[w] *= TOLD_LIFT;
                anyMeaning = true;
            }
        }
        // The few words worth planning past, by how well they fit on their own.
        int found = 0;
        for (int w = 0; w < words; w++) {
            float v = s[w] * fit[w];
            if (v <= 0f || found == PLAN_CANDIDATES && v <= planScore[found - 1]) {
                continue;
            }
            int p = found < PLAN_CANDIDATES ? found++ : found - 1;
            while (p > 0 && planScore[p - 1] < v) {
                planWord[p] = planWord[p - 1];
                planScore[p] = planScore[p - 1];
                p--;
            }
            planWord[p] = w;
            planScore[p] = v;
        }
        int turn = language.vocabulary.find(WordCode.TURN);
        int best = -1;
        float bestScore = 0f;
        for (int c = 0; c < found; c++) {
            int w = planWord[c];
            float score = planScore[c];
            if (anyMeaning && w != turn) {
                float[] next = language.scoresAfter(w);
                float plain = 0f, meant = 0f;
                for (int v = 0; v < words; v++) {
                    if (next[v] > 0f) {
                        plain = Math.max(plain, next[v]);
                        meant = Math.max(meant, next[v] * fit[v]);
                    }
                }
                if (plain > 0f) {
                    score *= (float) Math.pow(meant / plain, PLAN_WEIGHT);
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = w;
            }
        }
        return best;
    }

    /** Collects the meaning cells of the concept system again when lobes have grown. */
    private void gatherMeaning() {
        int total = 0;
        for (LobeId l : MEANING_LOBES) {
            total += targetable[l.ordinal()].size;
        }
        if (total == meaningCount) {
            return;
        }
        meaning = new int[total];
        meaningKeep = new float[total];
        int k = 0;
        for (int m = 0; m < MEANING_LOBES.length; m++) {
            IntList cells = targetable[MEANING_LOBES[m].ordinal()];
            for (int c = 0; c < cells.size; c++) {
                meaning[k] = cells.get(c);
                meaningKeep[k++] = MEANING_KEEP[m];
            }
        }
        meaningCount = total;
        links.ensureNeurons(lobe.length);
    }

    private void trackMeaning() {
        gatherMeaning();
        links.track(meaning, meaningCount, fired, meaningKeep);
    }

    /** What heard words bring up arrives at their meaning cells like any other input. */
    private void applyEvoked() {
        float[] e = links.evoked;
        for (int k = 0; k < Math.max(0, meaningCount); k++) {
            int i = meaning[k];
            synInput[i] += e[i];
        }
    }

    /**
     * Speaking up: when something new comes up strongly in the concept system and someone is
     * around to hear, the frontal part thinks a line through, word by word, as it would say it.
     * Only if the line it comes up with says what it noticed ("look, a zombie!") does it say it;
     * otherwise it keeps quiet, so it does not come out with a line about nothing.
     */
    private void maybeRemark() {
        int noticed = links.noticed;
        if (noticed < 0 || company <= 0f || replyUrge >= URGE_MIN || ticks < nextRemark
                || ticks - lastLineTick < REMARK_QUIET) {
            return;
        }
        LanguageArea.Place before = language.place();
        language.startLine();
        int[] line = thinkThrough(REMARK_OPENERS, noticed, REMARK_WORDS);
        if (line == null) {
            language.restore(before);
            return;
        }
        nextRemark = ticks + REMARK_COOLDOWN;
        replyUrge = REMARK_URGE;
        lineWords = 0;
        lineSoFar.clear();
        plan = line;
        planAt = 0;
        expected = planned();
    }

    /**
     * The frontal part thinks a line through before saying it: from where the language area is
     * now (the start of a line), it tries a few ways of opening, follows each to its end word by
     * word as it would say it, and keeps the one that comes most naturally (the likeliest words
     * per word, ending where a line ends). With mustSay set, only lines that get to that word
     * within the first sayWithin words count. Returns the line, or null if none would do; the
     * language area is left where it started.
     */
    private int[] thinkThrough(int tries, int mustSay, int sayWithin) {
        LanguageArea.Place start = language.place();
        topicHeard = mustSay < 0 && !language.heardOwnLine() ? language.heardLine() : null;
        recalledFocus = topicHeard == null ? new int[0] : toldBefore(topicHeard);
        groundedWord(false);
        int[] openers = openers(tries);
        int turn = language.vocabulary.find(WordCode.TURN);
        thoughtBest = null;
        thoughtEase = Float.NEGATIVE_INFINITY;
        int[] line = new int[MAX_LINE_WORDS];
        for (int opener : openers) {
            if (opener == turn) {
                continue;
            }
            language.restore(start);
            int w = opener;
            int length = 0;
            float ease = 0f;
            float meant = 0f;
            boolean says = mustSay < 0;
            while (length < MAX_LINE_WORDS && w >= 0 && w != turn) {
                ease += (float) Math.log(meantLikelihood(w));
                meant = Math.max(meant, (float) Math.log(fit[w]));
                if (!says && length < sayWithin) {
                    says = sameWord(language.vocabulary.word(w), language.vocabulary.word(mustSay));
                }
                line[length++] = w;
                String word = language.vocabulary.word(w);
                language.hear(word, false);
                if (says && SENTENCE_ENDS.contains(word) && length >= LINE_MIN_WORDS) {
                    // It could stop at the end of this sentence, too, rather than run on.
                    LanguageArea.Place here = language.place();
                    weigh(line, length, ease, meant, turn, 0f);
                    language.restore(here);
                }
                w = groundedWord(false);
            }
            if (says && w == turn) {
                weigh(line, length, ease, meant, turn, 0f);
            }
        }
        // Answers others gave to lines like this one come to mind too, and are weighed the same way:
        // those of the shared memory, and more strongly what this villager heard or said itself.
        language.restore(start);
        ExchangeMemory memory = ExchangeMemory.shared();
        if (mustSay < 0 && memory != null && !language.heardOwnLine()) {
            float[] match = new float[RECALLED];
            int[] heard = language.heardLine();
            int[] before = language.earlierLine();
            weighRecalled(memory.recall(heard, before, RECALL_BEFORE, RECALLED, match), match, RECALL_WEIGHT, false, start, turn);
            talk.ask = language.vocabulary.find("?");
            int[][] mine = talk.recall(heard, before, 0f, RECALLED, match, memory);
            if (toldLine != null) {
                // Asked about themselves, what someone said in the first person ("my cat ran
                // away.") was said by them, and is said back to them the other way round.
                for (int r = 0; r < mine.length; r++) {
                    mine[r] = aboutSpeaker(mine[r]) ? toldBack(mine[r]) : mine[r];
                }
            }
            weighRecalled(mine, match, OWN_RECALL_WEIGHT, true, start, turn);
            if (toldLine != null) {
                // What it was told, said back the other way round: "you have a dog named rex."
                weighRecalled(new int[][] {toldBack(toldLine)}, new float[] {1f}, TOLD_WEIGHT, true, start, turn);
            }
        }
        language.restore(start);
        recalledFocus = new int[0];
        int[] best = thoughtBest;
        if (best != null) {
            said[saidNext++ % said.length] = best;
        }
        return best;
    }

    /** How many remembered answers come to mind for a heard line, and how much they count. */
    static final int RECALLED = 8;
    static final float RECALL_BEFORE = 0.3f;
    static final float RECALL_WEIGHT = 1.5f;
    /** Its own remembered exchanges count for more, and one this close is the same question again. */
    static final float OWN_RECALL_WEIGHT = 3f;
    static final float SAME_QUESTION = 0.8f;
    static final float OWN_MATCH_MIN = 0.5f;
    /** The least likely a word of a line it knows by heart counts as (log). */
    static final float KNOWN_WORD = (float) Math.log(0.02);
    /**
     * How much it counts that a remembered answer comes easily. Someone really said it, so it is
     * good English however unlikely its words; ease counted in full makes short stock lines ("see
     * you later.") win over the answer that fits ("yeah, he has cancer."). Its own memories are
     * weighed the same way, so they keep their edge over the shared ones.
     */
    static final float RECALL_EASE = 0.5f;
    /** Lines further apart than this (ticks) are not an exchange. */
    static final int EXCHANGE_GAP = 1200;
    /** How strongly a word must fit what is perceived (log of its grounding lift) to be about the moment. */
    static final float MOMENT_FIT = 1f;
        private static final Set<String> SENTENCE_ENDS = Set.of(".", "!", "?");
    private int[] thoughtBest;
    private float thoughtEase;
    /** The line being answered, whose topic an answer should keep to; null when not answering. */
    private int[] topicHeard;
    /** Lines others said lately, the latest last; and the words of one being asked about now. */
    private final int[][] heardLines = new int[16][];
    private int heardLinesNext;
    private int[] recalledFocus = new int[0];
    private int[] toldLine;

    /**
     * Weighs a line thought through to here, with the language area just after its last word, and
     * keeps it if it beats the best so far: how naturally it ends here, and how much more natural
     * it is as an answer to this than as something to say out of nowhere, so a line that fits
     * anything ("i know, but...") loses to one that answers what was said; and a line that says
     * what is held in mind beats one that says nothing about it.
     */
    /**
     * Weighs remembered answers as lines it could say. Its own memory of what was answered to
     * just this may be said again even if said lately: asked the same again, it answers the same.
     */
    private void weighRecalled(int[][] recalled, float[] match, float strength, boolean own,
            LanguageArea.Place start, int turn) {
        int[] line = new int[MAX_LINE_WORDS];
        for (int r = 0; r < recalled.length; r++) {
            language.restore(start);
            if (Arrays.equals(recalled[r], language.heardLine()) || own && match[r] < OWN_MATCH_MIN) {
                // Saying someone's line back to them is no answer; and its own few memories are
                // only answers to lines much like the one they followed, or it would bring back
                // whatever was said a moment ago.
                continue;
            }
            groundedWord(false);
            int length = 0;
            float ease = 0f;
            float meant = 0f;
            for (int w : recalled[r]) {
                if (w >= language.vocabulary.size() || length == MAX_LINE_WORDS) {
                    break;
                }
                // A line of its own it knows by heart: a word the language area would not have
                // guessed (its own name) does not make it hard to say.
                float sure = (float) Math.log(meantLikelihood(w));
                ease += own ? Math.max(sure, KNOWN_WORD) : sure;
                meant = Math.max(meant, (float) Math.log(fit[w]));
                line[length++] = w;
                language.hear(language.vocabulary.word(w), false);
                groundedWord(false);
            }
            weigh(line, length, RECALL_EASE * ease, meant, turn, strength * match[r], own && match[r] >= SAME_QUESTION);
        }
    }

    private void weigh(int[] line, int length, float ease, float meant, int turn, float recalled) {
        weigh(line, length, ease, meant, turn, recalled, false);
    }

    private void weigh(int[] line, int length, float ease, float meant, int turn, float recalled, boolean mayRepeat) {
        if (length < LINE_MIN_WORDS || !mayRepeat && saidLately(line, length)) {
            return;
        }
        ease += (float) Math.log(likelihood(turn));
        TopicLinks topics = TopicLinks.shared();
        float about = topics == null || topicHeard == null ? 0f : TOPIC * topics.fit(topicHeard, line, length);
        ToneLinks tones = ToneLinks.shared();
        about += tones == null || topicHeard == null ? 0f : TONE * tones.fit(lastSentenceTwice(topicHeard), line, length);
        ease = (ease - RELEVANCE * outOfNowhere(line, length) + MEANING * meant + about) / (length + 1) + recalled;
        if (ease > thoughtEase) {
            thoughtEase = ease;
            thoughtBest = Arrays.copyOf(line, length);
        }
    }

    /**
     * A heard line with its last sentence counted twice: "nice to meet you. what do you do for
     * work?" is answered more in the tone of the question than of the greeting before it.
     */
    private int[] lastSentenceTwice(int[] heard) {
        int from = 0;
        for (int k = 0; k < heard.length - 1; k++) {
            if (heard[k] >= 0 && SENTENCE_ENDS.contains(language.vocabulary.word(heard[k]))) {
                from = k + 1;
            }
        }
        if (from == 0) {
            return heard;
        }
        int[] weighted = Arrays.copyOf(heard, heard.length * 2 - from);
        System.arraycopy(heard, from, weighted, heard.length, heard.length - from);
        return weighted;
    }

    /**
     * Asked something ("what's my dog's name?"), the line it was told earlier in the talk that
     * shares the most telling words with the question ("i have a dog named rex.") comes back, and
     * its telling words that the question lacks ("rex") are what the answer should say. Returns
     * those words, or none if the heard line is no question or nothing told fits it well enough.
     */
    private int[] toldBefore(int[] heard) {
        ExchangeMemory memory = ExchangeMemory.shared();
        int ask = language.vocabulary.find("?");
        toldLine = null;
        // A short, plain question about the speaker ("where do i live?"), not a long outburst
        // that ends in one.
        if (memory == null || heard.length == 0 || heard.length > TOLD_QUESTION_WORDS || heard[heard.length - 1] != ask
                || !aboutSpeaker(heard)) {
            return new int[0];
        }
        ToneLinks tones = ToneLinks.shared();
        int name = language.vocabulary.find("name");
        boolean askingName = contains(heard, name);
        int[] best = null;
        int bestK = 0;
        float bestCue = TOLD_CUE;
        // The latest line is the question itself; only what the speaker told about themselves
        // lately, not other questions, is recalled.
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] told = heardLines[(heardLinesNext - k) % heardLines.length];
            if (told.length == 0 || told[told.length - 1] == ask || !aboutSpeaker(told)) {
                continue;
            }
            float cue = 0f;
            boolean named = false;
            for (int w : told) {
                if (w >= 0 && saidIn(heard, w)) {
                    cue += memory.weight(w);
                }
                named |= w >= 0 && memory.weight(w) == 0f && tones != null && !tones.common(w)
                        && Character.isLetter(language.vocabulary.word(w).charAt(0));
            }
            if (askingName && named) {
                // "what's my name?" asks for a name, and "hi, i'm josh." gave one.
                cue += memory.weight(name);
            }
            if (cue > bestCue) {
                bestCue = cue;
                best = told;
                bestK = k;
            }
        }
        if (best == null) {
            return new int[0];
        }
        toldLine = best;
        if (contains(heard, language.vocabulary.find("why")) && bestK > 2) {
            // "why am i sad?": the why was told just after ("i'm sad today." "my cat ran away.").
            int[] next = heardLines[(heardLinesNext - bestK + 1) % heardLines.length];
            if (next.length > 0 && next[next.length - 1] != ask && aboutSpeaker(next)) {
                best = next;
                toldLine = new int[next.length + 1];
                toldLine[0] = language.vocabulary.find("because");
                System.arraycopy(next, 0, toldLine, 1, next.length);
            }
        }
        // Telling words: those that cue remembered exchanges, and names, which never do but are
        // not among the commonest words either.
        return Arrays.stream(best).filter(w -> w >= 0 && !saidIn(heard, w) && Character.isLetter(language.vocabulary.word(w).charAt(0))
                && (memory.weight(w) > 0f || tones != null && !tones.common(w))).distinct().toArray();
    }

    /** Whether a line is the speaker talking about themselves ("i", "my", "me"). */
    private boolean aboutSpeaker(int[] line) {
        for (int w : line) {
            if (w >= 0) {
                String word = language.vocabulary.word(w);
                if (word.equals("i") || word.equals("my") || word.equals("me") || word.equals("i'm") || word.equals("myself")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A line told by someone else, as said back to them: "i" becomes "you", "my" "your". */
    private int[] toldBack(int[] told) {
        String[][] swaps = {{"i", "you"}, {"me", "you"}, {"my", "your"}, {"i'm", "you're"},
            {"i've", "you've"}, {"i'll", "you'll"}, {"i'd", "you'd"}, {"myself", "yourself"}, {"am", "are"}};
        int[] back = told.clone();
        for (int k = 0; k < back.length; k++) {
            if (back[k] < 0) {
                continue;
            }
            String w = language.vocabulary.word(back[k]);
            for (String[] swap : swaps) {
                if (w.equals(swap[0])) {
                    back[k] = language.vocabulary.find(swap[1]);
                }
            }
        }
        return back;
    }

    /** Whether a word, or the same word with an "s" or "'s" or "ed" on it, is in the line. */
    private boolean saidIn(int[] line, int word) {
        String a = language.vocabulary.word(word);
        for (int w : line) {
            if (w >= 0) {
                String b = language.vocabulary.word(w);
                if (sameWord(a, b) || a.equals(b + "'s") || b.equals(a + "'s") || a.equals(b + "d") || b.equals(a + "d")
                        || a.equals(b + "ed") || b.equals(a + "ed")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(int[] line, int word) {
        for (int w : line) {
            if (w == word) {
                return true;
            }
        }
        return false;
    }

    /** How likely the line is as the opening of a talk, with nothing said before it. */
    private float outOfNowhere(int[] line, int length) {
        language.startLine();
        float ease = 0f;
        for (int k = 0; k < length; k++) {
            ease += (float) Math.log(likelihood(line[k]));
            language.hear(language.vocabulary.word(line[k]), false);
        }
        return ease + (float) Math.log(likelihood(language.vocabulary.find(WordCode.TURN)));
    }

    /** Whether it said this very line a little while ago: people don't repeat themselves. */
    private boolean saidLately(int[] line, int length) {
        for (int[] old : said) {
            if (old != null && old.length == length && Arrays.equals(old, 0, length, line, 0, length)) {
                return true;
            }
        }
        return false;
    }

    /**
     * How likely this word is next once meaning is weighed in, as groundedWord last weighed it:
     * a line that names what is held in mind comes as naturally as a stock line.
     */
    private float meantLikelihood(int word) {
        float[] sc = language.scores();
        float total = 0f;
        for (int w = 0; w < language.vocabulary.size(); w++) {
            total += sc[w] * fit[w];
        }
        return total > 0f ? Math.max(1e-6f, sc[word] * fit[word] / total) : 1e-6f;
    }

    /** How likely the language area finds this word next, 0..1. */
    private float likelihood(int word) {
        float[] sc = language.scores();
        float total = 0f;
        for (int w = 0; w < language.vocabulary.size(); w++) {
            total += sc[w];
        }
        return total > 0f ? Math.max(1e-6f, sc[word] / total) : 1e-6f;
    }

    /** The likeliest first words of a line, weighed by meaning, best first. */
    private int[] openers(int tries) {
        float[] sc = language.scores();
        int words = language.vocabulary.size();
        int[] top = new int[tries];
        float[] topScore = new float[tries];
        int found = 0;
        for (int w = 0; w < words; w++) {
            float v = sc[w] * fit[w];
            if (v <= 0f || found == tries && v <= topScore[found - 1]) {
                continue;
            }
            int p = found < tries ? found++ : found - 1;
            while (p > 0 && topScore[p - 1] < v) {
                top[p] = top[p - 1];
                topScore[p] = topScore[p - 1];
                p--;
            }
            top[p] = w;
            topScore[p] = v;
        }
        return Arrays.copyOf(top, found);
    }

    static boolean sameWord(String a, String b) {
        return a.equals(b) || a.equals(b + "s") || b.equals(a + "s");
    }

    /**
     * While the villager has something to say, the word its language area expects next drives that
     * word's speech pools, so it comes out through the same voice the brain babbles and imitates
     * with. The end of a line is not a sound: it is said by simply stopping.
     */
    private void driveLanguage() {
        if (replyUrge < URGE_MIN) {
            return;
        }
        replyUrge *= URGE_DECAY;
        if (expected < 0) {
            return;
        }
        String word = language.vocabulary.word(expected);
        if (word.equals(WordCode.TURN) || lineWords >= MAX_LINE_WORDS) {
            return;
        }
        for (int feature : WordCode.features(word)) {
            for (int i : speechPools[feature]) {
                extInput[i] += LANGUAGE_DRIVE;
            }
        }
    }

    /** The word with this vocabulary number, as the game shows it, or {@link WordCode#TURN}. */
    public String word(int id) {
        return id >= 0 && id < language.vocabulary.size() ? language.vocabulary.word(id) : null;
    }

    /**
     * New brains press random keys (motor babbling) so the cerebellum can learn what each one does.
     * The noise fades with the brain's own experience, not the villager's age: an adult that has
     * only just been given a brain still has everything to learn, and in game tests adults whose
     * babbling was cut by age learned nothing at all.
     */
    private void driveMotorBabble() {
        float experience = Math.min(1f, ticks / (float) EXPERIENCE_TICKS);
        float amp = genome.get(Genome.BABBLE) * (1f - 0.7f * experience) * babbleScale;
        for (int c = 0; c < MotorChannel.COUNT; c++) {
            babble[c] += -0.1f * babble[c] + 0.3f * rng.gaussian();
            float gain = c == MotorChannel.INVENTORY.ordinal() ? INVENTORY_BABBLE
                    : c == MotorChannel.HOTBAR.ordinal() ? WHEEL_BABBLE : 1f;
            float drive = amp * Math.max(0f, babble[c]) * BABBLE_GAIN * gain;
            for (int k = 0; k < SenseLayout.MOTOR_POOL_SIZE; k++) {
                extInput[motorPools[c][k]] = drive;
            }
        }
        driveSpeechBabble(amp);
    }

    /**
     * Cooing: now and then a brain tries out the sound of a word it knows, more often the younger
     * it is. Hearing itself say it is how it learns which speech pools make which heard sounds. It
     * does not babble while answering someone, or stray words would land in the middle of its line.
     */
    private void driveSpeechBabble(float amp) {
        if (voiceRng.nextFloat() < amp * BABBLE_WORD_RATE && replyUrge < URGE_MIN) {
            int[] features = language.vocabulary.features(voiceRng.nextInt(language.vocabulary.size()));
            for (int feature : features) {
                speechBabble[feature] = 1f;
            }
        }
        for (int w = 0; w < SenseLayout.WORD_FEATURES; w++) {
            speechBabble[w] *= 0.8f;
            float drive = amp * speechBabble[w] * SPEECH_BABBLE_GAIN;
            for (int i : speechPools[w]) {
                extInput[i] = drive;
            }
        }
    }

    private void integrate() {
        float noise = genome.get(Genome.NOISE);
        for (int i = 0; i < n; i++) {
            boolean spike;
            if (role[i] == ROLE_RECEPTOR) {
                spike = rng.nextFloat() < input[inputSlot[i]] * RECEPTOR_MAX_P;
            } else if (refractory[i] > 0) {
                refractory[i]--;
                spike = false;
            } else {
                float v = potential[i] * LobeId.of(lobe[i]).leak + synInput[i] + extInput[i]
                        + noise * rng.gaussian();
                spike = v >= threshold[i];
                if (spike) {
                    v = 0f;
                    refractory[i] = REFRACTORY_TICKS;
                } else if (v < -threshold[i]) {
                    v = -threshold[i];
                }
                potential[i] = v;
            }
            synInput[i] = 0f;
            fired[i] = spike;
            float s = spike ? 1f : 0f;
            trace[i] = trace[i] * TRACE_DECAY + s;
            fastRate[i] = fastRate[i] * FAST_RATE_DECAY + s * (1f - FAST_RATE_DECAY);
            slowRate[i] = slowRate[i] * SLOW_RATE_DECAY + s * (1f - SLOW_RATE_DECAY);
        }
    }

    /** The emotion lobe's innate pools set the dopamine level that gates learning. */
    private void updateDopamine() {
        for (int c = 0; c < emotionPools.length; c++) {
            int count = 0;
            for (int i : emotionPools[c]) {
                if (fired[i]) {
                    count++;
                }
            }
            emotionRates[c] = emotionRates[c] * 0.5f + 0.5f * count / emotionPools[c].length;
        }
        float drive = emotionRates[SenseLayout.EMO_PLEASURE] - emotionRates[SenseLayout.EMO_PAIN]
                + 0.3f * emotionRates[SenseLayout.EMO_CURIOSITY];
        for (int l = 0; l < CRITIC_LOBES.length; l++) {
            IntList m = targetable[CRITIC_LOBES[l].ordinal()];
            criticInputs[l] = m.data;
            criticCounts[l] = m.size;
        }
        float error = critic.step(drive, criticInputs, criticCounts, fastRate);
        dopamine = clamp(DOPAMINE_GAIN * error, -1.5f, 1.5f);
        dopamineBaseline = dopamineBaseline * 0.995f + drive * 0.005f;
    }

    private void learn() {
        float lr = genome.get(Genome.LEARNING_RATE);
        // Inside the lobes, learning is unsupervised: neurons that fire together wire together.
        // Letting reward reach these synapses too made the senses' maps drift with every mood swing.
        float gate = HEBB_BASE;
        // Cerebellum-to-motor synapses learn only from reward and pain, never from mere co-firing.
        float motorGate = dopamine;
        for (int i = 0; i < n; i++) {
            SynapseList s = out[i];
            if (trace[i] < TRACE_ACTIVE) {
                if (plastic[i]) {
                    if (role[i] == ROLE_RECEPTOR) {
                        Arrays.fill(s.elig, 0, s.size, 0f);
                        plastic[i] = false;
                    } else {
                        plastic[i] = fadeMotorTraces(i, s, lr, motorGate);
                    }
                }
                continue;
            }
            plastic[i] = true;
            boolean pre = fired[i];
            float preTrace = trace[i];
            // An inhibitory synapse lowers firing, so reward pushes its weight the other way.
            float preSign = inhibitory[i] ? -1f : 1f;
            for (int k = 0; k < s.size; k++) {
                int j = s.target[k];
                float e = s.elig[k] * ELIG_DECAY;
                float w;
                if (role[j] == ROLE_MOTOR) {
                    // Three-factor rule: did this motor neuron fire more or less than it usually
                    // does while the input was active? Reward then keeps or undoes that surprise.
                    // Compared with the neuron's long-run rate, so a whole burst counts, not just its onset.
                    float spike = fired[j] ? 1f : 0f;
                    // The learned pathway remembers what the body did for seconds, active or not, so
                    // a reward that comes later still reaches it; inborn reflex arcs forget fast.
                    float keep = role[i] == ROLE_RECEPTOR ? MOTOR_ELIG_DECAY : PATHWAY_ELIG_DECAY;
                    e = s.elig[k] * keep + preTrace * (spike - slowRate[j]);
                    s.elig[k] = e;
                    w = s.weight[k] + preSign * lr * (role[i] == ROLE_RECEPTOR ? MOTOR_LEARNING_BOOST : PATHWAY_BOOST) * e * motorGate;
                } else if (role[j] == ROLE_SPEECH) {
                    // The voice learns which heard sounds go with which word, whichever came first:
                    // saying a word and then hearing it binds the two, so hearing it later brings
                    // the word to the lips. Compared with the pool's usual activity, so only words
                    // said around the sound gain.
                    float post = trace[j] - slowRate[j] / (1f - TRACE_DECAY);
                    w = s.weight[k] + preSign * lr * SPEECH_LEARNING * preTrace * post;
                } else {
                    if (fired[j]) {
                        e += A_PLUS * preTrace;
                        // Only healthy co-firing cements; a runaway neuron must not protect itself.
                        if (preTrace > 0.3f && slowRate[i] < RATE_HIGH && slowRate[j] < RATE_HIGH) {
                            cement[i] = Math.min(1f, cement[i] + CEMENT_GAIN);
                            cement[j] = Math.min(1f, cement[j] + CEMENT_GAIN);
                        }
                    }
                    if (pre) {
                        e -= A_MINUS * (trace[j] - (fired[j] ? 1f : 0f));
                    }
                    s.elig[k] = e;
                    w = s.weight[k] + lr * e * gate * Math.max(LobeId.of(lobe[i]).plasticity, LobeId.of(lobe[j]).plasticity);
                }
                s.weight[k] = w < 0f ? 0f : (w > W_MAX ? W_MAX : w);
            }
        }
    }

    /**
     * A quiet neuron's synapses onto the motor pools still remember what the body did while it was
     * active, and that memory fades slowly, so a reward that comes seconds later (climbing onto the
     * block placed a moment ago) still reaches the action. Wiping it as soon as the neuron fell
     * quiet meant a reward 40 ticks after an action was never learned (1%, now 33-64%). Inborn
     * reflex arcs from receptors still forget at once, which keeps word imitation intact. Returns
     * false once the memory has faded.
     */
    private boolean fadeMotorTraces(int i, SynapseList s, float lr, float motorGate) {
        boolean left = false;
        float preSign = inhibitory[i] ? -1f : 1f;
        for (int k = 0; k < s.size; k++) {
            float e = role[s.target[k]] == ROLE_MOTOR ? s.elig[k] * QUIET_ELIG_DECAY : 0f;
            if (Math.abs(e) < QUIET_ELIG_MIN) {
                s.elig[k] = 0f;
                continue;
            }
            s.elig[k] = e;
            left = true;
            float w = s.weight[k] + preSign * lr * PATHWAY_BOOST * e * motorGate;
            s.weight[k] = w < 0f ? 0f : (w > W_MAX ? W_MAX : w);
        }
        return left;
    }

    private void homeostasis() {
        float resistance = genome.get(Genome.DECAY_RESISTANCE);
        int queued = 0;
        for (int i = 0; i < n; i++) {
            if (role[i] != ROLE_INTERIOR) {
                continue;
            }
            float t = threshold[i] + THRESHOLD_ADAPT * ((fired[i] ? 1f : 0f) - TARGET_RATE);
            threshold[i] = clamp(t, THRESHOLD_MIN, THRESHOLD_MAX);
            float r = slowRate[i];
            if (r < RATE_LOW || r > RATE_HIGH) {
                health[i] -= HEALTH_DECAY * (1f - cement[i] * resistance);
                cement[i] *= CEMENT_LOSS;
                if (health[i] <= 0f && queued < MAX_RESETS_PER_TICK) {
                    resetQueue[queued++] = i;
                }
            } else if (health[i] < 1f) {
                health[i] = Math.min(1f, health[i] + HEALTH_RECOVER);
            }
        }
        for (int q = 0; q < queued; q++) {
            reset(resetQueue[q]);
        }
    }

    /**
     * Slow housekeeping: weak synapses are pruned, and active neurons reach out to dull neurons
     * with few connections.
     */
    private void maintenance() {
        // Each word's usual presence is learned all day long, not only in conversation, and
        // what to say next follows what the concept system holds even between heard words.
        int heldWord = groundedWord(true);
        if (replyUrge >= URGE_MIN && plan != null) {
            expected = planned();
        } else if (replyUrge >= URGE_MIN && heldWord >= 0) {
            expected = heldWord;
        } else {
            maybeRemark();
        }
        // Synaptic scaling, using synInput as scratch space (it is empty between ticks).
        float[] excSum = synInput;
        for (int i = 0; i < n; i++) {
            if (inhibitory[i]) {
                continue;
            }
            SynapseList s = out[i];
            for (int k = 0; k < s.size; k++) {
                excSum[s.target[k]] += s.weight[k];
            }
        }
        for (int i = 0; i < n; i++) {
            if (inhibitory[i]) {
                continue;
            }
            SynapseList s = out[i];
            for (int k = 0; k < s.size; k++) {
                int j = s.target[k];
                float sum = excSum[j];
                // Motor pools are left unscaled so reward can raise how strongly a key is driven.
                if (sum > IN_WEIGHT_MAX && role[j] != ROLE_MOTOR) {
                    s.weight[k] *= IN_WEIGHT_MAX / sum;
                }
            }
        }
        Arrays.fill(synInput, 0, n, 0f);
        for (int i = 0; i < n; i++) {
            SynapseList s = out[i];
            for (int k = s.size - 1; k >= 0; k--) {
                if (s.weight[k] < W_PRUNE) {
                    inDegree[s.target[k]]--;
                    s.removeAt(k);
                }
            }
        }
        int reachOuts = Math.max(1, n / 200);
        for (int a = 0; a < reachOuts; a++) {
            int i = rng.nextInt(n);
            if (role[i] == ROLE_MOTOR || slowRate[i] < TARGET_RATE) {
                continue;
            }
            var rules = Wiring.outgoing(LobeId.of(lobe[i]));
            if (rules.isEmpty()) {
                continue;
            }
            Wiring.Rule rule = rules.get(rng.nextInt(rules.size()));
            int j = pickDullTarget(rule, i);
            if (j >= 0) {
                connect(i, j, initialWeight(rule));
            }
        }
    }

    private void readMotorPools() {
        for (int c = 0; c < MotorChannel.COUNT; c++) {
            int count = 0;
            for (int i : motorPools[c]) {
                if (fired[i]) {
                    count++;
                }
            }
            poolRates[c] = poolRates[c] * 0.6f + 0.4f * count / SenseLayout.MOTOR_POOL_SIZE;
        }
        for (int w = 0; w < SenseLayout.WORD_FEATURES; w++) {
            int count = 0;
            for (int i : speechPools[w]) {
                if (fired[i]) {
                    count++;
                }
            }
            speechRates[w] = speechRates[w] * 0.6f + 0.4f * count / SenseLayout.SPEECH_POOL_SIZE;
        }
    }

    /**
     * The word to say this tick, if any, by vocabulary number: the known word whose features fire
     * most strongly, once most of them fire enough. Two words can share all their features; then
     * the one the language area expects wins. When the line is finished (the language area expects
     * the end of it, or the line is very long), the villager says the end of its turn. A mouth says
     * one word at a time, so this is the one place a single winner is picked.
     */
    /** The last few words heard from others, primed for the voice. */
    private final int[] primed = {-1, -1, -1, -1};
    private int primedNext;

    private int decodeVoice() {
        if (voiceCooldown > 0) {
            voiceCooldown--;
            return -1;
        }
        Vocabulary v = language.vocabulary;
        if (replyUrge >= URGE_MIN && lineWords > 0 && expected >= 0
                && (v.word(expected).equals(WordCode.TURN) || lineWords >= MAX_LINE_WORDS)) {
            replyUrge = 0f;
            voiceCooldown = VOICE_COOLDOWN;
            return v.id(WordCode.TURN);
        }
        // Most of the time the voice is quiet: no word can be on its lips without enough of its
        // features, so there is no need to look through every word.
        int active = 0;
        for (float r : speechRates) {
            active += r > SAY_ON ? 1 : 0;
        }
        if (active < SAY_FEATURES) {
            return -1;
        }
        int word = -1;
        float best = 0f;
        boolean plannedOn = false;
        for (int w = 0; w < v.size(); w++) {
            int[] features = v.features(w);
            if (features.length == 0) {
                continue;
            }
            int on = 0;
            float sum = 0f;
            for (int feature : features) {
                on += speechRates[feature] > SAY_ON ? 1 : 0;
                sum += speechRates[feature];
            }
            // A word just heard is on the tip of the tongue: of words that sound alike to the voice,
            // it is the one that comes out, as a child echoes what it hears.
            for (int p : primed) {
                if (p == w) {
                    sum *= PRIMED;
                    break;
                }
            }
            if (on >= SAY_FEATURES && (sum > best || sum == best && w == expected)) {
                best = sum;
                word = w;
            }
            plannedOn |= w == expected && on >= SAY_FEATURES;
        }
        if (plan != null && plannedOn) {
            // Saying a line it thought through, the word it means comes out over one that sounds
            // alike, or a word just heard would slip into the middle of the line.
            word = expected;
        }
        if (word >= 0 && plan != null && planAt == 0 && lineWords == 0 && word != plan[0]) {
            // It holds its tongue until the line it thought through begins: a word cooed just
            // before is not let out in front of the answer.
            return -1;
        }
        if (word >= 0) {
            voiceCooldown = VOICE_COOLDOWN;
        }
        return word;
    }

    // ---------------------------------------------------------------- growth and rebirth

    /**
     * Gives a brain saved before a lobe existed (the frontal lobe, added later) that lobe's share
     * of neurons, wired in as if they had grown there.
     */
    void addMissingLobes() {
        float totalWeight = 0f;
        for (LobeId l : LobeId.values()) {
            totalWeight += l.growable() ? genome.lobeWeight(l) : 0f;
        }
        for (LobeId l : LobeId.values()) {
            if (!l.growable() || members[l.ordinal()].size > 0 || genome.lobeWeight(l) <= 0f) {
                continue;
            }
            int count = Math.round(n * genome.lobeWeight(l) / totalWeight);
            for (int k = 0; k < count; k++) {
                int i = addNeuron(l, ROLE_INTERIOR);
                wireOut(i);
                wireIn(i);
            }
        }
    }

    int targetSize() {
        return Math.round(genome.get(Genome.ADULT_NEURONS) * (0.6f + 0.4f * maturity));
    }

    /**
     * Sets how grown-up the villager is (0 newborn, 1 adult) and adds up to {@code maxNew} neurons
     * toward the size that maturity calls for, favouring the most active lobes.
     *
     * @return how many neurons were added
     */
    public int grow(float maturity, int maxNew) {
        this.maturity = clamp(Math.max(this.maturity, maturity), 0f, 1f);
        int added = 0;
        while (added < maxNew && n < targetSize()) {
            LobeId l = pickGrowthLobe();
            int i = addNeuron(l, ROLE_INTERIOR);
            wireOut(i);
            wireIn(i);
            added++;
        }
        return added;
    }

    private LobeId pickGrowthLobe() {
        LobeId[] lobes = LobeId.values();
        float[] w = new float[lobes.length];
        float total = 0f;
        for (LobeId l : lobes) {
            if (!l.growable()) {
                continue;
            }
            float activity = Math.min(3f, lobeActivity(l) / TARGET_RATE);
            w[l.ordinal()] = genome.lobeWeight(l) * (0.5f + activity);
            total += w[l.ordinal()];
        }
        float pick = rng.nextFloat() * total;
        for (LobeId l : lobes) {
            pick -= w[l.ordinal()];
            if (pick <= 0f && w[l.ordinal()] > 0f) {
                return l;
            }
        }
        return LobeId.CONCEPT;
    }

    private void reset(int i) {
        for (int p = 0; p < n; p++) {
            SynapseList s = out[p];
            for (int k = s.size - 1; k >= 0; k--) {
                if (s.target[k] == i) {
                    s.removeAt(k);
                }
            }
        }
        SynapseList own = out[i];
        for (int k = 0; k < own.size; k++) {
            inDegree[own.target[k]]--;
        }
        own.clear();
        inDegree[i] = 0;
        links.forget(i);
        initState(i);
        inhibitory[i] = rng.nextFloat() < INHIBITORY_FRACTION;
        wireOut(i);
        wireIn(i);
        resets++;
    }

    private int addNeuron(LobeId l, byte r) {
        return addNeuron(l, r, rng.nextFloat(), rng.nextFloat());
    }

    private int addNeuron(LobeId l, byte r, float x, float y) {
        if (n == lobe.length) {
            allocate(n * 3 / 2);
            if (forwardModel != null) {
                forwardModel.ensureCapacity(lobe.length);
                critic.ensureCapacity(lobe.length);
            }
            links.ensureNeurons(lobe.length);
        }
        int i = n++;
        lobe[i] = (byte) l.ordinal();
        role[i] = r;
        inhibitory[i] = r == ROLE_INTERIOR && rng.nextFloat() < INHIBITORY_FRACTION;
        inputSlot[i] = -1;
        out[i] = new SynapseList(r == ROLE_MOTOR || r == ROLE_SPEECH ? 0 : 16);
        initState(i);
        posX[i] = x;
        posY[i] = y;
        index(i);
        return i;
    }

    /** Adds a neuron to its lobe's lists and to the sheet grid. */
    void index(int i) {
        int l = lobe[i];
        int cell = cell(posX[i], posY[i]);
        members[l].add(i);
        memberGrid[l][cell].add(i);
        if (role[i] != ROLE_RECEPTOR) {
            targetable[l].add(i);
            targetGrid[l][cell].add(i);
        }
    }

    private static int cell(float x, float y) {
        int cx = Math.min(GRID - 1, Math.max(0, (int) (x * GRID)));
        int cy = Math.min(GRID - 1, Math.max(0, (int) (y * GRID)));
        return cy * GRID + cx;
    }

    /** A random neuron from the grid, near (x, y) on the lobe's sheet. */
    private int pickNear(IntList[] grid, IntList all, float x, float y) {
        for (int attempt = 0; attempt < 4; attempt++) {
            IntList c = grid[cell(x + rng.gaussian() * TOPO_SIGMA, y + rng.gaussian() * TOPO_SIGMA)];
            if (c.size > 0) {
                return c.get(rng.nextInt(c.size));
            }
        }
        return all.size == 0 ? -1 : all.get(rng.nextInt(all.size));
    }

    private void initState(int i) {
        potential[i] = 0f;
        threshold[i] = THRESHOLD_START;
        trace[i] = 0f;
        fastRate[i] = 0f;
        slowRate[i] = TARGET_RATE;
        health[i] = 1f;
        cement[i] = 0f;
        refractory[i] = 0;
        fired[i] = false;
        firedPrev[i] = false;
        plastic[i] = false;
        synInput[i] = 0f;
        extInput[i] = 0f;
    }

    private void wireOut(int i) {
        float conn = genome.get(Genome.CONNECTIVITY);
        for (Wiring.Rule rule : Wiring.outgoing(LobeId.of(lobe[i]))) {
            int count = stochasticRound(rule.synapsesPerNeuron() * conn);
            for (int c = 0; c < count; c++) {
                int j = pickDullTarget(rule, i);
                if (j >= 0) {
                    connect(i, j, initialWeight(rule));
                }
            }
        }
    }

    private void wireIn(int i) {
        if (role[i] == ROLE_RECEPTOR) {
            return;
        }
        float conn = genome.get(Genome.CONNECTIVITY);
        LobeId to = LobeId.of(lobe[i]);
        int toSize = Math.max(1, targetable[to.ordinal()].size);
        for (Wiring.Rule rule : Wiring.incoming(to)) {
            IntList from = members[rule.from().ordinal()];
            if (from.size == 0 || !rule.accepts(posY[i])) {
                continue;
            }
            float expected = rule.synapsesPerNeuron() * conn * from.size / (toSize * rule.band().share());
            int count = stochasticRound(Math.min(expected, rule.synapsesPerNeuron() * 3f));
            for (int c = 0; c < count; c++) {
                int src = rule.to() == LobeId.MOTOR || rule.diffuse()
                        ? from.get(rng.nextInt(from.size))
                        : pickNear(memberGrid[rule.from().ordinal()], from, posX[i], rule.band().outOf(posY[i]));
                if (src >= 0 && src != i) {
                    connect(src, i, initialWeight(rule));
                }
            }
        }
    }

    /** Prefers quiet neurons with few incoming connections, as the notebook describes. */
    private int pickDullTarget(Wiring.Rule rule, int source) {
        LobeId l = rule.to();
        IntList pool = targetable[l.ordinal()];
        if (pool.size == 0) {
            return -1;
        }
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        boolean mapped = l != LobeId.MOTOR && !rule.diffuse();
        Wiring.Band band = rule.band();
        for (int c = 0; c < CANDIDATES; c++) {
            int j = mapped
                    ? pickNear(targetGrid[l.ordinal()], pool, posX[source], band.into(posY[source]))
                    : pool.get(rng.nextInt(pool.size));
            if (j < 0 || j == source || !rule.accepts(posY[j])) {
                continue;
            }
            float score = inDegree[j] + 200f * slowRate[j];
            if (score < bestScore) {
                bestScore = score;
                best = j;
            }
        }
        return best;
    }

    private float initialWeight(Wiring.Rule rule) {
        return W_INIT * rng.nextFloat();
    }

    private void connect(int from, int to, float w) {
        SynapseList s = out[from];
        if (s.contains(to)) {
            return;
        }
        s.add(to, w);
        inDegree[to]++;
    }

    private int stochasticRound(float x) {
        int base = (int) x;
        return base + (rng.nextFloat() < x - base ? 1 : 0);
    }

    private void allocate(int cap) {
        lobe = lobe == null ? new byte[cap] : Arrays.copyOf(lobe, cap);
        role = role == null ? new byte[cap] : Arrays.copyOf(role, cap);
        inhibitory = inhibitory == null ? new boolean[cap] : Arrays.copyOf(inhibitory, cap);
        potential = grow(potential, cap);
        threshold = grow(threshold, cap);
        trace = grow(trace, cap);
        fastRate = grow(fastRate, cap);
        slowRate = grow(slowRate, cap);
        health = grow(health, cap);
        cement = grow(cement, cap);
        synInput = grow(synInput, cap);
        posX = grow(posX, cap);
        posY = grow(posY, cap);
        extInput = grow(extInput, cap);
        refractory = refractory == null ? new byte[cap] : Arrays.copyOf(refractory, cap);
        fired = fired == null ? new boolean[cap] : Arrays.copyOf(fired, cap);
        firedPrev = firedPrev == null ? new boolean[cap] : Arrays.copyOf(firedPrev, cap);
        plastic = plastic == null ? new boolean[cap] : Arrays.copyOf(plastic, cap);
        inDegree = inDegree == null ? new int[cap] : Arrays.copyOf(inDegree, cap);
        inputSlot = inputSlot == null ? new int[cap] : Arrays.copyOf(inputSlot, cap);
        out = out == null ? new SynapseList[cap] : Arrays.copyOf(out, cap);
    }

    private static float[] grow(float[] a, int cap) {
        return a == null ? new float[cap] : Arrays.copyOf(a, cap);
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ---------------------------------------------------------------- read-outs

    public int neuronCount() {
        return n;
    }

    public int neuronCount(LobeId l) {
        return members[l.ordinal()].size;
    }

    public long synapseCount() {
        long total = 0;
        for (int i = 0; i < n; i++) {
            total += out[i].size;
        }
        return total;
    }

    /** Fraction of a lobe's neurons firing per tick, averaged over the last few seconds. */
    public float lobeActivity(LobeId l) {
        IntList m = members[l.ordinal()];
        if (m.size == 0) {
            return 0f;
        }
        float s = 0f;
        for (int k = 0; k < m.size; k++) {
            s += slowRate[m.get(k)];
        }
        return s / m.size;
    }

    /** The meaning cells of the concept system, whose activity words are linked to. */
    int[] meaningCells() {
        gatherMeaning();
        return Arrays.copyOf(meaning, meaningCount);
    }

    public float poolRate(MotorChannel c) {
        return poolRates[c.ordinal()];
    }

    public float dopamine() {
        return dopamine;
    }

    public float surprise() {
        return surprise;
    }

    public float maturity() {
        return maturity;
    }

    public long ticks() {
        return ticks;
    }

    public long resets() {
        return resets;
    }

    public Genome genome() {
        return genome;
    }

    public MotorCommand lastCommand() {
        return lastCommand;
    }

    /** Scales motor babbling, for example 0 to see only what the villager has learned. */
    public void setBabbleScale(float scale) {
        babbleScale = Math.max(0f, scale);
    }

    public float babbleScale() {
        return babbleScale;
    }
}
