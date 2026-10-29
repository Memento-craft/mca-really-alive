package com.mementocraft.reallyalive.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    static final int TOLD_LINES = 15;
    static final float TOLD_LINK = 2f;
    /** How alike two words must be for one to stand for the other in a question. */
    static final float TOLD_ALIKE = Float.parseFloat(System.getProperty("reallyalive.toldAlike", "0.4"));
    static final float TOLD_TELLING = 5f;
    /** How telling a name is in a question about something told. */
    static final float TOLD_NAME = 8f;
    static final float TOLD_COVER = 0.5f;
    static final int TOLD_QUESTION_WORDS = 10;
    /** Lines told longer ago than this many have to be asked about more fully to come back. */
    static final int TOLD_RECENT = 8;
    static final float TOLD_COVER_OLD = 0.6f;
    /** A told line longer than this is a story, not a thing told to be asked about. */
    static final int TOLD_LONGEST = 24;
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
    static final int INPUT_SIZE = IN_COLOUR + SenseLayout.COLOUR_RECEPTORS;

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
     * into it, which is how a player climbs. Learning can still
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
        // The Motor lobe's sheet: controls in the lower half, the voice in the upper half.
        for (int c = 0; c < MotorChannel.COUNT; c++) {
            for (int k = 0; k < SenseLayout.MOTOR_POOL_SIZE; k++) {
                motorPools[c][k] = addNeuron(LobeId.MOTOR, ROLE_MOTOR, rng.nextFloat(),
                        Wiring.Band.KEYS.into(rng.nextFloat()));
            }
        }
        for (int w = 0; w < SenseLayout.WORD_FEATURES; w++) {
            for (int k = 0; k < SenseLayout.SPEECH_POOL_SIZE; k++) {
                speechPools[w][k] = addNeuron(LobeId.MOTOR, ROLE_SPEECH, rng.nextFloat(),
                        Wiring.Band.VOICE.into(rng.nextFloat()));
            }
        }
    }

    private void buildColourReceptors() {
        for (int i = 0; i < SenseLayout.COLOUR_RECEPTORS; i++) {
            receptor(LobeId.OCCIPITAL, IN_COLOUR + i);
        }
    }

    /** Brains saved before they had colour vision get their colour receptors. */
    void addMissingReceptors() {
        for (int i = 0; i < n; i++) {
            if (role[i] == ROLE_RECEPTOR && inputSlot[i] >= IN_COLOUR) {
                return;
            }
        }
        int first = n;
        buildColourReceptors();
        for (int i = first; i < n; i++) {
            wireOut(i);
        }
    }

    private int receptor(LobeId l, int slot) {
        float x = rng.nextFloat();
        float y = rng.nextFloat();
        if (slot >= IN_COLOUR) {
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
            int ask = language.vocabulary.find("?");
            if (line.length >= 3 && line.length <= TOLD_LONGEST && line[line.length - 1] != ask) {
                talk.rememberTold(line);
            }
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
        if (topicHeard != null && toldLine == null) {
            // Nothing told answers it; what it knows of the world may ("is a horse bigger than a
            // mouse?").
            toldLine = judged(topicHeard);
            toldLineAsHeard = null;
            language.restore(start);
        }
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
            talk.ask = language.vocabulary.find("?");
            if (talk.you.length == 0) {
                talk.you = java.util.stream.Stream.of("you", "your", "yourself").mapToInt(language.vocabulary::find).filter(w -> w >= 0).sorted().toArray();
            }
            int[][] recalled = memory.recall(heard, before, RECALL_BEFORE, RECALLED, match, talk.ask);
            int keep = 0;
            for (int r = 0; r < recalled.length; r++) {
                int[] kept = keptTrue(heard, recalled[r]);
                if (kept != null) {
                    match[keep] = match[r];
                    recalled[keep++] = kept;
                }
            }
            recalled = Arrays.copyOf(recalled, keep);
            weighRecalled(recalled, match, RECALL_WEIGHT, false, start, turn);
            int[][] mine = talk.recall(heard, before, 0f, RECALLED, match, memory);
            if (toldLine != null) {
                // The told line that answers the question was picked from all told lately; the
                // others are not brought up again by their words alone.
                int kept = 0;
                List<int[]> keptTold = talk.toldLines();
                for (int r = 0; r < mine.length; r++) {
                    // Nor is what it once answered from a line told long ago ("the cart is in the
                    // barn ."), when a later line answers now.
                    final int[] once = mine[r];
                    boolean toldOnce = toldLately(once) || keptTold.stream().anyMatch(old -> Arrays.equals(old, once));
                    if (!toldOnce || Arrays.equals(mine[r], toldLineAsHeard)) {
                        match[kept] = match[r];
                        mine[kept++] = mine[r];
                    }
                }
                mine = Arrays.copyOf(mine, kept);
                // Asked about themselves, what someone said in the first person ("my cat ran
                // away.") was said by them, and is said back to them the other way round.
                for (int r = 0; r < mine.length; r++) {
                    mine[r] = aboutSpeaker(mine[r]) ? toldBack(mine[r]) : mine[r];
                }
            }
            weighRecalled(mine, match, OWN_RECALL_WEIGHT, true, start, turn);
            if (toldLine != null) {
                // What it was told, said back the other way round: "you have a dog named rex."
                // What was done in order is worked out from the story, and no remembered line about
                // the same things ("pigs live in farms .") answers it instead.
                weighRecalled(new int[][] {toldInOwnWords ? toldLine : toldBack(toldLine)}, new float[] {1f},
                        toldSure ? 2 * TOLD_WEIGHT : TOLD_WEIGHT, true, start, turn);
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
    /** Whether the told line is surely the answer: worked out by order, or that it doesn't know. */
    private boolean toldSure;
    /** Whether the told line is already said as this villager would say it ("rosa is my mother ."). */
    private boolean toldInOwnWords;
    /** The told line as it was heard, before any "yes ," or "because" was put in front. */
    private int[] toldLineAsHeard;

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
     * its telling words that the question lacks ("rex") are what the answer should say. Asked about
     * someone or something else ("where is the key?"), any line told lately can answer, if it said
     * most of what the question asks about. Returns those words, or none if the heard line is no
     * question or nothing told fits it well enough.
     */
    /** A line told in order, without the "then" it was told with: "you milked the cow ." */
    private int[] withoutThen(int[] ordered) {
        if (ordered != null && ordered.length > 2 && ordered[0] == language.vocabulary.find("then")) {
            return Arrays.copyOfRange(ordered, 1, ordered.length);
        }
        return ordered;
    }

    private int[] toldBefore(int[] heardLine) {
        ExchangeMemory memory = ExchangeMemory.shared();
        int ask = language.vocabulary.find("?");
        toldLine = null;
        toldInOwnWords = false;
        toldSure = false;
        // A short, plain question about the speaker ("where do i live?"), not a long outburst
        // that ends in one.
        int[] plain = withoutAddress(heardLine);
        if (plain.length > 3 && plain[plain.length - 2] == language.vocabulary.find("now")) {
            // "where is the cat now?" asks where it is: what happened last tells.
            plain = withoutAt(plain, plain.length - 2);
        }
        // "and john?" after "where is mary?": "where is john?"
        int[] meant = plain.length == 0 ? null : followedUp(plain, 2);
        if (meant == null && plain.length > 0) {
            // "where is it now?" after "where was the cat?" asks of the cat.
            meant = ofTheSame(plain);
        }
        int[] reasoned = meant == null && plain.length > 0 ? fromRules(plain) : null;
        if (reasoned != null) {
            // "all blicks are furry." "zim is a blick.": "is zim furry?" "yes , all blicks are
            // furry , and zim is a blick ."
            toldLine = reasoned;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            toldSure = true;
            return new int[0];
        }
        kindLine = null;
        if (meant == null && plain.length > 0) {
            // "how many legs does rex have?" after "rex is a dog .": what is so of dogs.
            meant = ofItsKind(plain);
        }
        if (meant == null && plain.length > 0) {
            // "where is he?" after "tom went to the mill .": "where is tom?"; with no one told
            // of at all, it asks who is meant.
            meant = ofWhoIsMeant(plain);
            if (meant != null && meant.length == 0) {
                toldLine = said(unclear);
                toldLineAsHeard = null;
                toldInOwnWords = true;
                toldSure = true;
                return new int[0];
            }
        }
        int[] heard = meant != null ? meant : plain;
        int[] again = saidAgain(heard);
        if (again != null) {
            // "what did you say?" "i said we'll keep looking ."; "what?" says it again.
            toldLine = again;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            toldSure = true;
            return new int[0];
        }
        int[] odd = heard.length <= TOLD_LONGEST ? oddOneOut(heard) : null;
        if (odd != null) {
            // "which one is not a fruit, an apple, a pear or a stone?" "a stone is not a fruit ."
            toldLine = odd;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            return new int[0];
        }
        int[] against = saidOtherwise(heard);
        if (against != null) {
            // "the cart is red." then "the cart is blue.": "wait , you said the cart is red ."
            toldLine = against;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            toldSure = true;
            return new int[0];
        }
        if (memory != null && heard.length > TOLD_QUESTION_WORDS && heard.length <= TOLD_QUESTION_WORDS + 4
                && heard[heard.length - 1] == ask) {
            // A longer question may still ask of the order of things ("what did the guard do
            // before he went to sleep?").
            toldLine = withoutThen(inOrder(heard));
            toldSure = toldLine != null;
            toldLine = toldLine != null ? toldLine : compared(heard);
            toldLineAsHeard = null;
            return new int[0];
        }
        if (memory == null || heard.length == 0 || heard.length > TOLD_QUESTION_WORDS || heard[heard.length - 1] != ask) {
            return new int[0];
        }
        int[] placed = whereFrom(heard);
        if (placed != null) {
            // "the well is north of the mill." "the mill is north of the church.": "is the church
            // south of the well?" "yes , the church is south of the well , because ..."
            toldLine = placed;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            toldSure = true;
            return new int[0];
        }
        int[] general = generalised(heard);
        if (general != null) {
            // "tom's goat is brown." "ben's goat is brown." "ana's goat is brown.": "what colour
            // are goats?" "each goat i heard of is brown ."; "are all goats brown?" after one white
            // goat: "no , ben's goat is white ."
            toldLine = general;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            toldSure = true;
            return new int[0];
        }
        int[] feeling = feelingNow(heard);
        if (feeling != null) {
            // "how do you feel?" when hurt: "i'm hurt ."; "why are you scared?" "because there is
            // a zombie around ."
            toldLine = feeling;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            toldSure = true;
            return new int[0];
        }
        int[] safe = isItSafe(heard);
        if (safe != null) {
            // "is it safe to go outside?" at night: "no , it's night , and monsters come out at
            // night ."
            toldLine = safe;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            toldSure = true;
            return new int[0];
        }
        int[] reckonedHere = numbersTold(heard);
        if (reckonedHere != null) {
            // "how old is lisa's brother?" after "lisa is 8." "her brother is 3 years older.":
            // "lisa's brother is eleven ."
            toldLine = reckonedHere;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            toldSure = true;
            return new int[0];
        }
        int[] weighed = compared(heard);
        if (weighed == null && !ofAllAsked) {
            weighed = whichOfTwo(heard);
        }
        if (weighed == null) {
            weighed = countedList(heard);
        }
        if (weighed != null) {
            // "who has more apples?" after "tom has three apples ." "anna has five apples .":
            // "anna has five apples ."; "which comes first, friday or sunday?" "friday comes first ."
            toldLine = weighed;
            toldLineAsHeard = null;
            return new int[0];
        }
        int[] reckoned = worked(heard);
        if (reckoned == null) {
            reckoned = nextInOrder(heard);
        }
        if (reckoned != null) {
            // "what is two plus three?" "two plus three is five ."
            toldLine = reckoned;
            toldLineAsHeard = null;
            return new int[0];
        }
        int[] how = howToMake(heard);
        if (how != null) {
            // "how do i make bread?" "to make bread , you need three wheat ."
            toldLine = how;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            return new int[0];
        }
        int[] has = possessions(heard);
        if (has != null) {
            // "do you have any bread?" "yes , i have three bread ."
            toldLine = has;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            return new int[0];
        }
        int[] kin = family(heard);
        if (kin != null) {
            // "who is anna's son?" after "anna is ben's mother .": "ben is anna's son ."
            toldLine = kin;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            return new int[0];
        }
        int[] day = whatDay(heard);
        if (day != null) {
            // "what day is tomorrow?" after "today is monday .": "tomorrow is tuesday ."
            toldLine = day;
            toldLineAsHeard = null;
            return new int[0];
        }
        int[] many = howMany(heard);
        if (many != null) {
            // "how many apples do i have?" after "i have three apples and i eat one .": "you
            // have two apples ."
            toldLine = many;
            toldLineAsHeard = null;
            return new int[0];
        }
        int[] near = aroundHere(heard);
        if (near != null) {
            // "how many cows are there?" "there are two cows ."
            toldLine = near;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            return new int[0];
        }
        int[] follows = ruleFollows(heard);
        if (follows != null) {
            // "if it rains, we stay home." "it is raining.": "what do we do?" "we stay home ."
            toldLine = follows;
            toldLineAsHeard = null;
            toldInOwnWords = true;
            return new int[0];
        }
        int[] work = whatTheyDo(heard);
        if (work != null) {
            // "what do i do?" after "i run the bakery .": "you run the bakery ."
            toldLine = work;
            toldLineAsHeard = null;
            return new int[0];
        }
        int[] ordered = withoutThen(inOrder(heard));
        if (ordered != null) {
            // "tom fed the pigs." "then he milked the cow.": "what did tom do first?" "tom fed
            // the pigs ."; "what did he do after he fed the pigs?" "he milked the cow ."
            toldLine = ordered;
            toldSure = true;
            toldLineAsHeard = null;
            return new int[0];
        }
        int[] most = mostOf(heard);
        if (most != null) {
            // "who is the oldest?" after "tom is older than sam." "sam is older than ben.":
            // "tom is the oldest ."
            toldLine = most;
            toldLineAsHeard = null;
            return new int[] {most[most.length - 5]};
        }
        int[] anyone = generalAsk ? null : askedOfAnyone(heard);
        if (anyone != null) {
            // "how do i get iron?" asks what anyone does: "to get iron , you mine iron ore with a
            // stone pickaxe ."; "what should i do if i am hungry?" "you should eat some food ."
            generalAsk = true;
            int[] r = toldBefore(anyone);
            generalAsk = false;
            if (toldLine != null && !toldSure) {
                return r;
            }
            toldLine = null;
            toldSure = false;
        }
        boolean aboutThem = aboutSpeaker(heard);
        ToneLinks tones = ToneLinks.shared();
        float asked = 0f;
        for (int w : heard) {
            asked += toldWeight(w, memory, tones);
        }
        int name = language.vocabulary.find("name");
        boolean askingName = contains(heard, name);
        int[] best = null;
        int[] bestSeen = null;
        int bestK = 0;
        float bestCue = TOLD_CUE;
        float bestExact = 0f;
        int[][] names = namesGiven();
        // The latest line is the question itself; only what the speaker told about themselves
        // lately, not other questions, is recalled.
        // Then what it was told longer ago and kept, even before a save.
        List<int[]> toldLines = new ArrayList<>();
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            toldLines.add(heardLines[(heardLinesNext - k) % heardLines.length]);
        }
        int recent = toldLines.size();
        for (int[] old : talk.toldLines()) {
            boolean lately = false;
            for (int k = 1; k <= TOLD_LINES + 1 && k <= heardLinesNext; k++) {
                lately |= Arrays.equals(old, heardLines[(heardLinesNext - k) % heardLines.length]);
            }
            if (!lately) {
                toldLines.add(old);
            }
        }
        // Last, what everyone knows from growing up ("a spider has eight legs ."), only if
        // nothing it was told answers.
        int knownFrom = toldLines.size();
        int bestT = -1;
        int[] knownBest = null;
        toldLines.addAll(knownToAll(language.vocabulary));
        for (int t = 0; t < toldLines.size(); t++) {
            if (t == knownFrom && (best != null || !generalAsk && saysAny(heard, OF_THE_MOMENT))) {
                // What everyone knows is of kinds of things, not of the one talking or of now
                // ("is it raining?", "what have you found out?").
                break;
            }
            int k = t < recent ? t + 2 : TOLD_LINES + 1;
            int[] told = toldLines.get(t);
            if (told.length == 0 || told.length > TOLD_LONGEST || told[told.length - 1] == ask
                    || aboutThem && !aboutSpeaker(told) && !namesTheirs(told, names) && !rule(told)
                    || ofAllAsked && aboutSpeaker(told)) {
                // A rule is for anyone: "if you feed the dog, it wags its tail." answers "what
                // happens if i feed the dog?".
                continue;
            }
            if (told == kindLine) {
                // "rex is a dog ." says what kind rex is, not what is so of dogs.
                continue;
            }
            List<int[]> givenHere = new ArrayList<>();
            if (askingName && namesIn(told, givenHere) && givenHere.stream().noneMatch(g -> g[1] >= 0 && saidIn(heard, g[1]))) {
                // "my brother is called leo ." names the brother, not the one asking "what's my name?".
                continue;
            }
            if (!askingName && !contains(heard, language.vocabulary.find("called")) && namesIn(told, new ArrayList<>())) {
                // Giving a name answers what something is called, not what it did.
                continue;
            }
            // A thing called by its name is still the thing ("rex is sick." after "my dog is
            // called rex." tells of the dog).
            int[] seen = withWhatTheyName(told, names);
            if (t + 1 < recent && saysAny(told, AGAIN) && saysSome(heard, told, memory, tones)) {
                // "he keeps it in the stable ." after "ivan owns a horse ." tells of ivan and the
                // horse, when it says something the question asks itself ("keep"); "then she ate
                // a big meal ." says nothing of being hungry.
                seen = withThingsOf(seen, toldLines.get(t + 1));
            }
            float cue = 0f;
            boolean named = false;
            for (int w : seen) {
                if (w >= 0 && saidIn(heard, w)) {
                    cue += toldWeight(w, memory, tones);
                }
                named |= w >= 0 && memory.weight(w) == 0f && tones != null && !tones.common(w)
                        && Character.isLetter(language.vocabulary.word(w).charAt(0));
            }
            if (askingName && named) {
                // "what's my name?" asks for a name, and "hi, i'm josh." gave one.
                cue += memory.weight(name);
            }
            float exact = cue;
            float telling = 0f;
            float linked = 0f;
            TopicLinks topics = TopicLinks.shared();
            for (int q : heard) {
                if (q < 0 || toldWeight(q, memory, tones) == 0f) {
                    continue;
                }
                if (saidIn(seen, q)) {
                    telling = Math.max(telling, toldWeight(q, memory, tones));
                } else if (answersKind(q, told) || yesNoAsked(heard) && saysOpposite(told, q)) {
                    // "what colour is pia's hat?" is answered by "pia has a red hat .", "whose
                    // garden is bigger?" by "hilda has a big garden .".
                    telling = Math.max(telling, toldWeight(q, memory, tones));
                    cue += toldWeight(q, memory, tones);
                } else if (topics != null) {
                    // A word of the question the line did not say, but one that goes with a word
                    // it did ("what was he wearing?" "a purple coat.").
                    // Or a word that stands for a like thing ("wedding" for "married").
                    boolean goes = false, same = false;
                    for (int w : told) {
                        same |= w >= 0 && topics.alike(q, w) >= TOLD_ALIKE;
                        goes |= w >= 0 && topics.link(q, w) >= TOLD_LINK;
                    }
                    linked += goes || same ? toldWeight(q, memory, tones) : 0f;
                    if (same) {
                        telling = Math.max(telling, toldWeight(q, memory, tones));
                        cue += toldWeight(q, memory, tones);
                    }
                }
            }
            boolean theirName = askingName && named && aboutThem && aboutSpeaker(told);
            if ((!aboutThem || k > TOLD_LINES) && !theirName && (cue + linked < (k <= TOLD_RECENT ? TOLD_COVER : TOLD_COVER_OLD) * asked
                    || telling < TOLD_TELLING)) {
                // About someone or something else, the question has to be mostly about what the
                // line told ("where is the key?" after "the red key is under the bed."), and so
                // does a line told long ago, maybe by someone else.
                continue;
            }
            if (t >= knownFrom && !answersAsked(heard, told)) {
                // Nor one that doesn't give what is asked ("to get coal , you mine coal ore ." for
                // "what colour is coal?", "a rabbit is an animal ." for "is a bear bigger than a
                // rabbit?").
                continue;
            }
            if (t >= knownFrom && !saysSome(heard, told, memory, tones)) {
                // A fact has to say something the question says, not just something like it
                // ("how's the weather?" is not about the seasons).
                continue;
            }
            if (t >= knownFrom && kindLine != null && cue > bestCue + 1e-4f) {
                knownBest = told;
            }
            if (cue > bestCue + 1e-4f || cue > bestCue - 1e-4f && exact > bestExact) {
                bestT = t;
                // A line that says the word itself goes before one that says a like word ("my
                // brother" before "my sister").
                bestCue = cue;
                bestExact = exact;
                best = told;
                bestSeen = seen;
                // Lines from longer ago are not among those heard lately.
                bestK = k <= TOLD_LINES ? k : 0;
            }
        }
        if (best == null || !coversAll(heard, bestSeen, memory, tones)) {
            // No one line says it all, but a few that tell of the same things may: "tom is older
            // than sam." and "sam is older than ben." answer "is tom older than ben?", "a wolf is
            // at the gate." and "wolves are dangerous." answer "is the thing at the gate dangerous?".
            List<int[]> chain = linesTogether(heard, names, memory, tones);
            int[] went = chain == null ? null : wentLast(heard, chain);
            if (went != null) {
                // "mary went to the kitchen." "then she went to the garden.": "where is mary?"
                // "mary went to the garden ."
                chain = List.of(went);
            }
            if (chain != null) {
                best = new int[0];
                for (int[] line : chain) {
                    int[] longer = Arrays.copyOf(best, best.length + line.length);
                    System.arraycopy(line, 0, longer, best.length, line.length);
                    best = longer;
                }
                bestSeen = best;
                bestK = 0;
            }
        }
        int[] ofAll = best == null ? null : ofAllSuch(heard, best);
        if (ofAll != null) {
            // "what do my bees make?" after "i keep bees .": what bees make, "bees make honey ."
            ofAllAsked = true;
            int[] r = toldBefore(ofAll);
            ofAllAsked = false;
            if (toldLine != null) {
                return r;
            }
        }
        if (best == null) {
            int[] ofKind = ofAllAsked ? null : ofTheNamed(heard, null);
            if (ofKind != null) {
                // "how many legs does rex have?" after "i have a dog called rex .": what is so
                // of dogs.
                ofAllAsked = true;
                int[] r = toldBefore(ofKind);
                ofAllAsked = false;
                if (toldLine != null) {
                    return r;
                }
            }
            if (unheardOf(heard, memory, tones)) {
                // "where is my hat?" when no one said: "i don't know ."
                dontKnow();
            }
            return new int[0];
        }
        if (bestT >= recent && bestT < knownFrom && best == toldLines.get(bestT)) {
            // Told long ago, maybe changed since, also long ago ("the key is in the drawer ."
            // then "i moved the key to the shelf .").
            int[] since = movedSince(heard, toldLines, recent, bestT);
            if (since != null) {
                best = since;
                bestSeen = since;
            }
        }
        int[] later = toldLater(heard, best, bestK);
        boolean changed = later != null;
        if (changed) {
            // "mary went to the kitchen." "mary went to the garden.": "where is mary?" is answered
            // by what happened last.
            best = later;
            bestSeen = later;
        }
        int[] there = placeOf(heard, best, toldLines);
        if (there != null) {
            // "is max in the library?" is about max, though "lisa is in the library ." says more
            // of the question: "max is at the market ." answers it.
            best = there;
            bestSeen = there;
        }
        int[] further = whereThatIs(heard, best);
        if (further != null) {
            // "the key is in the box ." "the box is in the barn .": the key is in the barn too.
            best = join(best, further);
            bestSeen = best;
            toldSure = true;
        }
        // "mary went to the garden .", not "then mary went to the garden ."
        best = withoutThen(best);
        if (!answersAsked(heard, bestSeen != null ? join(best, bestSeen) : best)) {
            int[] ofKind = ofAllAsked ? null : ofTheNamed(heard, best);
            if (ofKind != null) {
                // "how many legs does rex have?" after "i have a dog called rex .": what is so of
                // dogs.
                ofAllAsked = true;
                int[] r = toldBefore(ofKind);
                ofAllAsked = false;
                if (toldLine != null && !toldSure) {
                    return r;
                }
            }
            // "what colour is my cat?" after "i have a cat .": the line is of the cat, but says
            // nothing of what is asked.
            dontKnow();
            return new int[0];
        }
        toldLine = best;
        toldLineAsHeard = best;
        int quality = yesNoAsked(heard) ? qualityAsked(heard) : -1;
        // "is the tavern quiet at night?" after "the tavern is loud at night .": no.
        int answer = quality >= 0 && !saidIn(best, quality) && saysOpposite(best, quality) ? language.vocabulary.find("no")
            : yesOrNo(heard, bestSeen, memory, tones);
        if (answer < 0 && heard.length > 2 && heard[0] >= 0 && ASKS_YES_OR_NO.contains(language.vocabulary.word(heard[0]))) {
            // "the door was open." "i closed the door.": "is the door open?" "no , ...";
            // "kai turned the lamp on.": "is the lamp on?" "yes , ..."
            int state = heard[heard.length - 2];
            if (state < 0 || !hasOpposite(language.vocabulary.word(state))) {
                state = -1;
            }
            int end = best.length - 1;
            while (end > 0 && best[end] >= 0 && !Character.isLetter(language.vocabulary.word(best[end]).charAt(0))) {
                end--;
            }
            answer = state < 0 ? placeAnswer(heard, best) : saysOpposite(best, state) ? language.vocabulary.find("no")
                    : (changed ? saidIn(best, state) : best[end] == state) ? language.vocabulary.find("yes") : -1;
        }
        if (answer >= 0) {
            // "is the baker sick?" asks yes or no: "yes , the baker is sick today ."
            toldLine = new int[best.length + 2];
            toldLine[0] = answer;
            toldLine[1] = language.vocabulary.find(",");
            System.arraycopy(best, 0, toldLine, 2, best.length);
            if (there != null) {
                // Not "max is at the market ." bare, which says nothing of the library.
                toldLineAsHeard = null;
            }
        }
        if (contains(heard, language.vocabulary.find("why")) && bestK > 2 && !contains(best, language.vocabulary.find("because"))) {
            // "why am i sad?": the why was told just after ("i'm sad today." "my cat ran away."),
            // and of someone else, told of them again ("the miller is angry." "his cart broke.").
            int[] next = heardLines[(heardLinesNext - bestK + 1) % heardLines.length];
            if (next.length > 0 && next[next.length - 1] != ask && (aboutThem ? aboutSpeaker(next) : toldOfAgain(next) || saysAny(next, AGAIN))) {
                best = next;
                toldLineAsHeard = next;
                toldLine = new int[next.length + 1];
                toldLine[0] = language.vocabulary.find("because");
                System.arraycopy(next, 0, toldLine, 1, next.length);
            }
        }
        int[] toIt = answer < 0 && toldLine[0] != language.vocabulary.find("because") ? toTheQuestion(heard, best) : null;
        if (toIt != null) {
            // "what happens if it rains?" "the field floods ."; "why did the field flood?"
            // "because it rained all night ."
            toldLine = toIt;
        }
        if (answer < 0 && yesNoAsked(heard) && onlyIfSaid(heard, best)) {
            // "did the horn sound?" after "when the horn sounds , the gates open ." and "the
            // gates opened .": something else may have opened them.
            dontKnow();
            return new int[0];
        }
        if (heard[0] == language.vocabulary.find("why") && !saysAny(toldLine, WHY_WORDS)) {
            // "why is the farmer sick?" after only "the farmer is sick .".
            dontKnow();
            return new int[0];
        }
        // Telling words: those that cue remembered exchanges, and names, which never do but are
        // not among the commonest words either.
        // What kind it is ("tibbs is a cat") was told, not asked; what is so of the kind is the answer.
        int[] kindTold = kindLine != null && knownBest != null ? kindLine : new int[0];
        return Arrays.stream(best).filter(w -> w >= 0 && !saidIn(heard, w) && !contains(kindTold, w)
                && Character.isLetter(language.vocabulary.word(w).charAt(0))
                && (memory.weight(w) > 0f || tones != null && !tones.common(w))).distinct().toArray();
    }

    /** Words that tell of something again, anywhere in a line ("a tree fell on it."). */
    private static final Set<String> AGAIN = Set.of("it", "he", "she", "they", "him", "her", "them", "his", "its", "their");

    /**
     * Only the part of what was told that answers the question: what happens if, the rule's
     * outcome ("the field floods ."); why, what made it so ("because it rained all night .", from
     * a rule and what was told of it, or from "because" in the line). Null if the whole is the answer.
     */
    private int[] toTheQuestion(int[] heard, int[] best) {
        if (heard.length == 0 || heard[0] < 0) {
            return null;
        }
        String first = language.vocabulary.word(heard[0]);
        int stop = language.vocabulary.find("."), comma = language.vocabulary.find(",");
        List<int[]> sentences = new ArrayList<>();
        int from = 0;
        for (int i = 0; i < best.length; i++) {
            if (best[i] == stop || i == best.length - 1) {
                sentences.add(Arrays.copyOfRange(best, from, i + 1));
                from = i + 1;
            }
        }
        if (first.equals("why")) {
            int[] fact = null;
            boolean ruled = false;
            for (int[] sentence : sentences) {
                ruled |= rule(sentence);
                fact = rule(sentence) ? fact : sentence;
            }
            if (ruled && fact != null && sentences.size() > 1) {
                return join(said("because"), fact);
            }
            int because = indexOf(best, language.vocabulary.find("because"), 1);
            return sentences.size() == 1 && because > 0 && because < best.length - 2
                    ? Arrays.copyOfRange(best, because, best.length) : null;
        }
        if (first.equals("what") && (contains(heard, language.vocabulary.find("if")) || contains(heard, language.vocabulary.find("when")))
                && sentences.size() == 1 && rule(best) && best[0] >= 0) {
            String opens = language.vocabulary.word(best[0]);
            int at = indexOf(best, comma, 1);
            if ((opens.equals("if") || opens.equals("when")) && at > 0 && at < best.length - 2) {
                // "if it rains , the field floods ."
                return Arrays.copyOfRange(best, at + 1, best.length);
            }
            int cut = Math.max(indexOf(best, language.vocabulary.find("if"), 1), indexOf(best, language.vocabulary.find("when"), 1));
            if (cut > 1) {
                // "the field floods if it rains ."
                return join(Arrays.copyOfRange(best, 0, cut), said("."));
            }
        }
        return null;
    }

    /** Whether a line says every telling word of a question, or one like it. */
    private boolean coversAll(int[] heard, int[] line, ExchangeMemory memory, ToneLinks tones) {
        if (rule(line) || sameOrder(heard, line) == 0) {
            // A rule alone ("if it rains, the field floods.") needs what makes it true told too,
            // and a comparison has to say how the two asked about compare.
            return false;
        }
        TopicLinks topics = TopicLinks.shared();
        for (int i = 1; i < heard.length; i++) {
            int q = heard[i];
            if (q < 0 || toldWeight(q, memory, tones) < TOLD_TELLING || saidIn(line, q)) {
                continue;
            }
            boolean like = false;
            for (int w : line) {
                like |= w >= 0 && topics != null && topics.alike(q, w) >= TOLD_ALIKE;
            }
            if (!like) {
                return false;
            }
        }
        return true;
    }

    /** The most told lines put together to answer one question. */
    static final int CHAIN = 4;

    /**
     * Lines told lately that answer the question together: each tells of something the one
     * before it told of too (not a word of the question: the thing that links them, "sam" in
     * "tom is older than sam ." "sam is older than ben ."), the first says something the question
     * asks, and all together say all it asks. The fewest such lines, in that order; or null.
     */
    private List<int[]> linesTogether(int[] heard, int[][] names, ExchangeMemory memory, ToneLinks tones) {
        int ask = language.vocabulary.find("?");
        List<int[]> lines = new ArrayList<>(), seen = new ArrayList<>();
        for (int k = Math.min(TOLD_LINES, heardLinesNext); k >= 2; k--) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            if (line.length > 0 && line.length <= TOLD_LONGEST && line[line.length - 1] != ask) {
                lines.add(line);
                seen.add(withWhatTheyName(line, names));
            }
        }
        for (int length = 2; length <= CHAIN; length++) {
            for (int first = 0; first < lines.size(); first++) {
                if (!saysSome(heard, seen.get(first), memory, tones)) {
                    continue;
                }
                List<Integer> path = new ArrayList<>(List.of(first));
                List<int[]> found = extend(path, length, heard, lines, seen, memory, tones);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private List<int[]> extend(List<Integer> path, int length, int[] heard, List<int[]> lines, List<int[]> seen,
            ExchangeMemory memory, ToneLinks tones) {
        if (path.size() == length) {
            int[] all = new int[0];
            for (int i : path) {
                int[] longer = Arrays.copyOf(all, all.length + seen.get(i).length);
                System.arraycopy(seen.get(i), 0, longer, all.length, seen.get(i).length);
                all = longer;
            }
            if (!coversAll(heard, all, memory, tones)) {
                return null;
            }
            List<int[]> chain = new ArrayList<>();
            for (int i : path) {
                chain.add(lines.get(i));
            }
            return chain;
        }
        int last = path.get(path.size() - 1);
        for (int next = 0; next < lines.size(); next++) {
            if (path.contains(next) || !linked(seen.get(last), seen.get(next), heard, memory, tones)) {
                continue;
            }
            path.add(next);
            List<int[]> found = extend(path, length, heard, lines, seen, memory, tones);
            path.remove(path.size() - 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Whether two lines share a telling word or name that the question doesn't say. */
    private boolean linked(int[] x, int[] y, int[] heard, ExchangeMemory memory, ToneLinks tones) {
        for (int w : x) {
            if (w >= 0 && thingWord(w) && saidIn(y, w) && !saidIn(heard, w)) {
                return true;
            }
        }
        return false;
    }

    private static final Set<String> NO_THING = Set.of("it", "and", "to", "at", "with", "for", "there", "here", "this",
            "that", "so", "but", "or", "very", "too", "now", "today", "then", "also", "be", "been", "i", "you", "he",
            "she", "we", "they", "me", "him", "them", "us", "if", "when");

    /** Whether a word can name a thing that links two lines ("well" can, "the" and "is" can't). */
    private boolean thingWord(int w) {
        String word = language.vocabulary.word(w);
        return Character.isLetter(word.charAt(0)) && !NO_THING.contains(word) && !BEFORE_A_THING.contains(word)
                && !ONE_WAY.contains(word) && !NOT.contains(word) && !BETWEEN.contains(word);
    }

    /** Whether a line says at least one telling word of the question. */
    private boolean saysSome(int[] heard, int[] line, ExchangeMemory memory, ToneLinks tones) {
        for (int q : heard) {
            if (q >= 0 && toldWeight(q, memory, tones) >= TOLD_TELLING / 2 && saidIn(line, q)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The answer to "who is the oldest?" from comparisons told lately ("tom is older than sam .",
     * "sam is older than ben ."): the one that is more than another and less than none, said as
     * "tom is the oldest ."; null if it is no such question or what was told doesn't settle it.
     */
    private int[] mostOf(int[] heard) {
        int the = language.vocabulary.find("the");
        int at = -1;
        for (int i = 1; i < heard.length; i++) {
            String w = heard[i] < 0 ? "" : language.vocabulary.word(heard[i]);
            if (heard[i - 1] == the && w.endsWith("est") && w.length() > 4) {
                at = i;
            }
        }
        if (at < 0) {
            return null;
        }
        String most = language.vocabulary.word(heard[at]);
        String moreWord = most.substring(0, most.length() - 3) + "er";
        phrases.clear();
        List<int[]> more = comparisons(moreWord);
        if (more.isEmpty() && OPPOSITE.containsKey(moreWord)) {
            // "who is the youngest?" after "tom is older than sam .": the one older than none.
            for (int[] m : comparisons(OPPOSITE.get(moreWord))) {
                more.add(new int[] {m[1], m[0]});
            }
        }
        int top = -1;
        for (int[] m : more) {
            boolean less = false;
            for (int[] n : more) {
                less |= sameThing(n[1], m[0]);
            }
            if (!less) {
                if (top >= 0 && !sameThing(top, m[0])) {
                    return null;
                }
                top = m[0];
            }
        }
        if (top < 0) {
            return null;
        }
        int[] named = phrases.getOrDefault(top, new int[] {top});
        int[] said = Arrays.copyOf(named, named.length + 4);
        said[named.length] = language.vocabulary.find("is");
        said[named.length + 1] = the;
        said[named.length + 2] = heard[at];
        said[named.length + 3] = language.vocabulary.find(".");
        return said;
    }

    private static final List<String> NUMBERS = List.of("zero", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
            "eighteen", "nineteen", "twenty");
    /** Doings that make fewer of something, and ones that make more, by their plain forms. */
    private static final class Doings {
        static final Set<String> FEWER = stems("eat", "lose", "sell", "give", "kill", "break", "use", "spend",
            "drop", "steal", "take", "burn");
        static final Set<String> MORE = stems("buy", "get", "find", "catch", "pick", "add", "grow", "make",
            "receive", "win", "more", "another", "bake", "collect");

        private static Set<String> stems(String... words) {
            return java.util.Arrays.stream(words).map(Brain::stem).collect(java.util.stream.Collectors.toSet());
        }
    }

    private static final List<String> DAYS = List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");

    private static final List<String> MONTHS = List.of("january", "february", "march", "april", "may", "june", "july",
            "august", "september", "october", "november", "december");

    /**
     * A sum worked out: "what is two plus three?" "two plus three is five ."; also minus, times
     * and divided by. Null if it is no such question, or the answer is no whole number.
     */
    private int[] worked(int[] heard) {
        if (heard.length < 4 || heard[0] < 0 || !Set.of("what", "what's", "how", "whats").contains(language.vocabulary.word(heard[0]))) {
            return null;
        }
        for (int i = 1; i + 1 < heard.length; i++) {
            if (heard[i - 1] < 0 || heard[i] < 0 || heard[i + 1] < 0) {
                continue;
            }
            int a = number(language.vocabulary.word(heard[i - 1]));
            String op = language.vocabulary.word(heard[i]);
            int at = i + 1;
            if ((op.equals("divided") || op.equals("take")) && at + 1 < heard.length && heard[at] >= 0
                    && Set.of("by", "away").contains(language.vocabulary.word(heard[at]))) {
                at++;
            }
            int b = heard[at] < 0 ? -1 : number(language.vocabulary.word(heard[at]));
            if (a < 0 || b < 0) {
                continue;
            }
            int c = switch (op) {
                case "plus", "+", "and" -> a + b;
                case "minus", "-", "take" -> a - b;
                case "times", "x", "*" -> a * b;
                case "divided", "/" -> b != 0 && a % b == 0 ? a / b : -1;
                default -> -1;
            };
            if (c < 0 || op.equals("and") && !contains(heard, language.vocabulary.find("make"))
                    && !contains(heard, language.vocabulary.find("makes"))) {
                continue;
            }
            int[] said = join(Arrays.copyOfRange(heard, i - 1, at + 1), new int[] {language.vocabulary.find("is"), numberWord(c),
                language.vocabulary.find(".")});
            return said[said.length - 2] < 0 ? null : said;
        }
        return null;
    }

    /** The told line that said what kind of thing was asked of ("rex is a dog ."), while answering. */
    private int[] kindLine;

    private static final Set<String> BEFORE_A_NAME = Set.of("the", "a", "an", "my", "your", "his", "her", "their", "our");

    /**
     * A question about something told to be of a kind ("rex is a dog .", "the thing in the cave is
     * a zombie ."), asked of the kind instead: "how many legs does rex have?" as "how many legs
     * does a dog have?", "does it burn in the sun?" as "does a zombie burn in the sun?". What is
     * known of the kind then answers. Null if no told line says what kind it is.
     */
    private int[] ofItsKind(int[] heard) {
        Vocabulary v = language.vocabulary;
        int ask = v.find("?"), is = v.find("is");
        if (heard[heard.length - 1] != ask) {
            return null;
        }
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            int at = line == null ? -1 : indexOf(line, is, 1);
            if (at < 0 || at + 3 != line.length - 1 || line[line.length - 1] == ask || line[at + 1] < 0
                    || !Set.of("a", "an").contains(v.word(line[at + 1])) || line[at + 2] < 0 || !thingWord(line[at + 2])) {
                continue;
            }
            int kind = line[at + 2];
            if (contains(heard, kind)) {
                return null;
            }
            // Where the question names it: its words ("rex", "my pet"), or "it", "he", "she".
            List<Integer> names = new ArrayList<>();
            for (int i = 0; i < at; i++) {
                if (line[i] >= 0 && !BEFORE_A_NAME.contains(v.word(line[i]))) {
                    names.add(line[i]);
                }
            }
            int from = -1, to = -1;
            for (int i = 0; i < heard.length - 1 && !names.isEmpty(); i++) {
                if (heard[i] == names.get(0) && i + names.size() <= heard.length) {
                    boolean all = true;
                    for (int j = 0; j < names.size() && all; j++) {
                        all = heard[i + j] == names.get(j);
                    }
                    if (all) {
                        from = i;
                        to = i + names.size();
                        while (from > 0 && heard[from - 1] >= 0 && BEFORE_A_NAME.contains(v.word(heard[from - 1]))) {
                            from--;
                        }
                    }
                }
            }
            if (from < 0) {
                for (int i = 1; i < heard.length - 1 && from < 0; i++) {
                    if (heard[i] >= 0 && Set.of("it", "he", "she").contains(v.word(heard[i]))) {
                        from = i;
                        to = i + 1;
                    }
                }
            }
            if (from < 0) {
                continue;
            }
            kindLine = line;
            return join(Arrays.copyOfRange(heard, 0, from), new int[] {line[at + 1], kind}, Arrays.copyOfRange(heard, to, heard.length));
        }
        return null;
    }

    private static final Set<String> OF_THE_MOMENT = Set.of("it", "it's", "today", "now", "tonight", "you", "your",
        "you're", "we", "our", "we're", "i", "my", "me", "us", "i'm", "this", "that", "here", "there", "any");

    private static final String KNOWLEDGE = "/reallyalive/knowledge.txt";
    private static List<String[]> knownLines;

    /**
     * What every villager knows from growing up, shipped as plain lines: everyday facts ("a
     * spider has eight legs .", "a farmer grows crops .") and facts of the world it lives in
     * ("zombies burn in the sun ."). Recalled like a line told long ago. A line comes to mind
     * only once the villager knows all its words, so one it never heard ("enderman") waits until
     * someone says it.
     */
    static List<int[]> knownToAll(Vocabulary v) {
        List<int[]> lines = new ArrayList<>();
        for (String[] line : knownLines()) {
            int[] words = new int[line.length];
            boolean all = true;
            for (int i = 0; i < line.length && all; i++) {
                words[i] = v.find(line[i]);
                all = words[i] >= 0;
            }
            if (all) {
                lines.add(words);
            }
        }
        return lines;
    }

    private static synchronized List<String[]> knownLines() {
        if (knownLines == null) {
            List<String[]> lines = new ArrayList<>();
            try (java.io.InputStream in = Brain.class.getResourceAsStream(KNOWLEDGE)) {
                if (in != null) {
                    for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            lines.add(line.split(" "));
                        }
                    }
                }
            } catch (java.io.IOException e) {
                lines.clear();
            }
            knownLines = List.copyOf(lines);
        }
        return knownLines;
    }

    private static final Set<String> MORE_WORDS = Set.of("more", "most", "bigger", "biggest", "greater", "larger",
        "older", "oldest", "deeper", "deepest", "higher", "highest", "longer", "longest", "taller", "tallest", "heavier",
        "heaviest", "later", "last", "after");
    private static final Set<String> LESS_WORDS = Set.of("fewer", "fewest", "less", "least", "smaller", "smallest",
        "younger", "youngest", "shallower", "shorter", "shortest", "lower", "lowest", "lighter", "earlier", "first", "before");
    /** Order words, which ask of the order things were told in, not of amounts told. */
    private static final Set<String> ORDER_WORDS = Set.of("later", "last", "after", "earlier", "first", "before");

    /** Where a word stands among numbers, days or months, as {kind, place}; null if it stands in none. */
    private static int[] placeAmong(String w) {
        return number(w) >= 0 ? new int[] {0, number(w)} : DAYS.contains(w) ? new int[] {1, DAYS.indexOf(w)}
            : MONTHS.contains(w) ? new int[] {2, MONTHS.indexOf(w)} : null;
    }

    /**
     * Amounts compared: of two numbers, days or months asked of ("which comes first, friday or
     * sunday?" "friday comes first ."; "is tuesday before monday?" "no , tuesday comes after monday
     * ."), or of what was told just before ("who has more apples?" after "tom has three apples ."
     * "anna has five apples .": "anna has five apples ."; "how many more coins does lou have?"
     * "lou has five more coins ."). Null if it is no such question.
     */
    private int[] compared(int[] heard) {
        Vocabulary v = language.vocabulary;
        int n = heard.length;
        String[] w = new String[n];
        for (int i = 0; i < n; i++) {
            w[i] = heard[i] < 0 ? "" : v.word(heard[i]);
        }
        int dir = 0, at = -1;
        for (int i = 1; i < n && dir == 0; i++) {
            dir = MORE_WORDS.contains(w[i]) ? 1 : LESS_WORDS.contains(w[i]) ? -1 : 0;
            at = i;
        }
        if (dir == 0) {
            return null;
        }
        String way = w[at];
        // Two named: "friday or sunday", "is tuesday before monday?".
        int or = Arrays.asList(w).indexOf("or");
        int a = -1, b = -1;
        if (or > 1 && or + 1 < n - 1) {
            a = or - 1;
            b = or + 1 + (w[or + 1].equals("the") && or + 2 < n - 1 ? 1 : 0);
        } else if (yesNoAsked(heard) && at == 2 && n == 5) {
            a = 1;
            b = 3;
        } else if (yesNoAsked(heard) && at == 2 && n == 6 && w[3].equals("than")) {
            // "is four less than two?"
            a = 1;
            b = 4;
        }
        int[] pa = a < 0 ? null : placeAmong(w[a]), pb = b < 0 ? null : placeAmong(w[b]);
        if (pa != null && pb != null && pa[0] == pb[0] && pa[1] != pb[1]) {
            boolean aWins = dir > 0 ? pa[1] > pb[1] : pa[1] < pb[1];
            String kindWay = pa[0] == 0 ? (ORDER_WORDS.contains(way) ? (dir > 0 ? "more" : "less") : way)
                : dir > 0 ? (way.equals("after") || way.equals("later") ? way : "after") : (way.equals("before") || way.equals("earlier") ? way : "before");
            String verb = pa[0] == 0 ? "is" : "comes";
            String than = pa[0] == 0 ? " than " : " ";
            if (yesNoAsked(heard)) {
                String other = pa[0] == 0 ? (dir > 0 ? "less" : "more") : (dir > 0 ? "before" : "after");
                return said((aWins ? "yes , " : "no , ") + w[a] + " " + verb + " " + (aWins ? kindWay : other) + than + w[b] + " .");
            }
            String win = aWins ? w[a] : w[b], lose = aWins ? w[b] : w[a];
            if (pa[0] != 0 && (way.equals("first") || way.equals("last"))) {
                return said(win + " comes " + way + " .");
            }
            return said(win + " " + verb + " " + kindWay + than + lose + " .");
        }
        if (ORDER_WORDS.contains(way)) {
            return null;
        }
        // Amounts told: the lines just before with a count, of the thing asked of if it names one.
        List<int[]> lines = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            if (line == null || line.length == 0 || line[line.length - 1] == v.find("?")) {
                // Questions in between ("how many more sheep does oskar have?") don't end it.
                continue;
            }
            for (int i = 1; i + 1 < line.length; i++) {
                int c = line[i] < 0 ? -1 : number(v.word(line[i]));
                if (c >= 0 && line[i + 1] >= 0 && Character.isLetter(v.word(line[i + 1]).charAt(0))) {
                    lines.add(0, line);
                    counts.add(0, c);
                    break;
                }
            }
        }
        // Of the thing asked of ("apples"), if the question names a thing they count.
        List<int[]> of = new ArrayList<>();
        List<Integer> ofCounts = new ArrayList<>();
        for (int j = 0; j < lines.size(); j++) {
            boolean says = false;
            for (int i = 1; i < n - 1; i++) {
                says |= heard[i] >= 0 && thingWord(heard[i]) && !MORE_WORDS.contains(w[i]) && !LESS_WORDS.contains(w[i])
                        && !ASKING_WORDS.contains(w[i]) && !Set.of("has", "have", "does", "do", "is", "are", "than", "many").contains(w[i])
                        && saidIn(lines.get(j), heard[i]);
            }
            if (says) {
                of.add(lines.get(j));
                ofCounts.add(counts.get(j));
            }
        }
        if (of.size() >= 2) {
            lines = of;
            counts = ofCounts;
        }
        if (lines.size() < 2) {
            return null;
        }
        if (w[0].equals("how") && n > 3 && w[1].equals("many") && (w[2].equals("more") || w[2].equals("fewer"))) {
            // "how many more coins does lou have?": lou's count less the other's.
            int whose = -1;
            for (int j = 0; j < lines.size() && whose < 0; j++) {
                // Whose count it is: the one the line tells of first ("lou has 9 coins .").
                int first = 0;
                while (first < lines.get(j).length && Set.of("the", "a", "an", "my").contains(v.word(lines.get(j)[first]))) {
                    first++;
                }
                whose = first < lines.get(j).length && contains(Arrays.copyOfRange(heard, 3, n), lines.get(j)[first]) ? j : -1;
            }
            if (whose < 0 || lines.size() != 2) {
                return null;
            }
            int diff = Math.abs(counts.get(whose) - counts.get(1 - whose));
            int[] line = lines.get(whose);
            int c = 0;
            while (c < line.length && (line[c] < 0 || number(v.word(line[c])) < 0)) {
                c++;
            }
            return join(Arrays.copyOfRange(line, 0, c), new int[] {numberWord(diff), v.find(w[2])}, Arrays.copyOfRange(line, c + 1, line.length));
        }
        if (yesNoAsked(heard)) {
            // "does tom have more apples than anna?"
            int than = Arrays.asList(w).indexOf("than");
            int mine = -1, theirs = -1;
            for (int j = 0; j < lines.size(); j++) {
                if (than > 1 && lines.get(j)[0] == heard[1]) {
                    mine = j;
                }
                if (than > 1 && than + 1 < n && lines.get(j)[0] == heard[than + 1]) {
                    theirs = j;
                }
            }
            if (mine < 0 || theirs < 0) {
                return null;
            }
            boolean so = dir > 0 ? counts.get(mine) > counts.get(theirs) : counts.get(mine) < counts.get(theirs);
            return withWord(v.find(so ? "yes" : "no"), lines.get(so ? mine : theirs));
        }
        int best = 0;
        boolean same = true;
        for (int j = 1; j < lines.size(); j++) {
            same &= counts.get(j).equals(counts.get(0));
            best = dir > 0 ? (counts.get(j) > counts.get(best) ? j : best) : (counts.get(j) < counts.get(best) ? j : best);
        }
        return same ? said("they have the same .") : lines.get(best);
    }

    private int numberWord(int n) {
        return language.vocabulary.find(n < NUMBERS.size() ? NUMBERS.get(n) : Integer.toString(n));
    }

    /**
     * What comes before or after a day, a month or a number: "what day comes before friday?"
     * "thursday comes before friday ."; null if it is no such question.
     */
    private int[] nextInOrder(int[] heard) {
        if (heard.length < 4 || heard[0] < 0 || !Set.of("what", "which", "what's").contains(language.vocabulary.word(heard[0]))
                || !contains(heard, language.vocabulary.find("comes")) && !contains(heard, language.vocabulary.find("come"))) {
            return null;
        }
        for (int i = 1; i + 1 < heard.length; i++) {
            if (heard[i] < 0 || heard[i + 1] < 0) {
                continue;
            }
            String way = language.vocabulary.word(heard[i]);
            String of = language.vocabulary.word(heard[i + 1]);
            int step = way.equals("after") ? 1 : way.equals("before") ? -1 : 0;
            if (step == 0) {
                continue;
            }
            String said = null;
            if (DAYS.contains(of)) {
                said = DAYS.get(Math.floorMod(DAYS.indexOf(of) + step, 7));
            } else if (MONTHS.contains(of)) {
                said = MONTHS.get(Math.floorMod(MONTHS.indexOf(of) + step, 12));
            } else if (number(of) >= 0 && number(of) + step >= 0) {
                said = language.vocabulary.word(numberWord(number(of) + step));
            }
            if (said != null && language.vocabulary.find(said) >= 0) {
                return new int[] {language.vocabulary.find(said), language.vocabulary.find("comes"), heard[i], heard[i + 1],
                    language.vocabulary.find(".")};
            }
        }
        return null;
    }

    /**
     * The answer to a question about what it holds or carries, from what the game told it: "what
     * are you holding?" "i'm holding a hoe .", "what do you have?" "i have three bread and a hoe .",
     * "do you have any bread?" "yes , i have three bread ." or "no , i don't have any bread .". Null
     * if it is no such question or it was never told.
     */
    private int[] possessions(int[] heard) {
        int n = heard.length;
        if (!toldCarried || n < 4 || heard[n - 1] != language.vocabulary.find("?")) {
            return null;
        }
        String[] w = new String[n];
        for (int i = 0; i < n; i++) {
            w[i] = heard[i] < 0 ? "" : language.vocabulary.word(heard[i]);
        }
        List<String> words = Arrays.asList(w);
        boolean you = words.contains("you") || words.contains("your");
        if (!you) {
            return null;
        }
        if (w[0].startsWith("what") && (words.contains("holding") || words.contains("hand"))) {
            return held == null ? said("i'm not holding anything .") : join(said("i'm holding"), article(held), held, said("."));
        }
        if (w[0].startsWith("what") && n <= 8 && (words.contains("carrying") || words.contains("inventory") || words.contains("pockets")
                || words.contains("bag") || words.contains("have") && words.contains("do"))) {
            if (carried.isEmpty()) {
                return said("i don't have anything .");
            }
            int[] list = said("i have");
            for (int i = 0; i < carried.size() && i < 4; i++) {
                Carried c = carried.get(i);
                int[] and = i == 0 ? new int[0] : said(i == Math.min(carried.size(), 4) - 1 ? "and" : ",");
                list = join(list, and, count(c), c.name());
            }
            return join(list, said("."));
        }
        int from = n >= 5 && w[0].equals("do") && w[1].equals("you") && w[2].equals("have") ? 3
                : n >= 5 && w[0].equals("have") && w[1].equals("you") && w[2].equals("got") ? 3
                : n >= 5 && w[0].equals("are") && w[1].equals("you") && w[2].equals("carrying") ? 3 : -1;
        if (from < 0) {
            return null;
        }
        while (from < n - 1 && Set.of("any", "a", "an", "some", "the").contains(w[from])) {
            from++;
        }
        int[] thing = Arrays.copyOfRange(heard, from, n - 1);
        if (thing.length == 0 || thing.length > 3 || Arrays.stream(thing).anyMatch(x -> x < 0)) {
            return null;
        }
        String asked = language.vocabulary.word(thing[thing.length - 1]);
        for (Carried c : carried) {
            if (sameKind(asked, c.name())) {
                return join(said("yes , i have"), count(c), c.name(), said("."));
            }
        }
        if (held != null && sameKind(asked, held)) {
            return join(said("yes , i'm holding"), article(held), held, said("."));
        }
        // Only what can be carried is said not to be ("do you have a minute?" is no such question).
        return known(ITEMS, asked) ? join(said("no , i don't have any"), thing, said(".")) : null;
    }

    /**
     * The answer to a question about the creatures around it, from what the game told it it sees:
     * "what do you see?" "i see two cows and a pig .", "how many cows are there?" "there are two
     * cows .", "are there any zombies around?" "no , i don't see any zombies .". It only says there
     * are none of a kind of creature. Null if it is no such question or it was never told.
     */
    private int[] aroundHere(int[] heard) {
        int n = heard.length;
        if (!toldAround || n < 3 || heard[n - 1] != language.vocabulary.find("?")) {
            return null;
        }
        String[] w = new String[n];
        for (int i = 0; i < n; i++) {
            w[i] = heard[i] < 0 ? "" : language.vocabulary.word(heard[i]);
        }
        List<String> words = Arrays.asList(w);
        boolean near = words.contains("see") || words.contains("around") || words.contains("nearby") || words.contains("here")
                || words.contains("there");
        if (!near) {
            return null;
        }
        if ((w[0].equals("what") || w[0].equals("who") || w[0].equals("what's") || w[0].equals("who's")) && n <= 6) {
            if (around.isEmpty()) {
                return said("i don't see anyone .");
            }
            int[] list = said("i see");
            for (int i = 0; i < around.size() && i < 4; i++) {
                int[] and = i == 0 ? new int[0] : said(i == Math.min(around.size(), 4) - 1 ? "and" : ",");
                list = join(list, and, count(around.get(i)), plural(around.get(i)));
            }
            return join(list, said("."));
        }
        int from;
        boolean howMany = n >= 5 && w[0].equals("how") && w[1].equals("many");
        if (howMany) {
            from = 2;
        } else if (n >= 5 && (w[0].equals("are") || w[0].equals("is")) && w[1].equals("there")) {
            from = 2;
        } else if (n >= 5 && (w[0].equals("do") || w[0].equals("can")) && w[1].equals("you") && w[2].equals("see")) {
            from = 3;
        } else {
            return null;
        }
        while (from < n - 1 && Set.of("any", "a", "an", "some", "the").contains(w[from])) {
            from++;
        }
        int to = from;
        while (to < n - 1 && !Set.of("are", "is", "do", "can", "around", "nearby", "here", "there", "near").contains(w[to])) {
            to++;
        }
        if (to == from || to - from > 2) {
            return null;
        }
        int[] kind = Arrays.copyOfRange(heard, from, to);
        String asked = w[to - 1];
        Carried found = null;
        for (Carried c : around) {
            if (sameKind(asked, c.name())) {
                found = c;
            }
        }
        if (found == null) {
            if (!known(CREATURES, asked)) {
                return null;
            }
            return howMany ? join(said("there are no"), kind, said(".")) : join(said("no , i don't see any"), kind, said("."));
        }
        if (howMany) {
            return join(said(found.count() == 1 ? "there is" : "there are"), count(found), plural(found), said("."));
        }
        return join(said("yes , i see"), count(found), plural(found), said("."));
    }

    /** The forms a word may be one of when it names many ("zombies" may be "zombie"). */
    private static List<String> singular(String w) {
        List<String> forms = new ArrayList<>(List.of(w, stem(w)));
        if (w.endsWith("ies") && w.length() > 4) {
            forms.add(w.substring(0, w.length() - 3) + "y");
        }
        if (w.endsWith("es")) {
            forms.add(w.substring(0, w.length() - 2));
        }
        if (w.endsWith("s")) {
            forms.add(w.substring(0, w.length() - 1));
        }
        return forms;
    }

    /** Whether an asked word names the same kind as a name's last word ("zombies", "zombie"). */
    private boolean sameKind(String asked, int[] name) {
        String last = language.vocabulary.word(name[name.length - 1]);
        return singular(asked).contains(last) || stem(asked).equals(stem(last));
    }

    private static boolean known(Set<String> kinds, String asked) {
        return singular(asked).stream().anyMatch(kinds::contains);
    }

    /** A name said for how many there are: "cow", "cows". */
    private int[] plural(Carried c) {
        int[] name = c.name().clone();
        if (c.count() > 1) {
            String last = language.vocabulary.word(name[name.length - 1]);
            String many = last.endsWith("s") || last.endsWith("sh") || last.endsWith("ch") ? last + "es"
                    : last.endsWith("y") && last.length() > 2 && "aeiou".indexOf(last.charAt(last.length() - 2)) < 0
                    ? last.substring(0, last.length() - 1) + "ies" : last.endsWith("sheep") || last.endsWith("fish") ? last : last + "s";
            name[name.length - 1] = language.vocabulary.id(many);
        }
        return name;
    }

    /**
     * The answer to "how do i make bread?" (or craft, cook, smelt) from the game's recipes: "to
     * make bread , you need three wheat .". Null if it is no such question or nothing is known to
     * be made so.
     */
    private int[] howToMake(int[] heard) {
        int n = heard.length;
        if (RECIPES.isEmpty() || n < 4 || heard[n - 1] != language.vocabulary.find("?")) {
            return null;
        }
        String[] w = new String[n];
        for (int i = 0; i < n; i++) {
            w[i] = heard[i] < 0 ? "" : language.vocabulary.word(heard[i]);
        }
        if (!w[0].equals("how") && !(w[0].equals("what") && Arrays.asList(w).contains("need"))) {
            return null;
        }
        int at = -1;
        for (int i = 1; i < n - 1 && at < 0; i++) {
            at = Set.of("make", "craft", "cook", "bake", "smelt", "build", "brew", "get").contains(w[i]) ? i : -1;
        }
        if (at < 0) {
            return null;
        }
        int from = at + 1;
        while (from < n - 1 && Set.of("a", "an", "the", "some", "any").contains(w[from])) {
            from++;
        }
        if (from >= n - 1 || n - 1 - from > 3) {
            return null;
        }
        List<String> thing = Arrays.asList(w).subList(from, n - 1);
        String recipe = null;
        for (String last : singular(thing.get(thing.size() - 1))) {
            List<String> name = new ArrayList<>(thing.subList(0, thing.size() - 1));
            name.add(last);
            recipe = recipe != null ? recipe : RECIPES.get(String.join(" ", name));
        }
        if (recipe == null) {
            return null;
        }
        // Said as it was asked: "to make a torch".
        String said = "to " + w[at] + " " + String.join(" ", Arrays.asList(w).subList(at + 1, n - 1)) + " , " + recipe + " .";
        return Arrays.stream(said.split(" ")).mapToInt(language.vocabulary::id).toArray();
    }

    /** The words of a short line it says, each found in its vocabulary. */
    private int[] said(String line) {
        return Arrays.stream(line.split(" ")).mapToInt(language.vocabulary::find).toArray();
    }

    private int[] article(int[] name) {
        return said("aeiou".indexOf(language.vocabulary.word(name[0]).charAt(0)) >= 0 ? "an" : "a");
    }

    private int[] count(Carried c) {
        return c.count() == 1 ? article(c.name()) : new int[] {numberWord(c.count())};
    }

    /** Family words: what each says the one is to the other, and whether a man or a woman. */
    private static final java.util.Map<String, String[]> KIN = kin("mother parent f", "father parent m",
            "parent parent -", "son child m", "daughter child f", "child child -", "brother sibling m", "sister sibling f",
            "wife spouse f", "husband spouse m", "uncle pibling m", "aunt pibling f", "nephew nibling m", "niece nibling f",
            "grandmother grandparent f", "grandfather grandparent m", "grandson grandchild m", "granddaughter grandchild f");
    private static final java.util.Map<String, String> KIN_TURNED = java.util.Map.of("parent", "child", "child", "parent",
            "sibling", "sibling", "spouse", "spouse", "pibling", "nibling", "nibling", "pibling", "grandparent", "grandchild",
            "grandchild", "grandparent");
    /** Who "my" names: the one talking. */
    private static final String SPEAKER = "#me";
    /** Who "your" names: this villager. */
    private static final String SELF = "#self";

    /** One of its own family, as the game tells it: "mother" and the words of her name. */
    record Kin(String word, int[] name) {}

    /** Its own family, told again by the game now and then; not saved. */
    final List<Kin> kin = new ArrayList<>();

    /** Something it carries: the words of its name ("wheat seeds") and how many. */
    record Carried(int[] name, int count) {}

    /** What it carries, and what it holds in its hand (null for nothing), as the game tells it; not saved. */
    final List<Carried> carried = new ArrayList<>();
    int[] held;
    /** Whether the game has told it what it carries at all. */
    boolean toldCarried;
    /** The last words of the names of things that can be carried at all ("bread", "hoe"), from the game. */
    static final Set<String> ITEMS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** How each thing is made, by its name ("bread": "you need three wheat"), from the game's recipes. */
    static final java.util.Map<String, String> RECIPES = new java.util.concurrent.ConcurrentHashMap<>();
    /** The creatures it sees around it, by kind, and how many; as the game tells it, not saved. */
    final List<Carried> around = new ArrayList<>();
    boolean toldAround;
    /** The time of day and the weather as the game last told them ("night", "stormy"); null if not told. */
    String timeNow;
    String weatherNow;
    /** The last words of the names of the kinds of creature there are ("cow", "golem"), from the game. */
    static final Set<String> CREATURES = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static java.util.Map<String, String[]> kin(String... rows) {
        java.util.Map<String, String[]> m = new java.util.HashMap<>();
        for (String row : rows) {
            String[] w = row.split(" ");
            m.put(w[0], new String[] {w[1], w[2]});
        }
        return m;
    }

    /** Someone named in a line from a place on: "the baker's" is the baker, "my" the speaker. */
    private String kinName(int[] line, int from, int to) {
        StringBuilder name = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (line[i] < 0) {
                return null;
            }
            String w = language.vocabulary.word(line[i]);
            if (w.equals("my") || w.equals("your")) {
                return to - from == 1 ? (w.equals("my") ? SPEAKER : SELF) : null;
            }
            if (w.endsWith("'s")) {
                w = w.substring(0, w.length() - 2);
            } else if (w.endsWith("'")) {
                w = w.substring(0, w.length() - 1);
            }
            if (!w.equals("the") && !w.equals("a")) {
                name.append(name.length() > 0 ? " " : "").append(w);
            }
        }
        return name.length() == 0 ? null : name.toString();
    }

    /**
     * The answer to "who is anna's son?" or "is ben anna's son?" from the family told lately or
     * long ago ("anna is ben's mother ."): each told tie is known both ways round, a parent's
     * parent is a grandparent, and a sibling has the same parents. Null if it is no such question
     * or nothing told answers it.
     */
    private int[] family(int[] heard) {
        int n = heard.length;
        if (n < 4 || heard[0] < 0 || heard[n - 2] < 0 || !KIN.containsKey(language.vocabulary.word(heard[n - 2]))) {
            return null;
        }
        String first = language.vocabulary.word(heard[0]);
        String rel = language.vocabulary.word(heard[n - 2]);
        String kind = KIN.get(rel)[0], gender = KIN.get(rel)[1];
        // Ties told: {one, kind, other}, "anna parent ben".
        List<String[]> ties = new ArrayList<>();
        java.util.Map<String, String> genders = new java.util.HashMap<>();
        java.util.Map<String, int[]> said = new java.util.HashMap<>();
        List<int[]> lines = new ArrayList<>();
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            lines.add(heardLines[(heardLinesNext - k) % heardLines.length]);
        }
        lines.addAll(talk.toldLines());
        int is = language.vocabulary.find("is");
        for (int[] line : lines) {
            int at = indexOf(line, is, 0);
            int end = line.length - 1;
            while (end > 0 && line[end] >= 0 && !Character.isLetter(language.vocabulary.word(line[end]).charAt(0))) {
                end--;
            }
            if (at < 1 || end < at + 2 || line[end] < 0 || !KIN.containsKey(language.vocabulary.word(line[end]))
                    || line[line.length - 1] == language.vocabulary.find("?")) {
                // Not a telling of a tie ("who is your mother?" asks one).
                continue;
            }
            String one = kinName(line, 0, at), other = kinName(line, at + 1, end);
            if (one == null || other == null || one.equals(other)) {
                continue;
            }
            String[] k = KIN.get(language.vocabulary.word(line[end]));
            ties.add(new String[] {one, k[0], other});
            ties.add(new String[] {other, KIN_TURNED.get(k[0]), one});
            if (!k[1].equals("-")) {
                genders.put(one, k[1]);
            }
            said.putIfAbsent(one, Arrays.copyOfRange(line, 0, at));
            int[] whose = Arrays.copyOfRange(line, at + 1, end);
            String last = language.vocabulary.word(whose[whose.length - 1]);
            if (!other.equals(SPEAKER) && !other.equals(SELF) && last.indexOf('\'') > 0) {
                // "ben's" is said "ben".
                whose[whose.length - 1] = language.vocabulary.find(last.substring(0, last.lastIndexOf('\'')));
                if (whose[whose.length - 1] >= 0) {
                    said.putIfAbsent(other, whose);
                }
            }
        }
        for (Kin k : kin) {
            // Its own family, as the game told it.
            String[] word = KIN.get(k.word());
            String one = kinName(k.name(), 0, k.name().length);
            if (word == null || one == null) {
                continue;
            }
            ties.add(new String[] {one, word[0], SELF});
            ties.add(new String[] {SELF, KIN_TURNED.get(word[0]), one});
            if (!word[1].equals("-")) {
                genders.put(one, word[1]);
            }
            said.putIfAbsent(one, k.name());
        }
        // What follows from them: a parent's parent, and the parents of a sibling.
        for (int round = 0; round < 2; round++) {
            List<String[]> more = new ArrayList<>();
            for (String[] a : ties) {
                for (String[] b : ties) {
                    if (!a[2].equals(b[0]) || a[0].equals(b[2])) {
                        continue;
                    }
                    String made = a[1].equals("parent") && b[1].equals("parent") ? "grandparent"
                            : a[1].equals("parent") && b[1].equals("sibling") ? "parent"
                            : a[1].equals("sibling") && b[1].equals("child") ? "child" : null;
                    if (made != null) {
                        more.add(new String[] {a[0], made, b[2]});
                        more.add(new String[] {b[2], KIN_TURNED.get(made), a[0]});
                    }
                }
            }
            ties.addAll(more);
        }
        int name = n - 2;
        if (first.equals("who") && n >= 5 && heard[1] == is) {
            // "who is anna's son?"
            String of = kinName(heard, 2, name);
            if (of == null) {
                return null;
            }
            for (String[] t : ties) {
                if (t[1].equals(kind) && t[2].equals(of) && (gender.equals("-") || !genders.containsKey(t[0])
                        || genders.get(t[0]).equals(gender))) {
                    int[] who = t[0].equals(SPEAKER) ? new int[] {language.vocabulary.find("you"), language.vocabulary.find("are")}
                            : t[0].equals(SELF) ? new int[] {language.vocabulary.find("i"), language.vocabulary.find("am")}
                            : said.containsKey(t[0]) ? join(said.get(t[0]), new int[] {is}) : null;
                    if (who == null) {
                        continue;
                    }
                    int[] whose = of.equals(SPEAKER) ? new int[] {language.vocabulary.find("your")}
                            : of.equals(SELF) ? new int[] {language.vocabulary.find("my")} : Arrays.copyOfRange(heard, 2, name);
                    int[] answer = join(who, whose, new int[] {heard[name], language.vocabulary.find(".")});
                    return Arrays.stream(answer).anyMatch(w -> w < 0) ? null : answer;
                }
            }
        } else if (first.equals("is") || first.equals("isn't")) {
            // "is ben anna's son?": ben is the one before the possessive.
            for (int split = 2; split < name; split++) {
                String one = kinName(heard, 1, split), of = kinName(heard, split, name);
                String whose = language.vocabulary.word(heard[split]);
                if (one == null || of == null || !whose.equals("my") && !whose.equals("your")
                        && !language.vocabulary.word(heard[name - 1]).endsWith("'s") && !language.vocabulary.word(heard[name - 1]).endsWith("'")) {
                    continue;
                }
                for (String[] t : ties) {
                    if (t[0].equals(one) && t[1].equals(kind) && t[2].equals(of)) {
                        // "is rosa your mother?" "yes , rosa is my mother ."
                        int[] of2 = Arrays.copyOfRange(heard, split, name + 1);
                        of2[0] = whose.equals("my") ? language.vocabulary.find("your") : whose.equals("your") ? language.vocabulary.find("my") : of2[0];
                        return join(new int[] {language.vocabulary.find("yes"), language.vocabulary.find(",")},
                                Arrays.copyOfRange(heard, 1, split), new int[] {is}, of2, new int[] {language.vocabulary.find(".")});
                    }
                }
            }
        }
        return null;
    }

    /**
     * The answer to "what day is tomorrow?" (or yesterday, or today) from the day told lately
     * ("today is monday ."); null if it is no such question or no day was told.
     */
    private int[] whatDay(int[] heard) {
        Integer when = null;
        for (int w : heard) {
            when = dayWord(w) != null ? dayWord(w) : when;
        }
        if (when == null || !contains(heard, language.vocabulary.find("day"))) {
            return null;
        }
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            Integer told = null;
            int day = -1;
            for (int w : line) {
                told = dayWord(w) != null ? dayWord(w) : told;
                day = w >= 0 && DAYS.contains(language.vocabulary.word(w)) ? DAYS.indexOf(language.vocabulary.word(w)) : day;
            }
            if (day >= 0 && told != null) {
                int asked = Math.floorMod(day + when - told, 7);
                String[] said = {when == 1 ? "tomorrow" : when == -1 ? "yesterday" : "today", when == -1 ? "was" : "is",
                    DAYS.get(asked), "."};
                return Arrays.stream(said).mapToInt(language.vocabulary::find).toArray();
            }
        }
        return null;
    }

    /**
     * A question without the words that only call someone ("hey boss , what's up ?" asks
     * "what's up ?"; "what's up , boss ?" too), so being called the same as in a told line
     * doesn't bring that line back.
     */
    private int[] withoutAddress(int[] heard) {
        int comma = language.vocabulary.find(",");
        List<Integer> kept = new ArrayList<>();
        for (int i = 0; i < heard.length; i++) {
            String before = i > 0 && heard[i - 1] >= 0 ? language.vocabulary.word(heard[i - 1]) : "";
            boolean greeting = Set.of("hey", "hi", "hello", "oh", "excuse").contains(i >= 0 && heard[i] >= 0 ? language.vocabulary.word(heard[i]) : "");
            boolean called = i == 0 && heard.length > 1 && heard[1] == comma
                    || i > 0 && Set.of("hey", "hi", "hello").contains(before)
                    || i > 0 && i == heard.length - 2 && heard[i - 1] == comma;
            if (!greeting && !called && !(heard[i] == comma && (i == 1 || kept.isEmpty()))) {
                kept.add(heard[i]);
            }
        }
        return kept.stream().mapToInt(Integer::intValue).toArray();
    }

    /** How many days from today a word says (tomorrow 1, yesterday -1, today 0), or null. */
    private Integer dayWord(int w) {
        String word = w < 0 ? "" : language.vocabulary.word(w);
        return word.equals("tomorrow") ? Integer.valueOf(1) : word.equals("yesterday") ? Integer.valueOf(-1)
                : word.equals("today") ? Integer.valueOf(0) : null;
    }

    /** Words after a bare count ("he lost one on the way"): no thing is named. */
    private static final Set<String> AFTER_A_COUNT = Set.of("more", "of", "on", "in", "at", "to", "from", "for", "today",
            "yesterday", "again", "too", "away", "back", "with", "by", "were", "was", "are", "is");

    private static int number(String w) {
        int n = NUMBERS.indexOf(w);
        if (n < 0 && !w.isEmpty() && w.chars().allMatch(Character::isDigit) && w.length() < 5) {
            n = Integer.parseInt(w);
        }
        return n;
    }

    /**
     * The answer to "how many apples do i have?" from what was told lately, in the order told:
     * the last count of them ("i have three apples"), less what was eaten, lost or sold since and
     * more what was bought or found ("and i eat one ."); null if it is no such question or
     * nothing told counts them.
     */
    private int[] howMany(int[] heard) {
        int at = -1;
        for (int i = 0; i + 2 < heard.length; i++) {
            if (heard[i] >= 0 && heard[i + 1] >= 0 && language.vocabulary.word(heard[i]).equals("how")
                    && language.vocabulary.word(heard[i + 1]).equals("many")) {
                at = i + 2;
            }
        }
        if (at < 0 || heard[at] < 0) {
            return null;
        }
        int thing = heard[at];
        Integer count = null;
        for (int k = Math.min(TOLD_LINES, heardLinesNext); k >= 2; k--) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            // Each part of the line on its own: "i have three apples", "and i eat one".
            int doing = 0;
            for (int i = 0; i < line.length; i++) {
                String w = line[i] < 0 ? "" : language.vocabulary.word(line[i]);
                String plain = stem(w);
                if (w.equals(",") || w.equals(".") || w.equals("and") || w.equals("then") || w.equals("but")) {
                    doing = 0;
                    continue;
                }
                doing = Doings.FEWER.contains(plain) ? -1 : Doings.MORE.contains(plain) ? 1 : doing;
                int n = number(w);
                if (n < 0) {
                    continue;
                }
                boolean ofIt = i + 1 < line.length && line[i + 1] >= 0 && sameThing(line[i + 1], thing)
                        || i + 2 < line.length && line[i + 2] >= 0 && sameThing(line[i + 2], thing);
                String next = i + 1 < line.length && line[i + 1] >= 0 ? language.vocabulary.word(line[i + 1]) : ".";
                // "one on the way home", "two of them", "two more".
                boolean bare = !Character.isLetter(next.charAt(0)) || AFTER_A_COUNT.contains(next);
                if (next.equals("more")) {
                    doing = 1;
                }
                for (int j = i + 1; doing == 0 && bare && j < line.length && line[j] >= 0; j++) {
                    // "one was sold": the doing comes after the count.
                    String later = language.vocabulary.word(line[j]);
                    if (later.equals(",") || later.equals(".") || later.equals("and")) {
                        break;
                    }
                    doing = Doings.FEWER.contains(stem(later)) ? -1 : Doings.MORE.contains(stem(later)) ? 1 : 0;
                }
                if (doing == 0 && ofIt || count == null && doing > 0 && ofIt) {
                    // "i have three apples", or "i bought four eggs" with none before.
                    count = n;
                } else if (count != null && doing != 0 && (ofIt || bare)) {
                    count = Math.max(0, count + doing * n);
                }
            }
        }
        if (count == null || count > 20) {
            return null;
        }
        List<Integer> said = new ArrayList<>();
        boolean mine = aboutSpeaker(heard);
        if (mine) {
            said.add(language.vocabulary.find("you"));
            said.add(language.vocabulary.find("have"));
        }
        said.add(language.vocabulary.find(NUMBERS.get(count)));
        said.add(thing);
        said.add(language.vocabulary.find("."));
        return said.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Comparisons the other way round. */
    private static final java.util.Map<String, String> OPPOSITE = opposites("older younger", "bigger smaller",
            "taller shorter", "faster slower", "stronger weaker", "richer poorer", "heavier lighter", "higher lower",
            "deeper shallower", "longer shorter", "wider narrower", "hotter colder", "louder quieter");

    private static java.util.Map<String, String> opposites(String... pairs) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        for (String p : pairs) {
            String[] w = p.split(" ");
            m.putIfAbsent(w[0], w[1]);
            m.putIfAbsent(w[1], w[0]);
        }
        return m;
    }

    /**
     * The comparisons told lately with the given word ("older"): each the one, the other, and
     * whether each was said with "the" ("the mill is bigger than the church .").
     */
    private List<int[]> comparisons(String moreWord) {
        int than = language.vocabulary.find("than");
        List<int[]> more = new ArrayList<>();
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            for (int i = 1; i < line.length; i++) {
                if (line[i] == than && line[i - 1] >= 0 && language.vocabulary.word(line[i - 1]).equals(moreWord)) {
                    int one = thingAt(line, i - 2, -1), other = thingAt(line, i + 1, 1);
                    if (one >= 0 && other >= 0) {
                        more.add(new int[] {one, other});
                        // How each was said ("the iron bar"), to say it the same way.
                        int head = i - 2;
                        while (head > 0 && line[head] != one) {
                            head--;
                        }
                        int from = head;
                        while (from > 0 && line[from - 1] >= 0 && Character.isLetter(language.vocabulary.word(line[from - 1]).charAt(0))
                                && !BETWEEN.contains(language.vocabulary.word(line[from - 1])) && head - from < 2) {
                            from--;
                        }
                        from = from > 0 && line[from - 1] == language.vocabulary.find("the") ? from - 1 : from;
                        phrases.putIfAbsent(one, Arrays.copyOfRange(line, from, head + 1));
                        int to = i + 1;
                        while (to < line.length && line[to] != other) {
                            to++;
                        }
                        phrases.putIfAbsent(other, Arrays.copyOfRange(line, i + 1, Math.min(to + 1, line.length)));
                    }
                }
            }
        }
        return more;
    }

    /** How the things compared lately were said, by the word for each. */
    private final java.util.Map<Integer, int[]> phrases = new java.util.HashMap<>();

    /** Whether a line starts by telling of someone or something just told of ("his cart broke."). */
    private boolean toldOfAgain(int[] line) {
        return line.length > 0 && line[0] >= 0
                && Set.of("he", "she", "it", "they", "his", "her", "its", "their").contains(language.vocabulary.word(line[0]));
    }

    /** Words that put a thing somewhere ("to the mill", "onto the wall"). */
    private static final Set<String> PLACE = Set.of("to", "into", "onto", "in", "on", "at", "inside", "under", "behind");
    /** Words that pass a thing on ("gave the key to anna"). */
    private static final Set<String> PASSING = Set.of("give", "gave", "lend", "lent", "hand", "handed", "pass", "passed",
            "sell", "sold", "throw", "threw", "bring", "brought");
    /** Doings that last, and so change when someone goes somewhere else ("where does pedro live?"). */
    private static final Set<String> LASTING = Set.of("live", "lives", "stay", "stays", "sleep", "sleeps", "work", "works");
    private static final Set<String> GOING = Set.of("go", "went", "move", "moved", "ran", "run", "walked", "came", "come",
            "jumped", "flew", "climbed", "left", "took", "put");
    /** Words a line starts with when it tells what happened next. */
    private static final Set<String> NEXT = Set.of("then", "now", "later", "after", "afterwards", "since", "but");
    private static final java.util.Map<String, String> STATE_OPPOSITE = opposites("open closed", "on off", "full empty",
            "alive dead", "awake asleep", "locked unlocked", "wet dry", "lit out", "hungry full");
    private static final Set<String> ASKING_WORDS = Set.of("where", "who", "what", "which", "how", "why", "when", "whose");

    /**
     * What was told later of the thing a told line tells of, when it changes the answer: after
     * "mary went to the kitchen." and "mary went to the garden.", "where is mary?" is answered by
     * the garden; after "tom took the key." and "tom gave the key to anna.", "who has the key?" by
     * "anna has the key ."; after "the door was open." and "i closed the door.", "is the door
     * open?" by the closing. A later line that starts with he, she or it right after tells of the
     * same thing, and says it by name. Null if nothing later changed it.
     */
    private int[] toldLater(int[] heard, int[] told, int k) {
        int ask = language.vocabulary.find("?");
        String first = heard[0] < 0 ? "" : language.vocabulary.word(heard[0]);
        boolean where = first.equals("where");
        boolean whoHas = first.equals("who") && heard.length > 2 && heard[1] >= 0
                && Set.of("has", "have", "had").contains(language.vocabulary.word(heard[1]));
        boolean yesNo = ASKS_YES_OR_NO.contains(first);
        int state = yesNo && heard.length > 2 && heard[heard.length - 2] >= 0
                && hasOpposite(language.vocabulary.word(heard[heard.length - 2])) ? heard[heard.length - 2] : -1;
        int doing = -1;
        if (where && heard.length > 3 && heard[1] >= 0 && Set.of("did", "does", "do").contains(language.vocabulary.word(heard[1]))) {
            doing = heard[heard.length - 2];
            if (doing >= 0 && (PLACE.contains(language.vocabulary.word(doing)) || language.vocabulary.word(doing).equals("from"))) {
                // "where did the traveler come from?" asks of coming.
                doing = heard[heard.length - 3];
            }
        }
        if (k == 0) {
            // A few lines together answered it; what happened after the last of them may change it.
            if (!where && !whoHas) {
                return null;
            }
            for (int j = 2; j <= TOLD_LINES && j <= heardLinesNext && k == 0; j++) {
                int[] line = heardLines[(heardLinesNext - j) % heardLines.length];
                if (line.length > 0 && line.length <= told.length && Arrays.equals(line, Arrays.copyOfRange(told, told.length - line.length, told.length))) {
                    k = j;
                    told = line;
                }
            }
            if (k == 0) {
                return null;
            }
        }
        List<Integer> things = new ArrayList<>();
        for (int w : heard) {
            if (w >= 0 && w != state && w != doing && thingWord(w) && !ASKING_WORDS.contains(language.vocabulary.word(w))
                    && !PLACE.contains(language.vocabulary.word(w)) && !language.vocabulary.word(w).equals("from")) {
                things.add(w);
            }
        }
        List<Integer> subject = new ArrayList<>();
        for (int w : told) {
            if (w >= 0 && thingWord(w)) {
                // "the cat was on the roof" tells of the cat.
                subject.add(w);
                break;
            }
        }
        things.addAll(subject);
        int[] now = null;
        int lastK = k;
        for (int j = k - 1; j >= 2; j--) {
            int[] line = heardLines[(heardLinesNext - j) % heardLines.length];
            if (line.length == 0 || line.length > TOLD_LONGEST || line[line.length - 1] == ask) {
                continue;
            }
            boolean again = j == lastK - 1 && pronounAt(line) >= 0;
            for (int w : line) {
                again |= w >= 0 && thingWord(w) && sameThing(List.of(w), things);
            }
            if (!again) {
                continue;
            }
            boolean changes;
            if (where) {
                // "the dog took the bread ." moves it too.
                changes = doing < 0 || LASTING.contains(language.vocabulary.word(doing)) ? saysAny(line, PLACE)
                        || doing < 0 && saysAny(line, Set.of("took", "stole", "grabbed", "ate")) : saidIn(line, doing);
                changes &= doing < 0 || saidIn(line, doing) || saysAny(line, GOING);
            } else if (whoHas) {
                changes = passedTo(line) >= 0;
            } else if (yesNo) {
                changes = state >= 0 && (saidIn(line, state) || saysOpposite(line, state))
                        || j == lastK - 1 && line[0] >= 0 && NEXT.contains(language.vocabulary.word(line[0]));
            } else {
                changes = saysAny(line, NEXT) || saysAny(line, Set.of("became", "becomes", "become"));
            }
            if (changes) {
                now = byName(line, told, subject);
                lastK = j;
            }
        }
        int to = whoHas ? passedTo(now != null ? now : told) : -1;
        if (to >= 0) {
            // "tom gave the key to anna .": "anna has the key ."
            List<Integer> who = thingFrom(now != null ? now : told, to + 1);
            if (who.isEmpty()) {
                return now;
            }
            int[] answer = new int[who.size() + heard.length - 1];
            for (int i = 0; i < who.size(); i++) {
                answer[i] = who.get(i);
            }
            System.arraycopy(heard, 1, answer, who.size(), heard.length - 2);
            answer[answer.length - 1] = language.vocabulary.find(".");
            return answer;
        }
        return now;
    }

    /**
     * Asked where someone is, of lines told together of them, the last that puts them somewhere,
     * by name ("then she went to the garden ." "mary went to the garden ."); null if not asked
     * where or no later line puts them anywhere.
     */
    private int[] wentLast(int[] heard, List<int[]> chain) {
        if (chain.size() < 2 || heard[0] < 0 || !language.vocabulary.word(heard[0]).equals("where")) {
            return null;
        }
        List<Integer> subject = new ArrayList<>();
        for (int w : chain.get(0)) {
            if (w >= 0 && thingWord(w)) {
                subject.add(w);
                break;
            }
        }
        for (int i = chain.size() - 1; i > 0; i--) {
            int[] line = chain.get(i);
            if (saysAny(line, PLACE) && pronounAt(line) >= 0) {
                return byName(line, chain.get(0), subject);
            }
        }
        return null;
    }

    /** Where in its first few words a line says he, she, it or they; -1 if it doesn't. */
    private int pronounAt(int[] line) {
        for (int i = 0; i < Math.min(4, line.length); i++) {
            if (line[i] >= 0 && Set.of("he", "she", "it", "they").contains(language.vocabulary.word(line[i]))) {
                return i;
            }
        }
        return -1;
    }

    /** A line with its he, she or it said as the thing told of before ("then it ran" "then the horse ran"). */
    private int[] byName(int[] line, int[] told, List<Integer> subject) {
        int at = pronounAt(line);
        int start = subject.isEmpty() ? -1 : indexOf(told, subject.get(0), 0);
        if (at < 0 || start < 0) {
            return line;
        }
        int end = start + subject.size();
        while (start > 0 && told[start - 1] >= 0 && Set.of("the", "a", "an", "my", "your", "our").contains(language.vocabulary.word(told[start - 1]))) {
            start--;
        }
        return join(Arrays.copyOfRange(line, 0, at), Arrays.copyOfRange(told, start, end), Arrays.copyOfRange(line, at + 1, line.length));
    }

    /** Where the one something was passed to is named ("gave the key to anna" names anna after "to"); -1 if none. */
    private int passedTo(int[] line) {
        for (int i = 0; i < line.length; i++) {
            if (line[i] >= 0 && PASSING.contains(language.vocabulary.word(line[i]))) {
                return indexOf(line, language.vocabulary.find("to"), i + 1);
            }
        }
        return -1;
    }

    /**
     * A short follow-up that leans on the question before it ("and john?", "what about the
     * dog?") as the whole question it means: the earlier question with the new thing in place of
     * the one it asked about ("where is mary?" then "and john?": "where is john?"). Which word
     * gives way is found in what was told: the one that stands where the new thing stands in a
     * line told alike ("mary is in the kitchen ." "john is in the garden ."), else the earlier
     * question's last word. Returns null if the line is no such follow-up; k is how far back the
     * line before it was heard.
     */
    private int[] followedUp(int[] heard, int k) {
        Vocabulary v = language.vocabulary;
        int ask = v.find("?");
        int from = heard.length > 1 && heard[0] == v.find("and") ? 1
            : heard.length > 2 && heard[1] == v.find("about") && (heard[0] == v.find("what") || heard[0] == v.find("how")) ? 2 : -1;
        int end = heard.length - 1;
        if (from < 0 || heard[end] != ask || end - from < 1 || end - from > 4 || k > heardLinesNext || k > 4
                || ASKING_WORDS.contains(v.word(heard[from])) || saysAny(heard, AGAIN)) {
            return null;
        }
        int[] before = heardLines[(heardLinesNext - k) % heardLines.length];
        if (before == null || before.length < 3 || before[before.length - 1] != ask) {
            return null;
        }
        int[] earlier = followedUp(before, k + 1);
        before = earlier != null ? earlier : withoutAddress(before);
        if (before.length < 3) {
            return null;
        }
        // "the blue one": the word that tells it apart.
        int head = heard[end - 1];
        if ((v.word(head).equals("one") || v.word(head).equals("ones")) && end - from > 1) {
            head = heard[end - 2];
        }
        if (head < 0 || !thingWord(head) || AGAIN.contains(v.word(head))) {
            // "and then?", "and you?" ask nothing new of a thing.
            return null;
        }
        int swap = -1;
        for (int j = 2; j <= TOLD_LINES && j <= heardLinesNext && swap < 0; j++) {
            int[] told = heardLines[(heardLinesNext - j) % heardLines.length];
            int at = told == null || told[told.length - 1] == ask ? -1 : indexOf(told, head, 0);
            for (int i = 2; at >= 0 && i <= TOLD_LINES && i <= heardLinesNext && swap < 0; i++) {
                int[] other = heardLines[(heardLinesNext - i) % heardLines.length];
                if (i == j || other == null || other.length <= at || other[at] == head || other[other.length - 1] == ask
                        || !contains(before, other[at]) || ASKING_WORDS.contains(v.word(other[at]))) {
                    continue;
                }
                int alike = 0;
                for (int w = 0; w < Math.min(told.length, other.length); w++) {
                    alike += w != at && told[w] == other[w] && Character.isLetter(v.word(told[w]).charAt(0)) ? 1 : 0;
                }
                swap = alike > 0 ? other[at] : -1;
            }
        }
        if (swap < 0) {
            swap = before[before.length - 2];
            Set<String> the = Set.of("a", "an", "the", "my", "your", "some", "his", "her", "their");
            if (end - from > 1 && heard[from] >= 0 && the.contains(v.word(heard[from])) && before[before.length - 3] >= 0
                    && !the.contains(v.word(before[before.length - 3])) && swap != head) {
                // "how do i make bread?" then "and a torch?": "how do i make a torch?"
                int[] meant = Arrays.copyOf(before, before.length - 2 + end - from + 1);
                System.arraycopy(heard, from, meant, before.length - 2, end - from);
                meant[meant.length - 1] = ask;
                return meant;
            }
        }
        if (swap == head || ASKING_WORDS.contains(v.word(swap))) {
            return null;
        }
        int[] meant = before.clone();
        for (int i = 0; i < meant.length; i++) {
            meant[i] = meant[i] == swap ? head : meant[i];
        }
        return meant;
    }

    /**
     * A question that says "he", "she" or "it" right after another question, with the things that
     * question asked of added at its end ("what does ivan own?" then "where does he keep it?"
     * asks of ivan too). Null if it says none or no question came just before.
     */
    private int[] ofTheSame(int[] heard) {
        int ask = language.vocabulary.find("?");
        if (heard[heard.length - 1] != ask || !saysAny(heard, AGAIN) || heardLinesNext < 2) {
            return null;
        }
        int[] before = heardLines[(heardLinesNext - 2) % heardLines.length];
        if (before == null || before.length < 3 || before[before.length - 1] != ask) {
            return null;
        }
        int[] meant = withThingsOf(Arrays.copyOf(heard, heard.length - 1), withoutAddress(before));
        if (meant.length == heard.length - 1) {
            return null;
        }
        meant = Arrays.copyOf(meant, meant.length + 1);
        meant[meant.length - 1] = ask;
        return meant;
    }

    private static int[] withoutAt(int[] line, int at) {
        int[] out = new int[line.length - 1];
        System.arraycopy(line, 0, out, 0, at);
        System.arraycopy(line, at + 1, out, at, line.length - at - 1);
        return out;
    }

    /** A line with the thing words of another line that it lacks added at its end. */
    private int[] withThingsOf(int[] line, int[] other) {
        int[] out = line;
        for (int w : other) {
            if (w >= 0 && thingWord(w) && !ASKING_WORDS.contains(language.vocabulary.word(w)) && !AGAIN.contains(language.vocabulary.word(w))
                    && !PLACE.contains(language.vocabulary.word(w)) && !contains(out, w)) {
                out = Arrays.copyOf(out, out.length + 1);
                out[out.length - 1] = w;
            }
        }
        return out;
    }

    /**
     * For "is max in the library?", when the best line does not say max, the line that tells
     * where max is, if one does. Null otherwise.
     */
    private int[] placeOf(int[] heard, int[] best, List<int[]> toldLines) {
        Vocabulary v = language.vocabulary;
        int at = placeAsked(heard);
        if (at < 0) {
            return null;
        }
        List<Integer> subject = new ArrayList<>();
        for (int i = 1; i < at; i++) {
            if (heard[i] >= 0 && thingWord(heard[i])) {
                subject.add(heard[i]);
            }
        }
        if (subject.isEmpty() || subject.stream().allMatch(w -> saidIn(best, w))) {
            return null;
        }
        for (int[] told : toldLines) {
            if (told.length > 0 && told[told.length - 1] != v.find("?") && told.length <= TOLD_LONGEST
                    && subject.stream().allMatch(w -> saidIn(told, w)) && placeAt(told) >= 0) {
                return told;
            }
        }
        return null;
    }

    /** Where "is X in/at/by ...?" names its place: the index of the place word, or -1. */
    private int placeAsked(int[] heard) {
        Vocabulary v = language.vocabulary;
        if (heard.length < 5 || heard[0] < 0 || !Set.of("is", "are", "was", "were").contains(v.word(heard[0]))) {
            return -1;
        }
        for (int i = 2; i < heard.length - 2; i++) {
            if (heard[i] >= 0 && (PLACE.contains(v.word(heard[i])) || v.word(heard[i]).equals("by"))) {
                return heard[heard.length - 2] >= 0 && thingWord(heard[heard.length - 2]) ? i : -1;
            }
        }
        return -1;
    }

    /** The first word in a line that puts something at a place ("in", "at", "by"), or -1. */
    private int placeAt(int[] told) {
        for (int i = 0; i + 1 < told.length; i++) {
            String w = told[i] < 0 ? "" : language.vocabulary.word(told[i]);
            if ((PLACE.contains(w) || w.equals("by")) && !w.equals("to")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * For "where is the key?" answered by a line kept from long ago, the latest kept line since
     * that puts the same thing somewhere else; null if none does. Kept lines are latest first.
     */
    private int[] movedSince(int[] heard, List<int[]> lines, int from, int at) {
        Vocabulary v = language.vocabulary;
        if (heard[0] < 0 || !v.word(heard[0]).equals("where")) {
            return null;
        }
        List<Integer> things = new ArrayList<>();
        for (int w : heard) {
            if (w >= 0 && thingWord(w) && !ASKING_WORDS.contains(v.word(w)) && !PLACE.contains(v.word(w))
                    && !Set.of("is", "are", "was", "were", "now").contains(v.word(w))) {
                things.add(w);
            }
        }
        for (int t = from; t < at; t++) {
            int[] line = lines.get(t);
            boolean of = !things.isEmpty();
            for (int w : things) {
                of &= saidIn(line, w);
            }
            if (of && line[line.length - 1] != v.find("?") && saysAny(line, PLACE)) {
                return line;
            }
        }
        int[] next = at - 1 >= from ? lines.get(at - 1) : null;
        if (next != null && next[next.length - 1] != v.find("?") && pronounAt(next) >= 0 && saysAny(next, PLACE)) {
            // "mary went to the kitchen ." "then she went to the garden .": she is mary.
            List<Integer> subject = new ArrayList<>();
            for (int w : lines.get(at)) {
                if (w >= 0 && thingWord(w)) {
                    subject.add(w);
                    break;
                }
            }
            return byName(next, lines.get(at), subject);
        }
        return null;
    }

    /**
     * "is john in the kitchen?" after "john is in the garden .": no, the line puts it somewhere
     * else; yes if it puts it there. -1 if the question asks of no place or the line tells of none.
     */
    private int placeAnswer(int[] heard, int[] told) {
        if (placeAsked(heard) < 0) {
            return -1;
        }
        return saidIn(told, heard[heard.length - 2]) ? language.vocabulary.find("yes")
            : placeAt(told) >= 0 ? language.vocabulary.find("no") : -1;
    }

    private boolean saysAny(int[] line, Set<String> words) {
        for (int w : line) {
            if (w >= 0 && words.contains(language.vocabulary.word(w))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a line says the opposite of a state ("closed" for "open"). */
    private boolean saysOpposite(int[] line, int state) {
        String word = state < 0 ? null : language.vocabulary.word(state);
        if (word == null || !hasOpposite(word)) {
            return false;
        }
        String other = STATE_OPPOSITE.get(word);
        for (int w : line) {
            if (w >= 0 && (other != null && stem(language.vocabulary.word(w)).equals(stem(other))
                    || QUALITY_OPPOSITE.getOrDefault(word, Set.of()).contains(language.vocabulary.word(w)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a line says what a word of the question asks for without saying the word: a colour
     * for "colour", the plain quality for "bigger" ("big").
     */
    private boolean answersKind(int q, int[] told) {
        String word = q < 0 ? "" : language.vocabulary.word(q);
        if (word.equals("colour") || word.equals("color")) {
            return saysAny(told, COLOURS);
        }
        String plain = OPPOSITE.containsKey(word) ? plainQuality(word) : null;
        return plain != null && saysAny(told, Set.of(plain));
    }

    private boolean yesNoAsked(int[] heard) {
        return heard.length > 2 && heard[0] >= 0 && ASKS_YES_OR_NO.contains(language.vocabulary.word(heard[0]));
    }

    /** The quality a yes or no question asks of ("quiet" in "is the tavern quiet at night?"), or -1. */
    private int qualityAsked(int[] heard) {
        for (int i = 2; i < heard.length; i++) {
            if (heard[i] >= 0 && QUALITY_OPPOSITE.containsKey(language.vocabulary.word(heard[i]))) {
                return heard[i];
            }
        }
        return -1;
    }

    private static final Set<String> NOT_OF_THE_EVENT = Set.of("did", "do", "does", "happened", "happen", "then",
        "after", "before", "first", "last", "what", "where", "who", "go", "went", "the", "a", "an", "he", "she", "it",
        "they", "we", "his", "her", "their", "?");

    /**
     * Questions about the order of what was told, from the lines told just before the question,
     * in the order they were told: what someone did (or what happened) first or last, what came
     * after or before something ("what did mia do after breakfast?"), and whether one thing came
     * before another ("did ivo chop wood before he fixed the roof?" "no , ivo fixed the roof
     * first ."). Null if the question asks none of these or the lines don't tell it.
     */
    private int[] inOrder(int[] heard) {
        Vocabulary v = language.vocabulary;
        int ask = v.find("?");
        int first = v.find("first"), last = v.find("last"), after = v.find("after"), before = v.find("before");
        int end = heard.length - 1;
        boolean yesNo = yesNoAsked(heard);
        int split = Math.max(indexOf(heard, after, 1), indexOf(heard, before, 1));
        // What came after or before something is found among all told lately, questions in
        // between or not; what came first or last, in the lines told since the last question
        // that asked of something else than the order of things.
        boolean wide = split > 1 && split < end - 1;
        List<int[]> story = new ArrayList<>();
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            if (line == null || line.length == 0 || line[line.length - 1] == ask) {
                if (wide || line != null && (contains(line, first) || contains(line, last)
                        || contains(line, after) || contains(line, before))) {
                    continue;
                }
                break;
            }
            story.add(0, line);
        }
        if (story.size() < 2) {
            return null;
        }
        if (wide) {
            boolean isAfter = heard[split] == after;
            int[] event = Arrays.copyOfRange(heard, split + 1, end);
            int at = lineOf(story, event, -1);
            if (at < 0) {
                return null;
            }
            boolean told = false;
            for (int[] line : story) {
                told |= contains(line, after) || contains(line, before);
            }
            if (yesNo && told) {
                // "the bell rang after the storm ." says the order itself, whenever it was told.
                return null;
            }
            if (yesNo) {
                // "did ivo chop wood before he fixed the roof?"
                int doing = heard[1] >= 0 && Set.of("the", "a", "an", "my", "your").contains(v.word(heard[1])) ? 3 : 2;
                int other = lineOf(story, Arrays.copyOfRange(heard, Math.min(doing, split), split), at);
                if (other < 0) {
                    return null;
                }
                boolean so = isAfter ? other > at : other < at;
                return so ? withWord(v.find("yes"), story.get(other)) : firstOf(story.get(isAfter ? other : at), v);
            }
            int next = isAfter ? at + 1 : at - 1;
            return next >= 0 && next < story.size() ? story.get(next) : null;
        }
        if (heard[end - 1] != first && heard[end - 1] != last) {
            return null;
        }
        int[] asked = Arrays.copyOfRange(heard, 1, end - 1);
        // Who did it is told in every line of theirs, so what they did tells the line ("did lars
        // plant beans first?").
        int doing = heard.length > 3 && heard[1] >= 0 && Set.of("the", "a", "an", "my", "your").contains(v.word(heard[1])) ? 3 : 2;
        if (yesNo) {
            // "did ivo fix the roof first?"
            int at = lineOf(story, Arrays.copyOfRange(heard, Math.min(doing, end - 1), end - 1), -1);
            if (at < 0 || heard[end - 1] != first) {
                return null;
            }
            return at == 0 ? withWord(v.find("yes"), story.get(0)) : firstOf(story.get(0), v);
        }
        if (heard[0] < 0 || !Set.of("what", "where", "who").contains(v.word(heard[0]))) {
            return null;
        }
        // Of whom: "what did tom do first?"; the lines that tell of them, or of what they did
        // next ("then he milked the cow .").
        List<int[]> theirs = new ArrayList<>();
        boolean of = false;
        for (int[] line : story) {
            boolean says = false, some = false;
            for (int w : asked) {
                if (w >= 0 && !NOT_OF_THE_EVENT.contains(v.word(w)) && thingWord(w)) {
                    some = true;
                    says |= saidIn(line, w);
                }
            }
            of = !some || says || of && (saysAny(line, AGAIN) || line[0] == v.find("then"));
            if (of) {
                theirs.add(line);
            }
        }
        if (theirs.isEmpty()) {
            return null;
        }
        return heard[end - 1] == first ? theirs.get(0) : theirs.get(theirs.size() - 1);
    }

    /** The line of a story that says most of what an event says (at least one telling word), other than skip; -1 if none. */
    private int lineOf(List<int[]> story, int[] event, int skip) {
        int best = -1, most = 0;
        for (int i = 0; i < story.size(); i++) {
            int says = 0;
            for (int w : event) {
                says += i != skip && w >= 0 && !NOT_OF_THE_EVENT.contains(language.vocabulary.word(w)) && thingWord(w)
                        && saidIn(story.get(i), w) ? 1 : 0;
            }
            if (says > most) {
                most = says;
                best = i;
            }
        }
        return best;
    }

    private int[] withWord(int word, int[] line) {
        int[] out = new int[line.length + 2];
        out[0] = word;
        out[1] = language.vocabulary.find(",");
        System.arraycopy(line, 0, out, 2, line.length);
        return out;
    }

    /** "no , ivo fixed the roof first ." from "ivo fixed the roof ." */
    private static int[] firstOf(int[] line, Vocabulary v) {
        int from = line.length > 1 && line[0] == v.find("then") ? 1 : 0;
        int end = line.length > 0 && line[line.length - 1] >= 0 && !Character.isLetter(v.word(line[line.length - 1]).charAt(0))
            ? line.length - 1 : line.length;
        int[] out = new int[end - from + 4];
        out[0] = v.find("no");
        out[1] = v.find(",");
        System.arraycopy(line, from, out, 2, end - from);
        out[out.length - 2] = v.find("first");
        out[out.length - 1] = v.find(".");
        return out;
    }

    /** "bigger": "big", "larger": "large", "heavier": "heavy". */
    private static String plainQuality(String more) {
        String w = more.substring(0, more.length() - 2);
        if (more.endsWith("ier")) {
            return more.substring(0, more.length() - 3) + "y";
        }
        if (w.length() > 2 && w.charAt(w.length() - 1) == w.charAt(w.length() - 2)) {
            return w.substring(0, w.length() - 1);
        }
        return QUALITY_OPPOSITE.containsKey(w + "e") ? w + "e" : w;
    }

    private static final Set<String> COLOURS = Set.of("red", "green", "blue", "yellow", "white", "black", "brown",
            "grey", "gray", "orange", "pink", "purple", "golden", "silver");

    /** Whether a word is a state or a quality with an opposite ("open", "sharp"). */
    private static boolean hasOpposite(String word) {
        return STATE_OPPOSITE.containsKey(word) || QUALITY_OPPOSITE.containsKey(word);
    }

    /** Qualities and their opposites, each of which may have more than one ("tall": "short", "low"). */
    private static final java.util.Map<String, Set<String>> QUALITY_OPPOSITE = qualities("warm cold", "hot cold",
            "sharp dull", "sharp blunt", "clean dirty", "big small", "big little", "large small", "tall short", "tall low", "tall small", "tall little",
            "high low", "long short", "old young", "old new", "fresh stale", "fresh rotten", "heavy light", "fast slow",
            "rich poor", "happy sad", "strong weak", "deep shallow", "wide narrow", "loud quiet", "safe dangerous",
            "kind mean", "easy hard", "soft hard", "good bad", "clever stupid", "friendly unfriendly", "busy quiet",
            "thick thin", "fat thin", "bright dark", "light dark", "sweet sour", "sweet bitter", "smooth rough",
            "cheap expensive", "near far", "early late", "brave scared", "healthy sick", "well sick", "tired rested");

    private static java.util.Map<String, Set<String>> qualities(String... pairs) {
        java.util.Map<String, Set<String>> m = new java.util.HashMap<>();
        for (String p : pairs) {
            String[] w = p.split(" ");
            m.computeIfAbsent(w[0], x -> new java.util.HashSet<>()).add(w[1]);
            m.computeIfAbsent(w[1], x -> new java.util.HashSet<>()).add(w[0]);
        }
        return m;
    }

    /** Whether a line is one of those heard lately. */
    private boolean toldLately(int[] line) {
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            if (Arrays.equals(line, heardLines[(heardLinesNext - k) % heardLines.length])) {
                return true;
            }
        }
        return false;
    }

    /** The words a yes or no question starts with. */
    private static final Set<String> ASKS_YES_OR_NO = Set.of("is", "are", "was", "were", "am", "did", "does", "do", "can",
            "has", "have", "will", "isn't", "aren't", "didn't", "doesn't");

    /**
     * The answer to a yes or no question from the told line that answers it: yes if the line says
     * everything the question asks ("is the baker sick?" "the baker is sick today."), else -1 (not
     * such a question, or the line said something else, and the line itself is the answer: "is the
     * gate open?" "the gate is closed."). A word that goes with one the line said can mean the same
     * ("ill", "sick") or the opposite ("open", "closed"), so it is never taken for a no.
     */
    private int yesOrNo(int[] heard, int[] told, ExchangeMemory memory, ToneLinks tones) {
        if (heard.length == 0 || heard[0] < 0 || !ASKS_YES_OR_NO.contains(language.vocabulary.word(heard[0]))) {
            return -1;
        }
        for (int i = 1; i < heard.length; i++) {
            int q = heard[i];
            // Small words ("my", "the") don't make it a different question.
            if (q >= 0 && toldWeight(q, memory, tones) >= TOLD_TELLING / 2 && !saidIn(told, q)) {
                return -1;
            }
        }
        if (rule(told) || onlyIfSaid(heard, told)) {
            // "if it rains, the field floods." alone doesn't say that it did; nor does "the field
            // flooded ." with it say that it rained.
            return -1;
        }
        int order = sameOrder(heard, told);
        if (order != 0 && negated(told, heard) != negated(heard, heard)) {
            // "is there bread?" "there is no bread."
            order = -order;
        }
        return order == 0 ? -1 : language.vocabulary.find(order > 0 ? "yes" : "no");
    }

    private static final Set<String> ASKING = Set.of("is", "are", "can", "was", "were", "will", "does", "do");
    static final float JUDGE_MARGIN = 1f;

    /**
     * A yes or no answer from what the language area knows: "is a horse bigger than a mouse?"
     * is weighed as "a horse is bigger than a mouse ." against "a mouse is bigger than a horse ."; the likelier by a clear
     * margin is the answer, said with yes or no. Null if it is no such question or neither is
     * clearly likelier. The language area is left elsewhere.
     */
    private int[] judged(int[] heardLine) {
        int[] heard = withoutAddress(heardLine);
        int ask = language.vocabulary.find("?");
        if (heard.length < 4 || heard.length > TOLD_QUESTION_WORDS || heard[heard.length - 1] != ask || heard[0] < 0
                || !ASKING.contains(language.vocabulary.word(heard[0]))) {
            return null;
        }
        int end = 1;
        while (end < heard.length - 1 && heard[end] >= 0 && BEFORE_A_THING.contains(language.vocabulary.word(heard[end]))) {
            end++;
        }
        if (end >= heard.length - 2) {
            return null;
        }
        // "a horse" is "bigger than a mouse"
        int[] subject = Arrays.copyOfRange(heard, 1, end + 1);
        int[] rest = Arrays.copyOfRange(heard, end + 1, heard.length - 1);
        int aux = heard[0];
        int than = indexOf(rest, language.vocabulary.find("than"), 0);
        int[] said = join(subject, new int[] {aux}, rest);
        if (than < 0) {
            return null;
        }
        int[] other = join(Arrays.copyOfRange(rest, than + 1, rest.length), new int[] {aux}, Arrays.copyOfRange(rest, 0, than + 1), subject);
        said = join(said, new int[] {language.vocabulary.find(".")});
        other = join(other, new int[] {language.vocabulary.find(".")});
        float yes = outOfNowhere(said, said.length), no = outOfNowhere(other, other.length);
        if (Math.abs(yes - no) < JUDGE_MARGIN) {
            return null;
        }
        return join(new int[] {language.vocabulary.find(yes > no ? "yes" : "no"), language.vocabulary.find(",")}, yes > no ? said : other);
    }

    private static int[] join(int[]... parts) {
        int n = 0;
        for (int[] p : parts) {
            n += p.length;
        }
        int[] all = new int[n];
        int at = 0;
        for (int[] p : parts) {
            System.arraycopy(p, 0, all, at, p.length);
            at += p.length;
        }
        return all;
    }

    /**
     * A remembered answer made to say what it says the right way round: one that answered "is a
     * dog bigger than a mouse?" with "yes , a dog is bigger than a mouse ." says "no" to "is a
     * mouse bigger than a dog?"; null if it tells of other things than were asked about.
     */
    private int[] keptTrue(int[] heard, int[] answer) {
        int yes = language.vocabulary.find("yes"), no = language.vocabulary.find("no");
        if (answer.length < 3 || answer[0] != yes && answer[0] != no) {
            return answer;
        }
        int[] said = Arrays.copyOfRange(answer, 1, answer.length);
        int than = language.vocabulary.find("than");
        int at = indexOf(heard, than, 0);
        if (at > 0) {
            // About other things than the question ("yes , a fox is bigger than a rabbit ." to
            // "is a rabbit bigger than a bear?"), it is no answer to it.
            List<Integer> asked = new ArrayList<>(thingFrom(heard, 0));
            asked.addAll(thingFrom(heard, at + 1));
            int its = indexOf(said, than, 0);
            List<Integer> one = thingFrom(said, 0);
            if (!one.isEmpty() && !sameThing(one, asked) || its >= 0 && !sameThing(thingFrom(said, its + 1), asked)) {
                return null;
            }
            if (its < 0 && !one.isEmpty() && sameThing(one, thingFrom(heard, 0)) != (answer[0] == yes)) {
                // "no , a fox is bigger ." to "is a fox bigger than ...?" says both.
                return null;
            }
        }
        int order = sameOrder(heard, said);
        if (order < 0) {
            int[] turned = answer.clone();
            turned[0] = answer[0] == yes ? no : yes;
            return turned;
        }
        return answer;
    }

    /** Words that put two things one way round: "older than", "north of", "came before", "in". */
    private static final Set<String> ONE_WAY = Set.of("than", "of", "before", "after", "in", "inside", "on", "under",
            "behind", "above", "below");

    /**
     * For a question that puts two things one way round ("is tom older than ben?", "is the ring in
     * the cellar?"), whether what was told puts them the same way (1: "tom is older than sam . sam
     * is older than ben .") or the other (-1: "ben is older than tom ."); 0 if it can't be told; 1
     * if it is no such question. The first thing is the first one named where the first such word
     * is said, the other the one said after the last such word.
     */
    private int sameOrder(int[] heard, int[] told) {
        int at = -1;
        for (int i = 2; i + 1 < heard.length; i++) {
            at = heard[i] >= 0 && ONE_WAY.contains(language.vocabulary.word(heard[i])) ? i : at;
        }
        if (at < 0 || indexOf(told, heard[at], 0) < 0) {
            return 1;
        }
        int way = heard[at];
        int last = indexOf(told, way, 0);
        for (int i = last + 1; i < told.length; i++) {
            last = told[i] == way ? i : last;
        }
        // The thing the told lines start from, and the one the last of them ends at.
        List<Integer> askedOne = thingFrom(heard, 0), askedOther = thingFrom(heard, at + 1);
        List<Integer> toldOne = thingFrom(told, 0), toldOther = thingFrom(told, last + 1);
        if (askedOne.isEmpty() || askedOther.isEmpty() || toldOne.isEmpty() || toldOther.isEmpty()) {
            return 0;
        }
        if (sameThing(askedOne, toldOne) && sameThing(askedOther, toldOther)) {
            return 1;
        }
        return sameThing(askedOne, toldOther) && sameThing(askedOther, toldOne) ? -1 : 0;
    }

    private boolean sameThing(List<Integer> a, List<Integer> b) {
        for (int x : a) {
            for (int y : b) {
                if (sameThing(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The words naming a thing from a place in a line on, past the small words before it and up
     * to the next small word or stop ("the north tower is" names the north tower).
     */
    private List<Integer> thingFrom(int[] line, int from) {
        while (from < line.length && line[from] >= 0 && (BEFORE_A_THING.contains(language.vocabulary.word(line[from]))
                || !Character.isLetter(language.vocabulary.word(line[from]).charAt(0)))) {
            from++;
        }
        List<Integer> thing = new ArrayList<>();
        for (int i = from; i < line.length && thing.size() < 3 && line[i] >= 0; i++) {
            String w = language.vocabulary.word(line[i]);
            if (!Character.isLetter(w.charAt(0)) || BETWEEN.contains(w) || ONE_WAY.contains(w) || BEFORE_A_THING.contains(w)) {
                break;
            }
            thing.add(line[i]);
        }
        return thing;
    }

    /** Words a question or telling starts with before it names a thing. */
    private static final Set<String> BEFORE_A_THING = Set.of("is", "are", "was", "were", "am", "did", "does", "do", "can",
            "will", "has", "have", "the", "a", "an", "my", "your", "his", "her", "our", "their", "all", "every");


    /** Small words between a comparison and what it compares ("is", "the"). */
    private static final Set<String> BETWEEN = Set.of("is", "are", "was", "were", "am", "the", "a", "an", "much", "far", "even", "still");

    /** The word for a thing compared, from an index on, going the given way past small words; -1 if none. */
    private int thingAt(int[] line, int i, int step) {
        while (i >= 0 && i < line.length && line[i] >= 0 && BETWEEN.contains(language.vocabulary.word(line[i]))) {
            i += step;
        }
        // Going on, the last word before the line goes on to something else is the thing itself
        // ("than the north tower ." compares with the tower).
        while (step > 0 && i + 1 < line.length && line[i + 1] >= 0 && Character.isLetter(language.vocabulary.word(line[i + 1]).charAt(0))
                && !language.vocabulary.word(line[i + 1]).equals("and")) {
            i++;
        }
        return i >= 0 && i < line.length ? line[i] : -1;
    }

    private boolean sameThing(int a, int b) {
        return a == b || a >= 0 && b >= 0 && stem(language.vocabulary.word(a)).equals(stem(language.vocabulary.word(b)));
    }

    private static final Set<String> NOT = Set.of("no", "not", "never", "isn't", "wasn't", "aren't", "weren't", "don't",
            "doesn't", "didn't", "can't", "won't", "hasn't", "haven't", "nobody", "nothing");

    /**
     * Whether what a line says of the question's thing is said not to be so: the part of it (cut
     * at commas and full stops) that says most of the question's words has a "no" or "not" in it
     * ("if the baker oversleeps , there is no bread ." of "is there bread?").
     */
    private boolean negated(int[] line, int[] heard) {
        int comma = language.vocabulary.find(","), stop = language.vocabulary.find(".");
        int best = -1;
        boolean not = false;
        // What is asked of it ("swim" in "can the thing in the river swim?") is said with or
        // without "not" in the part that says it ("no zombie can swim ."), whatever the rest says.
        int asked = heard.length > 2 && heard[heard.length - 1] >= 0 && !Character.isLetter(language.vocabulary.word(heard[heard.length - 1]).charAt(0))
                ? heard[heard.length - 2] : -1;
        for (int from = 0; from < line.length; ) {
            int to = from;
            int says = 0;
            boolean no = false, saysAsked = false;
            for (; to < line.length && line[to] != comma && line[to] != stop; to++) {
                if (line[to] >= 0) {
                    no |= NOT.contains(language.vocabulary.word(line[to]));
                    says += saidIn(heard, line[to]) && Character.isLetter(language.vocabulary.word(line[to]).charAt(0)) ? 1 : 0;
                    saysAsked |= asked >= 0 && line != heard && line[to] == asked;
                }
            }
            if (saysAsked) {
                return no;
            }
            if (says > best) {
                best = says;
                not = no;
            }
            from = to + 1;
        }
        return not;
    }

    /**
     * Whether what is asked ("did the bell ring?") is said only as what a rule needs ("if the bell
     * rings , the guards come ."), not as what happened.
     */
    private boolean onlyIfSaid(int[] heard, int[] told) {
        Vocabulary v = language.vocabulary;
        int asked = heard.length > 2 ? heard[heard.length - 2] : -1;
        if (asked < 0 || !thingWord(asked)) {
            return false;
        }
        int stop = v.find("."), comma = v.find(","), iff = v.find("if"), when = v.find("when");
        boolean inRule = false, happened = false;
        boolean condition = false;
        for (int i = 0; i < told.length; i++) {
            int w = told[i];
            if (w == iff || w == when) {
                condition = true;
            } else if (w == comma || w == stop) {
                condition = false;
            } else if (w >= 0 && saidIn(new int[] {w}, asked)) {
                inRule |= condition;
                happened |= !condition;
            }
        }
        return inRule && !happened;
    }

    /** Whether a line is a rule alone ("if it rains, the field floods.", "when the bell rings, ..."). */
    private boolean rule(int[] line) {
        return (contains(line, language.vocabulary.find("if")) || contains(line, language.vocabulary.find("when")))
                && countOf(line, language.vocabulary.find(".")) < 2;
    }

    private static int indexOf(int[] line, int word, int from) {
        for (int i = from; i < line.length; i++) {
            if (line[i] == word) {
                return i;
            }
        }
        return -1;
    }

    private static int countOf(int[] line, int word) {
        int n = 0;
        for (int w : line) {
            n += w == word ? 1 : 0;
        }
        return n;
    }

    /**
     * What things were given names lately ("my dog is called rex.", "the dog's name is rex."), as
     * the name, the word for the thing, and 1 if it is the speaker's own ("my dog").
     */
    private int[][] namesGiven() {
        List<int[]> names = new ArrayList<>();
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            namesIn(heardLines[(heardLinesNext - k) % heardLines.length], names);
        }
        return names.toArray(new int[0][]);
    }

    /** Adds the names a line gives to the list; true if it gives any. */
    private boolean namesIn(int[] line, List<int[]> names) {
        int found = names.size();
        for (int i = 0; i + 1 < line.length; i++) {
            String w = line[i] < 0 ? "" : language.vocabulary.word(line[i]);
            String before = i > 0 && line[i - 1] >= 0 ? language.vocabulary.word(line[i - 1]) : "";
            if ((w.equals("called") || w.equals("named")) && (before.equals("is") || before.equals("was")) && i >= 2) {
                names.add(new int[] {line[i + 1], line[i - 2], aboutSpeaker(line) ? 1 : 0});
            } else if (w.equals("name") && i + 2 < line.length && line[i + 1] >= 0
                    && language.vocabulary.word(line[i + 1]).equals("is") && before.endsWith("'s")) {
                names.add(new int[] {line[i + 2], language.vocabulary.find(before.substring(0, before.length() - 2)),
                    aboutSpeaker(line) ? 1 : 0});
            }
        }
        return names.size() > found;
    }

    /** Whether a line names something of the speaker's own ("rex is sick." after "my dog is called rex."). */
    private static boolean namesTheirs(int[] line, int[][] names) {
        for (int[] n : names) {
            if (n[2] == 1 && contains(line, n[0])) {
                return true;
            }
        }
        return false;
    }

    /** A told line with the words for what the names in it name added. */
    private static int[] withWhatTheyName(int[] line, int[][] names) {
        int[] out = line;
        for (int[] n : names) {
            if (n[1] >= 0 && contains(line, n[0]) && !contains(out, n[1])) {
                out = Arrays.copyOf(out, out.length + 1);
                out[out.length - 1] = n[1];
            }
        }
        return out;
    }

    /**
     * How telling a word is in a question about something told: as a cue to remembered exchanges,
     * and a name ("lena", "pedro"), which never cues one, as a rare word.
     */
    private float toldWeight(int w, ExchangeMemory memory, ToneLinks tones) {
        if (w < 0) {
            return 0f;
        }
        float weight = memory.weight(w);
        String word = language.vocabulary.word(w);
        // A word that cues nothing is a name ("pedro"), even one heard as often as "john" or
        // "paris", but not one of the few small words that cue nothing either.
        return weight > 0f || tones == null || word.length() < 3 || !Character.isLetter(word.charAt(0))
                || tones.common(w) && NOT_A_NAME.contains(word) ? weight : TOLD_NAME;
    }

    private static final Set<String> NOT_A_NAME = Set.of("the", "hey", "you", "i'm", "just", "and", "that", "plus", "hmm",
            "hmmm", "additionally", "ugh", "nope", "why's", "yep");

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

    /**
     * "what do my bees make?" without the "my", when the line told of their bees ("i keep bees .")
     * tells nothing of what the question asks they do: what is so of all bees answers it. Null if
     * the question asks of no thing of theirs, or the line tells what it asks.
     */
    private boolean ofAllAsked;

    /**
     * The question asked of a kind instead of the one named: "how many legs does a dog have?" for
     * "how many legs does rex have?" when the line says "a dog called rex"; null if it names none.
     */
    private int[] ofTheNamed(int[] heard, int[] best) {
        Vocabulary v = language.vocabulary;
        for (int k = 1; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] told = k == 1 ? best : heardLines[(heardLinesNext - k) % heardLines.length];
            int[] asked = told == null ? null : ofTheNamedIn(heard, told);
            if (asked != null) {
                return asked;
            }
        }
        // Or named long ago.
        for (int[] told : talk.toldLines()) {
            int[] asked = ofTheNamedIn(heard, told);
            if (asked != null) {
                return asked;
            }
        }
        return null;
    }

    private int[] ofTheNamedIn(int[] heard, int[] told) {
        Vocabulary v = language.vocabulary;
        if (told.length == 0 || told[told.length - 1] == v.find("?")) {
            return null;
        }
        for (int j = 1; j + 1 < told.length; j++) {
            if (told[j] < 0 || !Set.of("called", "named").contains(v.word(told[j]))) {
                continue;
            }
            // "a dog called rex", "my dog is called max"
            int thing = j - 1;
            if (thing > 0 && told[thing] >= 0 && Set.of("is", "was").contains(v.word(told[thing]))) {
                thing--;
            }
            if (told[thing] < 0 || !thingWord(told[thing])) {
                continue;
            }
            int at = indexOf(heard, told[j + 1], 0);
            if (at > 0 && at < heard.length - 1 && thingWord(told[j + 1])) {
                int[] asked = new int[heard.length + 1];
                System.arraycopy(heard, 0, asked, 0, at);
                asked[at] = v.find("a");
                asked[at + 1] = told[thing];
                System.arraycopy(heard, at + 1, asked, at + 2, heard.length - at - 1);
                return asked[at] < 0 ? null : asked;
            }
        }
        return null;
    }

    /**
     * Where the place a line puts something is: for "where is the key?" answered by "the key is
     * in the box .", a line told lately that puts the box somewhere ("the box is in the barn .");
     * null if none does or it is no where question.
     */
    private int[] whereThatIs(int[] heard, int[] best) {
        Vocabulary v = language.vocabulary;
        if (heard.length < 3 || heard[0] < 0 || !v.word(heard[0]).equals("where")) {
            return null;
        }
        int at = placeAt(best);
        int place = -1;
        for (int i = Math.max(at, 0) + 1; at >= 0 && i < best.length; i++) {
            if (best[i] >= 0 && thingWord(best[i]) && !BEFORE_A_THING.contains(v.word(best[i]))) {
                place = best[i];
                break;
            }
        }
        if (place < 0 || saidIn(heard, place)) {
            return null;
        }
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            int there = line == null || line.length == 0 || line[line.length - 1] == v.find("?") ? -1 : placeAt(line);
            int of = there < 0 ? -1 : indexOf(line, place, 0);
            if (of >= 0 && of < there && !Arrays.equals(line, best)) {
                return line;
            }
        }
        return null;
    }

    /**
     * "who is taller, tom or ben?" worked out as "is tom taller than ben?" from what was told
     * ("tom is taller than sam ." "sam is taller than ben ."): "tom is taller than ben ."; null if
     * it is no such question or what was told doesn't tell.
     */
    private int[] whichOfTwo(int[] heard) {
        Vocabulary v = language.vocabulary;
        int comma = indexOf(heard, v.find(","), 0), or = indexOf(heard, v.find("or"), 0);
        if (heard.length < 8 || heard[0] < 0 || !Set.of("who", "which", "what").contains(v.word(heard[0])) || heard[1] < 0
                || !Set.of("is", "are", "was", "were").contains(v.word(heard[1])) || comma != 3 || or < comma + 2
                || or >= heard.length - 2 || heard[2] < 0 || !v.word(heard[2]).endsWith("er")) {
            return null;
        }
        int[] a = Arrays.copyOfRange(heard, comma + 1, or);
        int[] b = Arrays.copyOfRange(heard, or + 1, heard.length - 1);
        int[] asked = join(new int[] {heard[1]}, a, new int[] {heard[2], v.find("than")}, b, new int[] {v.find("?")});
        int[] keepLine = toldLine;
        boolean keepOwn = toldInOwnWords, keepSure = toldSure;
        ofAllAsked = true;
        toldBefore(asked);
        ofAllAsked = false;
        int[] answer = toldLine;
        toldLine = keepLine;
        toldInOwnWords = keepOwn;
        toldSure = keepSure;
        if (answer == null || answer.length == 0 || answer[0] != v.find("yes") && answer[0] != v.find("no")) {
            // Nothing told says; what it knows of the world may ("what is bigger, a house or a
            // mouse?").
            answer = judged(asked);
        }
        if (answer == null || answer.length == 0 || answer[0] != v.find("yes") && answer[0] != v.find("no")) {
            return null;
        }
        boolean first = answer[0] == v.find("yes");
        return join(first ? a : b, new int[] {heard[1], heard[2], v.find("than")}, first ? b : a, new int[] {v.find(".")});
    }

    /**
     * "how many people went to the market?" after "tom , anna and ben went to the market .":
     * "three people went to the market ."; null if no line told lately lists who did it.
     */
    private int[] countedList(int[] heard) {
        Vocabulary v = language.vocabulary;
        if (heard.length < 5 || heard[0] < 0 || heard[1] < 0 || !v.word(heard[0]).equals("how") || !v.word(heard[1]).equals("many")) {
            return null;
        }
        int[] doing = Arrays.copyOfRange(heard, 3, heard.length - 1);
        if (doing.length == 0 || doing[0] < 0 || Set.of("do", "does", "did", "are", "is", "were", "was", "have", "has").contains(v.word(doing[0]))) {
            return null;
        }
        int and = v.find("and"), comma = v.find(",");
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            int at = line == null ? -1 : indexOf(line, doing[0], 0);
            if (at < 3 || !saidIn(line, doing[doing.length - 1])) {
                continue;
            }
            int[] who = Arrays.copyOfRange(line, 0, at);
            if (indexOf(who, and, 0) < 0) {
                continue;
            }
            int n = 1;
            for (int w : who) {
                n += w == and || w == comma ? 1 : 0;
            }
            return join(new int[] {numberWord(n), heard[2]}, Arrays.copyOfRange(line, at, line.length));
        }
        return null;
    }

    /**
     * What a rule told lately says will be, when what it needs was told too: "if it rains , we
     * stay home ." and "it is raining .": "what do we do?" "we stay home ."; "what happens now?"
     * likewise. Null if no rule told lately has its condition told.
     */
    private int[] ruleFollows(int[] heard) {
        Vocabulary v = language.vocabulary;
        if (heard.length < 3 || heard[0] < 0 || !v.word(heard[0]).equals("what")
                || !saysAny(heard, Set.of("do", "does", "happens", "happen", "will"))) {
            return null;
        }
        int iff = v.find("if"), when = v.find("when"), comma = v.find(",");
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] rule = heardLines[(heardLinesNext - k) % heardLines.length];
            if (rule == null || rule.length < 5 || rule[0] != iff && rule[0] != when) {
                continue;
            }
            int split = indexOf(rule, comma, 1);
            if (split < 2 || split >= rule.length - 2) {
                continue;
            }
            int[] needs = Arrays.copyOfRange(rule, 1, split);
            int[] then = Arrays.copyOfRange(rule, split + 1, rule.length);
            boolean ofThem = saysAny(heard, Set.of("happens", "happen")) || then[0] >= 0 && contains(heard, then[0]);
            if (!ofThem && then[0] >= 0 && Set.of("he", "she", "it", "they").contains(v.word(then[0]))) {
                // "if you feed the chickens , they lay eggs .": "what do the chickens do?"
                for (int w : needs) {
                    ofThem |= w >= 0 && thingWord(w) && !NO_THING.contains(v.word(w)) && saidIn(heard, w)
                            && !Set.of("do", "does", "did").contains(v.word(w));
                }
            }
            if (!ofThem) {
                continue;
            }
            for (int j = 2; j <= TOLD_LINES && j <= heardLinesNext; j++) {
                int[] fact = heardLines[(heardLinesNext - j) % heardLines.length];
                if (j == k || fact == null || fact.length == 0 || fact[fact.length - 1] == v.find("?") || rule(fact)
                        || negated(fact, fact)) {
                    continue;
                }
                boolean all = true, some = false;
                for (int w : needs) {
                    if (w >= 0 && thingWord(w) && !NO_THING.contains(v.word(w))) {
                        boolean said = false;
                        for (int f : fact) {
                            said |= f >= 0 && sameDoing(v.word(w), v.word(f));
                        }
                        all &= said;
                        some = true;
                    }
                }
                if (all && some) {
                    return then;
                }
            }
        }
        return null;
    }

    private static final Set<String> ALL_OF = Set.of("all", "every", "always", "each");
    private static final Set<String> YES_NO = Set.of("is", "are", "was", "were", "do", "does", "did", "can", "will");
    /** Words of a question that say nothing of what it is about. */
    private static final Set<String> ONLY_ASKING = Set.of("colour", "color", "kind", "usually", "often", "like", "up",
            "all", "every", "always", "each", "any", "most");

    /**
     * What several told lines alike say together, when a question asks of them all: three lines
     * the same but for whose goat or which day ("tom's goat is brown .", "ben's goat is brown .",
     * "ana's goat is brown .") make "what colour are goats?" "each goat i heard of is brown ."; a
     * line that differs in one more word is a case against it ("are all goats brown?" "no , ben's
     * goat is white ."; "are the stones heavy?" "most were , but the third stone was light .").
     * Null when no three lines are alike so, or the question names one of them or asks of
     * something else.
     */
    private int[] generalised(int[] heard) {
        Vocabulary v = language.vocabulary;
        List<List<String>> told = new ArrayList<>();
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            if (line != null && line.length >= 4 && line.length <= TOLD_LONGEST && line[line.length - 1] == v.find(".")) {
                told.add(wordsOf(line));
            }
        }
        List<String> q = wordsOf(heard);
        if (told.size() < 3 || q.size() < 3 || !q.get(q.size() - 1).equals("?")) {
            return null;
        }
        for (List<String> base : told) {
            int n = base.size();
            for (int slot = -1; slot < n - 1; slot++) {
                List<List<String>> alike = new ArrayList<>(), against = new ArrayList<>();
                int otherAt = -1;
                Set<String> named = new java.util.HashSet<>();
                for (List<String> line : told) {
                    if (line.size() != n) {
                        continue;
                    }
                    List<Integer> differ = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        if (i != slot && !line.get(i).equals(base.get(i))) {
                            differ.add(i);
                        }
                    }
                    if (differ.isEmpty()) {
                        alike.add(line);
                        named.add(slot < 0 ? "" : line.get(slot));
                    } else if (differ.size() == 1 && (otherAt < 0 || otherAt == differ.get(0))) {
                        otherAt = differ.get(0);
                        against.add(line);
                        named.add(slot < 0 ? "" : line.get(slot));
                    }
                }
                if (alike.size() < 2 || alike.size() + against.size() < 3 || alike.size() <= against.size()
                        || slot >= 0 && named.size() < alike.size() + against.size()) {
                    continue;
                }
                int[] said = generalSaid(base, slot, otherAt, alike, against, q, named);
                if (said != null) {
                    return said;
                }
            }
        }
        return null;
    }

    private int[] generalSaid(List<String> base, int slot, int otherAt, List<List<String>> alike,
            List<List<String>> against, List<String> q, Set<String> named) {
        int n = base.size();
        // The question asks of them all: it names none of them, and what it is about was told.
        if (q.stream().anyMatch(named::contains)) {
            return null;
        }
        int about = 0, off = 0;
        boolean saysValue = otherAt < 0;
        for (int i = 0; i < q.size() - 1; i++) {
            String w = q.get(i);
            int id = language.vocabulary.find(w);
            if (id < 0 || !thingWord(id) || ASKING_WORDS.contains(w) || ONLY_ASKING.contains(w) || YES_NO.contains(w)) {
                continue;
            }
            int at = -1;
            for (int j = 0; j < n && at < 0; j++) {
                if (j != slot && (sameDoing(w, base.get(j)) || w.endsWith("ies") && base.get(j).equals(w.substring(0, w.length() - 3) + "y"))) {
                    at = j;
                }
            }
            if (at < 0) {
                off++;
            } else {
                about++;
                saysValue |= at == otherAt;
            }
        }
        if (about == 0 || off > about) {
            return null;
        }
        // Said of them all: "each goat i heard of is brown .", "the miller woke early each day i heard of .",
        // "the red mushroom made everyone sick .", "the blue flowers grew in the shade every time ."
        List<String> all = new ArrayList<>();
        String head = slot >= 0 && slot + 1 < n ? base.get(slot + 1) : "";
        String which = slot >= 0 ? base.get(slot) : "";
        boolean before = slot >= 0 && !head.isEmpty() && !AFTER_WHO.contains(head) && (which.endsWith("'s")
                || Set.of("my", "your", "his", "her", "our", "their").contains(which) || slot > 0 && base.get(slot - 1).equals("the"));
        for (int i = 0; i < n - 1; i++) {
            String w = base.get(i);
            if (i == slot) {
                if (DAYS.contains(w) && i > 0 && base.get(i - 1).equals("on")) {
                    all.remove(all.size() - 1);
                    all.addAll(List.of("each", "day", "i", "heard", "of"));
                } else if (before) {
                    if (!all.isEmpty() && all.get(all.size() - 1).equals("the")) {
                        all.remove(all.size() - 1);
                    }
                    boolean many = head.endsWith("s") && !head.endsWith("ss");
                    all.addAll(many ? List.of("all", "the", head, "i", "heard", "of") : List.of("each", head, "i", "heard", "of"));
                    i++;
                } else {
                    all.add("everyone");
                }
            } else {
                all.add(w);
            }
        }
        if (slot < 0) {
            all.addAll(List.of("every", "time"));
        }
        all.add(".");
        boolean yesNo = YES_NO.contains(q.get(0)), ofAll = q.stream().anyMatch(ALL_OF::contains);
        List<String> out = new ArrayList<>();
        if (!against.isEmpty()) {
            if (yesNo && ofAll) {
                out.addAll(List.of("no", ","));
            } else {
                String aux = Set.of("is", "are", "was", "were").contains(base.get(slot + 1 < n && slot + 2 < n ? slot + 2 : 0)) ? "were" : "did";
                out.addAll(List.of("most", aux, ",", "but"));
            }
            out.addAll(against.get(0));
            return said(String.join(" ", out));
        }
        if (yesNo && saysValue && off == 0) {
            out.addAll(List.of("yes", ","));
        }
        out.addAll(all);
        return said(String.join(" ", out));
    }

    /** Each way one thing can lie from another: its line (0 north-south, 1 east-west, 2 right-left, 3 up-down) and side. */
    private static final java.util.Map<String, int[]> WAYS = java.util.Map.of("north", new int[] {0, 1}, "south",
            new int[] {0, -1}, "east", new int[] {1, 1}, "west", new int[] {1, -1}, "right", new int[] {2, 1}, "left",
            new int[] {2, -1}, "above", new int[] {3, 1}, "over", new int[] {3, 1}, "below", new int[] {3, -1}, "under",
            new int[] {3, -1});
    private static final String[][] WAY_WORDS = {{"north", "south"}, {"east", "west"}, {"right", "left"}, {"above", "below"}};

    /**
     * The earlier told line a told line goes against, said back as "wait , you said the cart is red
     * ."; what can't be so of one thing at once: two colours, two counts, two names, two kinds
     * ("tom is a baker ." "tom is a farmer ."), a line and its "not", a comparison turned round.
     * Where someone is, what they did and states that change ("the gate is open .") are not
     * against what was said before. Null if nothing told lately is gone against.
     */
    private int[] saidOtherwise(int[] heard) {
        List<String> now = wordsOf(heard);
        int n = now.size();
        if (n < 4 || n > TOLD_LONGEST || !now.get(n - 1).equals(".")) {
            return null;
        }
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            List<String> was = line == null ? List.of() : wordsOf(line);
            if (was.size() < 4 || was.size() > TOLD_LONGEST || !was.get(was.size() - 1).equals(".") || was.equals(now)) {
                continue;
            }
            if (against(was, now) || against(now, was) && was.size() != n) {
                return join(said("wait , you said"), toldBack(line));
            }
        }
        return null;
    }

    /** Whether a told line goes against an earlier one; see {@link #saidOtherwise}. */
    private static boolean against(List<String> was, List<String> now) {
        if (was.size() + 1 == now.size()) {
            // "the well is dry ." "the well is not dry ."
            for (int i = 1; i < was.size(); i++) {
                if (now.get(i).equals("not") && Set.of("is", "are", "was", "were").contains(was.get(i - 1))
                        && now.subList(0, i).equals(was.subList(0, i)) && now.subList(i + 1, now.size()).equals(was.subList(i, was.size()))) {
                    return true;
                }
            }
            return false;
        }
        if (was.size() != now.size()) {
            return false;
        }
        List<Integer> differ = new ArrayList<>();
        for (int i = 0; i < was.size(); i++) {
            if (!was.get(i).equals(now.get(i))) {
                differ.add(i);
            }
        }
        if (differ.size() == 2 && was.contains("than")) {
            // "tom is older than ben ." "ben is older than tom ."
            int a = differ.get(0), b = differ.get(1);
            return was.get(a).equals(now.get(b)) && was.get(b).equals(now.get(a)) && a < was.indexOf("than") && b > was.indexOf("than");
        }
        if (differ.size() != 1) {
            return false;
        }
        int i = differ.get(0);
        String a = was.get(i), b = now.get(i), before = i > 0 ? was.get(i - 1) : "";
        if (i == 0 || was.contains("in") || was.contains("at") || was.contains("to")) {
            // A different one, or where it is: no contradiction.
            return false;
        }
        boolean has = Set.of("has", "have", "had").contains(before);
        boolean isIt = Set.of("is", "are", "was", "were").contains(before);
        return COLOURS.contains(a) && COLOURS.contains(b) && isIt
                || (number(a) >= 0 && number(b) >= 0) && has
                || Set.of("called", "named").contains(before)
                || Set.of("a", "an").contains(before) && i == was.size() - 2 && i >= 3 && Set.of("is", "was").contains(was.get(i - 2));
    }

    /** One told "the well is north of the mill .": the things, the way, and the line. */
    private record Lies(String a, String b, String way, String line) {}

    /** "the well is north of the mill" read as the two things and the way, or null. */
    private static Lies liesOf(List<String> w) {
        int is = w.indexOf("is");
        if (is < 1 || is + 2 >= w.size()) {
            return null;
        }
        int at = is + 1;
        if (w.get(at).equals("to") && at + 2 < w.size() && w.get(at + 1).equals("the")) {
            at += 2;
        }
        String way = w.get(at);
        int from;
        if (way.equals("next") && at + 1 < w.size() && w.get(at + 1).equals("to")) {
            from = at + 2;
        } else if (WAYS.containsKey(way) && WAYS.get(way)[0] < 3) {
            if (at + 1 >= w.size() || !w.get(at + 1).equals("of")) {
                return null;
            }
            from = at + 2;
        } else if (WAYS.containsKey(way)) {
            from = at + 1;
        } else {
            return null;
        }
        if (from >= w.size()) {
            return null;
        }
        String a = String.join(" ", w.subList(0, is)), b = String.join(" ", w.subList(from, w.size()));
        return a.isEmpty() || b.isEmpty() || a.equals(b) ? null : new Lies(a, b, way, null);
    }

    /** "south of the well", "below the lamp", "next to the inn". */
    private static String wayTo(String way, String b) {
        return way.equals("next") ? "next to " + b : WAYS.get(way)[0] < 3 ? way + " of " + b : way + " " + b;
    }

    /**
     * Where things lie from each other, worked out from what was told: "the well is north of the
     * mill ." and "the mill is north of the church ." make "is the church south of the well?" "yes ,
     * the church is south of the well , because ..."; "where is the farm?" after "the lake is west
     * of the farm ." is "the farm is east of the lake ."; "which is furthest north?" names it. When
     * the told lines don't settle it ("the mill is north of the well ." "the church is north of the
     * well ." "is the mill north of the church?") it says it doesn't know. Null when nothing told
     * of such places bears on it.
     */
    private int[] whereFrom(int[] heard) {
        List<String> q = wordsOf(heard);
        int n = q.size();
        if (n < 4 || !q.get(n - 1).equals("?")) {
            return null;
        }
        List<Lies> told = new ArrayList<>();
        for (int k = TOLD_LINES; k >= 2; k--) {
            if (k > heardLinesNext) {
                continue;
            }
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            List<String> w = line == null ? List.of() : wordsOf(line);
            if (w.size() < 5 || !w.get(w.size() - 1).equals(".") || w.size() > TOLD_LONGEST) {
                continue;
            }
            Lies l = liesOf(w.subList(0, w.size() - 1));
            if (l != null) {
                told.add(new Lies(l.a(), l.b(), l.way(), String.join(" ", w.subList(0, w.size() - 1))));
            }
        }
        if (told.isEmpty()) {
            return null;
        }
        List<String> body = q.subList(0, n - 1);
        if (body.get(0).equals("is")) {
            // "is the church south of the well?"
            List<String> as = withIs(body);
            Lies asked = as.isEmpty() ? null : liesOf(as);
            if (asked == null || told.stream().noneMatch(l -> l.a().equals(asked.a()) || l.b().equals(asked.a()))) {
                return null;
            }
            if (asked.way().equals("next")) {
                for (Lies l : told) {
                    if (l.way().equals("next") && (l.a().equals(asked.a()) && l.b().equals(asked.b())
                            || l.a().equals(asked.b()) && l.b().equals(asked.a()))) {
                        return said("yes , " + l.line() + " .");
                    }
                }
                return null;
            }
            int axis = WAYS.get(asked.way())[0], side = WAYS.get(asked.way())[1];
            List<Lies> chain = path(told, axis, asked.a(), asked.b(), side);
            if (chain != null) {
                // Said as told, in the order told: "yes , the well is north of the mill , and the mill
                // is north of the church ."
                List<String> lines = told.stream().map(Lies::line).filter(l -> chain.stream().anyMatch(c -> c.line().equals(l))).toList();
                return said("yes , " + String.join(" , and ", lines) + " .");
            }
            List<Lies> against = path(told, axis, asked.a(), asked.b(), -side);
            if (against != null) {
                String other = WAY_WORDS[axis][side > 0 ? 1 : 0];
                return said("no , " + asked.a() + " is " + wayTo(other, asked.b()) + " .");
            }
            boolean known = told.stream().anyMatch(l -> l.a().equals(asked.b()) || l.b().equals(asked.b()));
            List<String> lines = told.stream().filter(l -> WAYS.containsKey(l.way()) && WAYS.get(l.way())[0] == axis)
                    .map(Lies::line).toList();
            return known && !lines.isEmpty() ? said("i don't know , " + String.join(" , and ", lines) + " .") : null;
        }
        if (body.get(0).equals("where") && body.size() >= 3 && body.get(1).equals("is")) {
            // "where is the farm?" when only told what lies from it.
            String thing = String.join(" ", body.subList(2, body.size()));
            if (told.stream().anyMatch(l -> l.a().equals(thing))) {
                return null;
            }
            for (Lies l : told) {
                if (l.b().equals(thing)) {
                    String back = l.way().equals("next") ? "next" : WAY_WORDS[WAYS.get(l.way())[0]][WAYS.get(l.way())[1] > 0 ? 1 : 0];
                    return said(thing + " is " + wayTo(back, l.a()) + " .");
                }
            }
            return null;
        }
        if (body.get(0).equals("what") && body.size() >= 5 && body.get(1).equals("is") && WAYS.containsKey(body.get(2))) {
            // "what is south of the well?"
            String way = body.get(2);
            int at = body.get(3).equals("of") ? 4 : 3;
            String thing = String.join(" ", body.subList(at, body.size()));
            int axis = WAYS.get(way)[0], side = WAYS.get(way)[1];
            for (Lies l : told) {
                if (!WAYS.containsKey(l.way()) || WAYS.get(l.way())[0] != axis) {
                    continue;
                }
                int s = WAYS.get(l.way())[1];
                if (l.b().equals(thing) && s == side) {
                    return said(l.line() + " .");
                }
                if (l.a().equals(thing) && s == -side) {
                    return said(l.b() + " is " + wayTo(way, thing) + " .");
                }
            }
            return null;
        }
        int far = body.indexOf("furthest") >= 0 ? body.indexOf("furthest") : body.indexOf("farthest");
        if (body.get(0).equals("which") && far >= 0 && far + 1 < body.size() && WAYS.containsKey(body.get(far + 1))) {
            // "which is furthest north?": the one nothing lies further that way from.
            String way = body.get(far + 1);
            int axis = WAYS.get(way)[0], side = WAYS.get(way)[1];
            Set<String> things = new java.util.LinkedHashSet<>();
            for (Lies l : told) {
                if (WAYS.containsKey(l.way()) && WAYS.get(l.way())[0] == axis) {
                    things.add(l.a());
                    things.add(l.b());
                }
            }
            for (String t : things) {
                boolean beyond = things.stream().anyMatch(o -> !o.equals(t) && path(told, axis, o, t, side) != null);
                boolean all = things.stream().allMatch(o -> o.equals(t) || path(told, axis, t, o, side) != null);
                if (!beyond && all) {
                    return said(t + " is furthest " + way + " .");
                }
            }
            return null;
        }
        return null;
    }

    /** "is the church south of the well" read as a told line. */
    private static List<String> withIs(List<String> body) {
        int of = -1;
        for (int i = 2; i < body.size() && of < 0; i++) {
            if (WAYS.containsKey(body.get(i)) || body.get(i).equals("next") || body.get(i).equals("to") && i + 1 < body.size()
                    && body.get(i + 1).equals("the") && i + 2 < body.size() && WAYS.containsKey(body.get(i + 2))) {
                of = i;
            }
        }
        if (of < 0) {
            return List.of();
        }
        List<String> line = new ArrayList<>(body.subList(1, of));
        line.add("is");
        line.addAll(body.subList(of, body.size()));
        return line;
    }

    /**
     * The told lines that put a further than b one way along a line (side 1 north, east, right or
     * up; -1 the other), each step either told so or told the other way round; null if they don't.
     */
    private static List<Lies> path(List<Lies> told, int axis, String a, String b, int side) {
        java.util.Map<String, List<Lies>> from = new java.util.HashMap<>();
        java.util.Map<String, String> to = new java.util.HashMap<>();
        for (Lies l : told) {
            int[] w = WAYS.get(l.way());
            if (w == null || w[0] != axis) {
                continue;
            }
            // Each step goes from the thing further the asked way to the one less so.
            String hi = w[1] == side ? l.a() : l.b(), lo = w[1] == side ? l.b() : l.a();
            from.computeIfAbsent(hi, x -> new ArrayList<>()).add(new Lies(hi, lo, l.way(), l.line()));
        }
        java.util.Map<String, Lies> came = new java.util.HashMap<>();
        java.util.ArrayDeque<String> open = new java.util.ArrayDeque<>(List.of(a));
        Set<String> seen = new java.util.HashSet<>(List.of(a));
        while (!open.isEmpty()) {
            String at = open.poll();
            if (at.equals(b)) {
                List<Lies> chain = new ArrayList<>();
                for (String c = b; !c.equals(a); c = came.get(c).a()) {
                    chain.add(0, came.get(c));
                }
                return chain;
            }
            for (Lies step : from.getOrDefault(at, List.of())) {
                if (seen.add(step.b())) {
                    came.put(step.b(), step);
                    open.add(step.b());
                }
            }
        }
        return null;
    }

    /** A rule told of a kind: "all blicks are furry .", "no glorps can fly .", "some vims are green ." */
    private record KindRule(String kind, String how, List<String> does, List<String> line) {}

    private static final Set<String> HOW_MANY_OF = Set.of("all", "every", "no", "some", "each");

    /** Whether a word names the kind: "blicks", "blick" and "a blick"; "dax" and "dax"; "ponies" and "pony". */
    private static boolean ofKind(String word, String kind) {
        return word.equals(kind) || word.equals(kind + "s") || word.equals(kind + "es")
                || kind.endsWith("y") && word.equals(kind.substring(0, kind.length() - 1) + "ies")
                || kind.equals(word + "s") || kind.equals(word + "es");
    }

    /** "live" said of one: "lives"; "are" "is", "have" "has". */
    private static String ofOne(String verb) {
        return switch (verb) {
            case "are" -> "is";
            case "have" -> "has";
            case "do" -> "does";
            case "can", "will", "must", "should", "is", "has", "does" -> verb;
            default -> verb.endsWith("s") || verb.endsWith("sh") || verb.endsWith("ch") || verb.endsWith("x") ? verb + "es"
                    : verb + "s";
        };
    }

    /**
     * The answer to a question about one of a kind, reasoned from rules told of the kind: after
     * "all blicks are furry ." and "zim is a blick .", "is zim furry?" is "yes , all blicks are
     * furry , and zim is a blick ."; "no glorps can fly ." makes "can pip fly?" "no"; "where does ari
     * live?" after "all snorks live in caves ." is "ari lives in caves ."; rules chain ("all fips
     * are blue ." "all blue things are cold ."). What doesn't follow it won't say: one not of the
     * kind ("mo is not a dax ."), "some", or the rule turned round ("all mogs are tall ." "tess is
     * tall ." "is tess a mog?") get "i don't know" and why. Null when no told rule bears on it.
     */
    private int[] fromRules(int[] heard) {
        Vocabulary v = language.vocabulary;
        List<String> q = wordsOf(heard);
        int n = q.size();
        if (n < 3 || n > TOLD_QUESTION_WORDS || !q.get(n - 1).equals("?")) {
            return null;
        }
        List<KindRule> rules = new ArrayList<>();
        java.util.Map<String, List<String>> kindOf = new java.util.HashMap<>(), notKindOf = new java.util.HashMap<>(),
                saidOf = new java.util.HashMap<>();
        for (int k = TOLD_LINES; k >= 2; k--) {
            if (k > heardLinesNext) {
                continue;
            }
            int[] told = heardLines[(heardLinesNext - k) % heardLines.length];
            List<String> w = told == null ? List.of() : wordsOf(told);
            if (w.size() < 3 || w.size() > TOLD_LONGEST || !w.get(w.size() - 1).equals(".")) {
                continue;
            }
            List<String> body = w.subList(0, w.size() - 1);
            String first = body.get(0);
            if (HOW_MANY_OF.contains(first) && body.size() >= 3) {
                // "all blue things are cold .": the kind is "blue things".
                int at = 2;
                if (body.size() >= 4 && Set.of("things", "ones").contains(body.get(2))) {
                    at = 3;
                }
                String kind = at == 3 ? body.get(1) + " things" : body.get(1);
                rules.add(new KindRule(kind, first.equals("each") ? "every" : first, body.subList(at, body.size()), body));
            } else if (body.size() >= 3 && first.endsWith("s") && !first.endsWith("'s") && !BEFORE_A_THING.contains(first)
                    && !NO_THING.contains(first) && Set.of("are", "can", "have", "live", "eat").contains(body.get(1))) {
                // "grubs are slow ."
                rules.add(new KindRule(first, "all", body.subList(1, body.size()), body));
            } else if (body.size() == 4 && body.get(1).equals("is") && Set.of("a", "an").contains(body.get(2))) {
                kindOf.computeIfAbsent(first, x -> new ArrayList<>()).add(body.get(3));
            } else if (body.size() == 5 && body.get(1).equals("is") && body.get(2).equals("not") && Set.of("a", "an").contains(body.get(3))) {
                notKindOf.computeIfAbsent(first, x -> new ArrayList<>()).add(body.get(4));
            } else if (body.size() == 3 && body.get(1).equals("is")) {
                saidOf.computeIfAbsent(first, x -> new ArrayList<>()).add(body.get(2));
            }
        }
        if (rules.isEmpty()) {
            return null;
        }
        // Who the question asks of: a name told of above.
        String name = null;
        int nameAt = -1;
        for (int i = 0; i < n - 1 && name == null; i++) {
            String w = q.get(i);
            if (kindOf.containsKey(w) || notKindOf.containsKey(w) || saidOf.containsKey(w)) {
                name = w;
                nameAt = i;
            }
        }
        if (name == null) {
            return null;
        }
        boolean yesNo = YES_NO.contains(q.get(0));
        List<String> asked = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            String w = q.get(i);
            int id = v.find(w);
            if (i != nameAt && id >= 0 && thingWord(id) && !ASKING_WORDS.contains(w) && !YES_NO.contains(w)
                    && !Set.of("many", "much", "kind", "colour", "color").contains(w)) {
                asked.add(w);
            }
        }
        // "is tess a mog?" after "all mogs are tall ." "tess is tall .": the rule turned round.
        if (yesNo && nameAt + 2 < n - 1 && Set.of("a", "an").contains(q.get(nameAt + 1)) && saidOf.containsKey(name)) {
            String kind = q.get(nameAt + 2);
            for (KindRule r : rules) {
                if (ofKind(r.kind(), kind) && r.how().equals("all") || ofKind(r.kind(), kind) && r.how().equals("every")) {
                    if (r.does().size() == 2 && saidOf.get(name).contains(r.does().get(1))) {
                        return said("i don't know , " + name + " is " + r.does().get(1) + " , but not all " + r.does().get(1)
                                + " things are " + r.kind() + " .");
                    }
                }
            }
            return null;
        }
        if (asked.isEmpty()) {
            return null;
        }
        List<String> kinds = new ArrayList<>(kindOf.getOrDefault(name, List.of()));
        List<String> because = new ArrayList<>();
        for (int step = 0; step < 3; step++) {
            for (KindRule r : rules) {
                boolean ours = kinds.stream().anyMatch(kd -> ofKind(r.kind(), kd) || r.kind().equals(kd));
                if (!ours) {
                    continue;
                }
                boolean all = asked.stream().allMatch(a -> r.does().stream().anyMatch(d -> sameDoing(a, d) || ofKind(a, d)));
                boolean some = asked.stream().anyMatch(a -> r.does().stream().anyMatch(d -> sameDoing(a, d) || ofKind(a, d)));
                String line = String.join(" ", r.line());
                if (yesNo && all) {
                    String kind = kindOf.get(name).get(0);
                    String why = String.join(" , ", because) + (because.isEmpty() ? "" : " , ") + line + " , and " + name + " is "
                            + ("aeiou".indexOf(kind.charAt(0)) >= 0 ? "an " : "a ") + kind + " .";
                    return switch (r.how()) {
                        case "no" -> said("no , " + why);
                        case "some" -> said("maybe , " + line + " , but i don't know if " + name + " is one of them .");
                        default -> said("yes , " + why);
                    };
                }
                if (!yesNo && some && !r.how().equals("no") && !r.how().equals("some") && !r.does().isEmpty()) {
                    // "ari lives in caves ."
                    List<String> out = new ArrayList<>(List.of(name));
                    List<String> does = new ArrayList<>(r.does());
                    boolean many = r.how().equals("all");
                    out.add(many ? ofOne(does.get(0)) : does.get(0));
                    out.addAll(does.subList(1, does.size()));
                    out.add(".");
                    return Arrays.stream(String.join(" ", out).split(" ")).mapToInt(v::id).toArray();
                }
                if (r.does().size() == 2 && Set.of("are", "is").contains(r.does().get(0)) && !r.how().equals("no")
                        && !r.how().equals("some") && !kinds.contains(r.does().get(1) + " things")) {
                    // "all fips are blue ." makes kip one of the blue things.
                    kinds.add(r.does().get(1) + " things");
                    because.add(line);
                }
            }
        }
        // One not of the kind: what is so of the kind says nothing of it.
        for (String kd : notKindOf.getOrDefault(name, List.of())) {
            for (KindRule r : rules) {
                if (ofKind(r.kind(), kd) && asked.stream().allMatch(a -> r.does().stream().anyMatch(d -> sameDoing(a, d)))) {
                    return said("i don't know , " + String.join(" ", r.line()) + " , but " + name + " is not "
                            + ("aeiou".indexOf(kd.charAt(0)) >= 0 ? "an " : "a ") + kd + " .");
                }
            }
        }
        return null;
    }

    /** "rains" and "raining", "rang" and "rings": the same doing. */
    private boolean sameDoing(String a, String b) {
        if (sameWord(a, b) || stem(a).equals(stem(b))) {
            return true;
        }
        Set<String> forms = plain(a);
        return plain(b).stream().anyMatch(forms::contains);
    }

    /** The forms a doing may have without its ending: "died" "die", "di"; "raining" "rain". */
    private static Set<String> plain(String w) {
        String p = IRREGULAR.getOrDefault(w, w);
        Set<String> forms = new java.util.HashSet<>();
        forms.add(p);
        for (String end : new String[] {"ing", "es", "s", "ed", "d"}) {
            if (p.endsWith(end) && p.length() > end.length() + 1) {
                forms.add(p.substring(0, p.length() - end.length()));
            }
        }
        if (p.endsWith("ing") && p.length() > 5) {
            // "coming" "come"
            forms.add(p.substring(0, p.length() - 3) + "e");
        }
        return forms;
    }

    /** What follows from numbers told lately ({@link NumberSense}), in words it knows; null if nothing does. */
    private int[] numbersTold(int[] heard) {
        Vocabulary v = language.vocabulary;
        List<List<String>> told = new ArrayList<>();
        for (int k = TOLD_LINES; k >= 2; k--) {
            if (k > heardLinesNext) {
                continue;
            }
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            if (line == null || line.length == 0 || line[line.length - 1] == v.find("?")) {
                continue;
            }
            told.add(wordsOf(line));
        }
        List<String> said = NumberSense.answer(told, wordsOf(heard));
        if (said == null) {
            return null;
        }
        int[] line = said.stream().mapToInt(v::find).toArray();
        return Arrays.stream(line).anyMatch(w -> w < 0) ? null : line;
    }

    private List<String> wordsOf(int[] line) {
        List<String> words = new ArrayList<>();
        for (int w : line) {
            words.add(w < 0 ? "" : language.vocabulary.word(w));
        }
        return words;
    }

    private boolean generalAsk;

    /** Who "he", "she" and "they" stand for, and what "it" does; "him", "her" and "them" too. */
    private static final Set<String> SOMEONE = Set.of("he", "she", "him", "her"), SOME = Set.of("they", "them"),
            PRONOUNS = Set.of("he", "she", "him", "her", "they", "them", "it");
    private static final Set<String> STANDS_FOR = Set.of("he", "she", "him", "her", "they", "them", "it", "he's",
            "she's", "they're", "it's", "his", "their", "its");
    /** Words around "it" that make it stand for nothing ("what time is it?", "is it raining?"). */
    private static final Set<String> IT_OF_NOTHING = Set.of("time", "day", "raining", "snowing", "sunny", "stormy",
            "night", "morning", "evening", "afternoon", "noon", "late", "early", "cold", "hot", "warm", "dark", "light",
            "safe", "going", "outside", "far", "windy", "cloudy", "foggy", "true", "possible", "worth", "fine", "okay");
    /** Words of greeting and small talk, which tell of no one. */
    private static final Set<String> SMALL_TALK = Set.of("hello", "hi", "hey", "good", "morning", "evening",
            "afternoon", "night", "day", "how", "are", "doing", "fine", "well", "thanks", "thank", "okay", "ok", "yes",
            "no", "yeah", "nice", "great", "bye", "see", "later", "what's", "up", "name", "sir", "friend", "again",
            "sure", "alright", "what", "who", "where", "why", "am", "i'm", "you're", "your", "my", "greetings", "howdy");
    /** Words that end who a line tells of ("the old miller went ..."). */
    private static final Set<String> AFTER_WHO = Set.of("is", "are", "was", "were", "has", "have", "had", "can", "will",
            "did", "does", "do", "went", "came", "ran", "saw", "took", "lost", "found", "left", "made", "said", "got",
            "gave", "fell", "sat", "ate", "flew", "swam", "sold", "bought", "caught", "wrote", "told", "in", "on", "at",
            "to", "of", "with", "by", "and", ",", ".", "?", "!", "'s", "isn't", "wasn't", "won't", "can't", "likes", "lives");
    /** Words for more than one that don't end in "s". */
    private static final Set<String> MANY_OF_THEM = Set.of("children", "people", "men", "women", "folk", "geese", "mice",
            "sheep", "cattle", "police", "oxen");
    private static final Set<String> WHOSE = Set.of("the", "a", "an", "my", "your", "his", "her", "our", "their");
    /** What it asks back when a question's "he" or "it" stands for nothing told. */
    private String unclear;

    /**
     * A question about "he", "she", "it" or "they" asked of what they stand for: the one the
     * latest line that tells of such a one names ("tom went to the mill ." makes "where is he?"
     * "where is tom ?"). An empty line when nothing was told of anyone at all, so it should ask
     * who is meant ("who do you mean ?", "what do you mean ?"); null when the question has no such
     * word or what it stands for can't be told.
     */
    private int[] ofWhoIsMeant(int[] heard) {
        Vocabulary v = language.vocabulary;
        List<String> w = wordsOf(heard);
        int n = w.size();
        if (n < 3 || n > TOLD_QUESTION_WORDS || !w.get(n - 1).equals("?")) {
            return null;
        }
        int at = -1;
        for (int i = 0; i < n - 1; i++) {
            String word = w.get(i);
            boolean possessive = word.equals("her") && i + 2 < n && !AFTER_WHO.contains(w.get(i + 1));
            if (PRONOUNS.contains(word) && !possessive) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return null;
        }
        String pronoun = w.get(at);
        if (pronoun.equals("it") && w.stream().anyMatch(IT_OF_NOTHING::contains)) {
            return null;
        }
        for (int i = 1; i < at; i++) {
            if (heard[i] >= 0 && thingWord(heard[i]) && !SMALL_TALK.contains(w.get(i)) && !PRONOUNS.contains(w.get(i))
                    && BEFORE_A_THING.contains(w.get(i - 1))) {
                // "what does the mayor do when he is angry?": "he" is the mayor.
                return null;
            }
        }
        boolean many = SOME.contains(pronoun), person = SOMEONE.contains(pronoun);
        boolean toldOfAny = false;
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            if (line == null) {
                continue;
            }
            for (int x : line) {
                toldOfAny |= x >= 0 && thingWord(x) && !SMALL_TALK.contains(v.word(x));
            }
            int[] who = whoLineTellsOf(line);
            if (who == null && (line.length > TOLD_LONGEST || saysAny(line, STANDS_FOR))) {
                // A long line, or one that itself says "he" of someone it doesn't name: who that
                // is can't be told for sure.
                return null;
            }
            if (who == null) {
                continue;
            }
            String head = v.word(who[who.length - 1]);
            boolean plural = (head.endsWith("s") && !head.endsWith("ss") || MANY_OF_THEM.contains(head)) && who.length > 1;
            boolean thing = WHOSE.contains(v.word(who[0]));
            if (many ? plural : !plural && (person || thing)) {
                int[] meant = new int[heard.length - 1 + who.length];
                System.arraycopy(heard, 0, meant, 0, at);
                System.arraycopy(who, 0, meant, at, who.length);
                System.arraycopy(heard, at + 1, meant, at + who.length, heard.length - at - 1);
                return meant;
            }
        }
        if (toldOfAny || toldAround && !around.isEmpty() && !person) {
            return null;
        }
        unclear = pronoun.equals("it") ? "what do you mean ?" : "who do you mean ?";
        return new int[0];
    }

    /**
     * Who or what a line tells of, as the words that name it: "tom" in "tom went to the mill .",
     * "the old miller" in "the old miller sold his cart .", "tom" in "where is tom ?". Null for
     * a line about the speaker or the listener, or one that tells of no one.
     */
    private int[] whoLineTellsOf(int[] line) {
        Vocabulary v = language.vocabulary;
        int i = 0;
        while (i < line.length && line[i] >= 0 && (ASKING_WORDS.contains(v.word(line[i]))
                || Set.of("is", "are", "was", "were", "did", "does", "do", "can", "will", "has", "have").contains(v.word(line[i])))) {
            i++;
        }
        if (i >= line.length || line[i] < 0) {
            return null;
        }
        String first = v.word(line[i]);
        if (WHOSE.contains(first)) {
            int end = i + 1;
            while (end < line.length && end < i + 4 && line[end] >= 0 && !AFTER_WHO.contains(v.word(line[end]))) {
                end++;
            }
            return end > i + 1 ? Arrays.copyOfRange(line, i, end) : null;
        }
        if (!thingWord(line[i]) || SMALL_TALK.contains(first) || PRONOUNS.contains(first) || first.endsWith("ing")
                || i + 1 >= line.length || line[i + 1] < 0 || !AFTER_WHO.contains(v.word(line[i + 1]))) {
            return null;
        }
        return new int[] {line[i]};
    }

    private static final Set<String> SAY_AGAIN = Set.of("what ?", "huh ?", "pardon ?", "sorry ?", "come again ?",
            "say that again ?", "can you say that again ?", "could you say that again ?", "can you repeat that ?",
            "could you repeat that ?", "say that again .", "say it again .", "what was that ?");
    private static final Set<String> WHAT_SAID = Set.of("what did you say ?", "what did you just say ?", "what was that you said ?");

    /**
     * Its last line again, when it wasn't heard: "what?" says it again, "what did you say?" "i said
     * ..."; null if not asked or it said nothing yet.
     */
    private int[] saidAgain(int[] heard) {
        String asked = String.join(" ", wordsOf(heard));
        boolean again = SAY_AGAIN.contains(asked), what = WHAT_SAID.contains(asked);
        int[] last = saidNext == 0 ? null : said[(saidNext - 1) % said.length];
        if (!again && !what || last == null || last.length == 0) {
            return null;
        }
        return what ? join(said("i said"), last) : last;
    }

    private static final Set<String> NIGHT = Set.of("night", "midnight");
    /** Below this share of its health it feels hurt. */
    static final float HURT = 0.5f;

    /** The dangerous creature in sight, as "a zombie" or "two zombies"; null if none. */
    private String dangerSeen() {
        Vocabulary v = language.vocabulary;
        List<int[]> facts = knownToAll(v);
        for (Carried seen : around) {
            String kind = v.word(seen.name()[seen.name().length - 1]);
            for (int[] fact : facts) {
                List<String> f = wordsOf(fact);
                if ((f.contains(kind) || f.contains(kind + "s")) && f.contains("dangerous") && !f.contains("not")) {
                    String name = String.join(" ", wordsOf(seen.name()));
                    return seen.count() == 1 ? ("aeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name
                        : NUMBERS.get(Math.min(seen.count(), 20)) + " " + name + "s";
                }
            }
        }
        return null;
    }

    /**
     * How it feels, from its body and what it sees: hurt when its health is low, scared with a
     * dangerous creature in sight, tired at night, fine otherwise. Answers "how do you feel?",
     * "are you hurt / scared / tired?" and "why are you ...?"; "how are you?" only when something
     * is wrong, as the greeting it usually is gets a greeting back. Null if not asked.
     */
    private int[] feelingNow(int[] heard) {
        String asked = String.join(" ", wordsOf(heard));
        float health = input[IN_FEEL + FeelChannel.HEALTH.ordinal()];
        boolean hurt = health > 0f && health < HURT;
        String danger = toldAround ? dangerSeen() : null;
        boolean tired = timeNow != null && NIGHT.contains(timeNow);
        String state = hurt ? "hurt" : danger != null ? "scared" : tired ? "tired" : null;
        String because = hurt ? "i need to rest" : danger != null ? "there " + (danger.startsWith("a ") || danger.startsWith("an ")
                ? "is " : "are ") + danger + " around" : tired ? "it's late at night" : null;
        String line = null;
        if (Set.of("how do you feel ?", "how are you feeling ?", "how do you feel today ?", "how are you feeling today ?",
                "are you okay ?", "are you ok ?", "are you alright ?", "are you all right ?").contains(asked)) {
            line = state == null ? (asked.startsWith("are") ? "yes , i'm fine ." : "i feel good .")
                : (asked.startsWith("are") ? "no , " : "") + "i'm " + state + " , " + because + " .";
        } else if (asked.equals("how are you ?") && state != null) {
            line = "not great , i'm " + state + " , " + because + " .";
        } else if (asked.matches("are you (hurt|scared|afraid|tired) \\?")) {
            String q = asked.split(" ")[2];
            String st = q.equals("afraid") ? "scared" : q;
            line = st.equals(state) || st.equals("scared") && danger != null || st.equals("tired") && tired || st.equals("hurt") && hurt
                ? "yes , " + (st.equals("hurt") ? "i got hurt ." : st.equals("scared") ? "there " + (danger.startsWith("a") ? "is " : "are ") + danger + " around ." : "it's late at night .")
                : "no , i'm not " + q + " .";
        } else if (asked.matches("why are you (hurt|scared|afraid|tired) \\?")) {
            String q = asked.split(" ")[3];
            String st = q.equals("afraid") ? "scared" : q;
            line = st.equals("scared") && danger != null ? "because there " + (danger.startsWith("a") ? "is " : "are ") + danger + " around ."
                : st.equals("tired") && tired ? "because it's late at night ." : st.equals("hurt") && hurt ? "i got hurt , i need to rest ."
                : "i'm not " + q + " .";
        }
        if (line == null) {
            return null;
        }
        int[] said = said(line);
        return Arrays.stream(said).anyMatch(w -> w < 0) ? null : said;
    }

    /**
     * Whether it is safe out, from what it sees and knows: monsters around that it knows are
     * dangerous ("creepers are dangerous ."), the night, or a storm. "is it safe to go outside?"
     * "no , there are two zombies around ."; "should i go out?" "yes , it looks safe .". Null if
     * it is no such question or the game told it nothing of the world around.
     */
    private int[] isItSafe(int[] heard) {
        Vocabulary v = language.vocabulary;
        if (heard.length < 3 || heard[0] < 0 || !toldAround && timeNow == null && weatherNow == null) {
            return null;
        }
        String asked = String.join(" ", wordsOf(heard));
        int[] answer = safeNow(asked);
        return answer == null || Arrays.stream(answer).anyMatch(w -> w < 0) ? null : answer;
    }

    private int[] safeNow(String asked) {
        Vocabulary v = language.vocabulary;
        boolean safeAsked = asked.startsWith("is it safe") || asked.matches("(should|can) (i|we) go (out|outside)( now| today)? \\?");
        if (!safeAsked) {
            return null;
        }
        List<int[]> facts = knownToAll(v);
        for (Carried seen : around) {
            String kind = v.word(seen.name()[seen.name().length - 1]);
            boolean dangerous = false;
            for (int[] fact : facts) {
                List<String> f = wordsOf(fact);
                if ((f.contains(kind) || f.contains(kind + "s")) && f.contains("dangerous") && !f.contains("not")) {
                    dangerous = true;
                }
            }
            if (dangerous) {
                String many = seen.count() == 1 ? "there is a" : "there are " + NUMBERS.get(Math.min(seen.count(), 20));
                String name = String.join(" ", wordsOf(seen.name())) + (seen.count() == 1 ? "" : "s");
                return said("no , " + many + " " + name + " around .");
            }
        }
        if (weatherNow != null && weatherNow.equals("stormy")) {
            return said("no , there's a storm .");
        }
        if (timeNow != null && NIGHT.contains(timeNow)) {
            return said("no , it's night , and monsters come out at night .");
        }
        return said("yes , it looks safe .");
    }

    /**
     * "how do i get iron?" or "what should i do if i am hungry?" as asked of anyone, "how do you
     * get iron ?"; null if it is no such question.
     */
    private int[] askedOfAnyone(int[] heard) {
        Vocabulary v = language.vocabulary;
        if (heard.length < 4 || heard[0] < 0 || heard[1] < 0 || heard[2] < 0) {
            return null;
        }
        String first = v.word(heard[0]), second = v.word(heard[1]), third = v.word(heard[2]);
        boolean how = first.equals("how") && Set.of("do", "can", "should").contains(second) && third.equals("i");
        boolean should = first.equals("what") && Set.of("should", "can", "do").contains(second) && third.equals("i")
                && (contains(heard, v.find("if")) || contains(heard, v.find("when")));
        if (first.equals("what") && Set.of("do", "does").contains(second) && third.equals("you") && heard.length > 5
                && heard[3] == v.find("need") && heard[4] == v.find("to")) {
            // "what do you need to sleep at night?" asks what anyone needs.
            return heard;
        }
        if (!how && !should) {
            return null;
        }
        int[] you = heard.clone();
        for (int i = 0; i < you.length; i++) {
            String w = you[i] < 0 ? "" : v.word(you[i]);
            String to = switch (w) {
                case "i", "me" -> "you";
                case "my" -> "your";
                case "am" -> "are";
                case "i'm" -> "you're";
                default -> null;
            };
            if (to != null) {
                you[i] = v.find(to);
            }
        }
        return Arrays.stream(you).anyMatch(w -> w < 0) ? null : you;
    }

    /**
     * Which of a few things is (or is not) of a kind, from what it knows of each: "which one is
     * not a fruit, an apple, a pear or a stone?" "a stone is not a fruit ."; null if it is no such
     * question or what it knows doesn't single one out.
     */
    private int[] oddOneOut(int[] heard) {
        Vocabulary v = language.vocabulary;
        int comma = indexOf(heard, v.find(","), 0), or = indexOf(heard, v.find("or"), 0);
        if (heard.length < 10 || heard[0] < 0 || !Set.of("which", "what").contains(v.word(heard[0])) || comma < 4 || or < comma) {
            return null;
        }
        int is = indexOf(heard, v.find("is"), 1);
        if (is < 1 || is > comma) {
            return null;
        }
        boolean not = heard[is + 1] == v.find("not");
        int[] kind = Arrays.copyOfRange(heard, is + (not ? 2 : 1), comma);
        int kindWord = kind.length == 0 ? -1 : kind[kind.length - 1];
        if (kindWord < 0 || !thingWord(kindWord)) {
            return null;
        }
        // The things: "an apple", "a pear", "a stone".
        List<int[]> things = new ArrayList<>();
        int from = comma + 1;
        for (int i = comma + 1; i < heard.length; i++) {
            if (heard[i] == v.find(",") || heard[i] == v.find("or") || i == heard.length - 1) {
                if (i > from) {
                    things.add(Arrays.copyOfRange(heard, from, i));
                }
                from = i + 1;
            }
        }
        if (things.size() < 2) {
            return null;
        }
        List<int[]> facts = knownToAll(v);
        int[] single = null;
        int fits = 0, knownOf = 0;
        for (int[] thing : things) {
            int head = thing[thing.length - 1];
            Boolean of = null;
            for (int[] fact : facts) {
                int at = indexOf(fact, head, 0);
                int verb = indexOf(fact, v.find("is"), 0);
                int k = indexOf(fact, kindWord, 0);
                if (at >= 0 && verb > at && k > verb) {
                    boolean no = false;
                    for (int j = verb; j < k; j++) {
                        no |= fact[j] == v.find("not");
                    }
                    if (indexOf(fact, v.find("not"), verb) > 0 && indexOf(fact, v.find("not"), verb) < k) {
                        no = true;
                    }
                    of = !no;
                    break;
                }
            }
            if (of == null) {
                continue;
            }
            knownOf++;
            if (of != not) {
                fits++;
                single = thing;
            }
        }
        if (not && fits == 0 && knownOf == things.size() - 1) {
            // "a cow is an animal ." "a pig is an animal .": the stone is the one that is not.
            for (int[] thing : things) {
                int head = thing[thing.length - 1];
                boolean known = false;
                for (int[] fact : facts) {
                    int at = indexOf(fact, head, 0), verb = indexOf(fact, v.find("is"), 0);
                    known |= at >= 0 && verb > at && indexOf(fact, kindWord, 0) > verb;
                }
                if (!known) {
                    single = thing;
                    fits = 1;
                }
            }
        }
        if (fits != 1 || knownOf < things.size() - 1) {
            return null;
        }
        int[] said = join(single, said("is"));
        if (not) {
            said = join(said, said("not"));
        }
        return join(said, kind, said("."));
    }

    /** The answer when nothing it knows tells what is asked. */
    private void dontKnow() {
        toldLine = Arrays.stream(new String[] {"i", "don't", "know", "."}).mapToInt(language.vocabulary::find).toArray();
        if (Arrays.stream(toldLine).anyMatch(w -> w < 0)) {
            toldLine = null;
            return;
        }
        toldLineAsHeard = null;
        toldInOwnWords = true;
        toldSure = true;
    }

    private static final Set<String> NOT_A_DOING = Set.of("is", "are", "was", "were", "has", "have", "had", "do", "does",
            "did", "can", "will", "would", "should", "could", "'s");
    private static final Set<String> WHERE_WORDS = Set.of("to", "into", "onto", "in", "on", "at", "inside", "under", "behind",
            "from", "near", "by", "beside", "above", "below", "over", "home", "upstairs", "downstairs", "outside", "away",
            "here", "there", "north", "south", "east", "west", "left", "right", "underground", "deep", "everywhere",
            "nowhere", "somewhere", "anywhere", "far", "close", "nearby", "down", "up", "around", "across", "through",
            "took", "stole", "grabbed", "carried", "ate");
    private static final Set<String> WHEN_WORDS = Set.of("in", "on", "at", "after", "before", "ago", "yesterday", "today",
            "tomorrow", "morning", "noon", "evening", "night", "tonight", "week", "year", "spring", "summer", "autumn",
            "winter", "soon", "later", "when", "since", "until", "for", "day", "days", "weeks", "hour", "hours", "month",
            "months", "years", "minutes", "while");

    /**
     * Whether a told line gives what a question asks for, not only what it asks of: a colour for
     * "what colour", a name for "what is its name", a count for "how many", a place for "where",
     * a time for "when", and the doing itself for "who bought the sword?" or "what did tom buy?".
     * True when it can't tell.
     */
    private boolean answersAsked(int[] heard, int[] told) {
        Vocabulary v = language.vocabulary;
        if (heard.length < 3 || heard[0] < 0 || heard[1] < 0) {
            return true;
        }
        String first = v.word(heard[0]), second = v.word(heard[1]);
        String doing = heard[heard.length - 2] < 0 ? "" : v.word(heard[heard.length - 2]);
        int than = indexOf(heard, v.find("than"), 1);
        if (than > 1 && heard[than - 1] >= 0) {
            // "is a bear bigger than a rabbit?" is answered by a comparison.
            return contains(told, v.find("than")) || saidIn(told, heard[than - 1]);
        }
        if (saysAny(heard, COLOURS_ASKED)) {
            // And of the thing asked of: "pia has a red hat ." is no answer to "what colour is
            // my cat?".
            boolean of = false, some = false;
            for (int w : heard) {
                if (w >= 0 && thingWord(w) && !COLOURS_ASKED.contains(v.word(w)) && !ASKING_WORDS.contains(v.word(w))
                        && !NOT_A_DOING.contains(v.word(w)) && !Set.of("my", "your", "what's").contains(v.word(w))) {
                    some = true;
                    of |= saidIn(told, w);
                }
            }
            return saysAny(told, COLOURS) && (of || !some);
        }
        if (contains(heard, v.find("name")) && Set.of("what", "what's").contains(first)) {
            // "my dog is called rex .", "i have a dog named rex .", "hi, i'm josh ."
            ExchangeMemory memory = ExchangeMemory.shared();
            ToneLinks tones = ToneLinks.shared();
            for (int w : told) {
                if (w >= 0 && (Set.of("name", "called", "named").contains(v.word(w)) || memory != null && tones != null
                        && memory.weight(w) == 0f && !tones.common(w) && Character.isLetter(v.word(w).charAt(0)))) {
                    return true;
                }
            }
            return namesIn(told, new ArrayList<>());
        }
        if (first.equals("how") && Set.of("many", "old", "much", "long", "far").contains(second)) {
            for (int w : told) {
                if (w >= 0 && (number(v.word(w)) >= 0 || AMOUNTS.contains(v.word(w)))) {
                    return true;
                }
            }
            // "it is very old ."; "for a week ."
            return second.equals("old") ? saysAny(told, Set.of("old", "young", "new", "years", "baby"))
                : Set.of("long", "far").contains(second) && (saysAny(told, WHEN_WORDS) || saysAny(told, Set.of("miles", "mile", "far", "close", "near")));
        }
        if (first.equals("how") && SIZES.contains(second)) {
            // "how big is my uncle's farm?"
            for (int w : told) {
                if (w >= 0 && (number(v.word(w)) >= 0 || SIZES.contains(v.word(w)))) {
                    return true;
                }
            }
            return false;
        }
        if (first.equals("where")) {
            return saysAny(told, WHERE_WORDS);
        }
        if (first.equals("what") && Set.of("is", "was", "are", "were", "'s").contains(second) && heard.length > 3
                && heard[2] >= 0 && PLACE.contains(v.word(heard[2]))) {
            // "what was in the chest?"
            return saysAny(told, PLACE);
        }
        if (first.equals("when")) {
            return saysAny(told, WHEN_WORDS);
        }
        if (first.equals("who") && !NOT_A_DOING.contains(second)) {
            return saidIn(told, heard[1]);
        }
        if (first.equals("what") && Set.of("did", "does", "do").contains(second) && !NOT_A_DOING.contains(doing)
                && heard[heard.length - 2] >= 0 && thingWord(heard[heard.length - 2])) {
            return saidIn(told, heard[heard.length - 2]);
        }
        return true;
    }

    private static final Set<String> SIZES = Set.of("big", "small", "tall", "short", "deep", "wide", "heavy", "high",
            "large", "little", "huge", "tiny", "long");
    private static final Set<String> AMOUNTS = Set.of("no", "none", "lots", "few", "some", "many", "thirty", "forty", "fifty",
            "sixty", "seventy", "eighty", "ninety", "hundred", "hundreds", "thousand", "dozen", "half", "all", "both");
    private static final Set<String> WHY_WORDS = Set.of("because", "so", "if", "since", "cause", "after", "when", "from", "by", "due",
            "thanks", "caused");
    private static final Set<String> COLOURS_ASKED = Set.of("colour", "color", "colours", "colors");

    /**
     * Whether a question asks of the asker's own things ("where is my hat?") or of someone by a
     * name no one said here ("where does lena live?"), which only being told would tell.
     */
    private boolean unheardOf(int[] heard, ExchangeMemory memory, ToneLinks tones) {
        Vocabulary v = language.vocabulary;
        if (heard.length < 3 || heard[0] < 0 || !ASKING_WORDS.contains(v.word(heard[0])) && !v.word(heard[0]).equals("what's")
                || saysAny(heard, Set.of("you", "your", "you're", "think", "should", "can", "i"))) {
            return false;
        }
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            if (Arrays.equals(heardLines[(heardLinesNext - k) % heardLines.length], heard)) {
                // Asked before, maybe answered by someone else ("the password is pumpkin.").
                return false;
            }
        }
        if (contains(heard, v.find("my"))) {
            return true;
        }
        // "who bought the sword?" after "the blacksmith made a sword .": of the sword, but not
        // what was told of it. A name given to something ("a dog called rex") is of a kind of
        // thing, and what is so of the kind may answer.
        int[][] names = namesGiven();
        for (int i = 1; i < heard.length; i++) {
            final int named = heard[i];
            if (Arrays.stream(names).anyMatch(g -> g[0] == named)) {
                continue;
            }
            int w = heard[i];
            if (w < 0 || heard[i - 1] < 0 || !Set.of("the", "a", "an", "his", "her", "their", "does", "did", "is", "was")
                    .contains(v.word(heard[i - 1])) || !thingWord(w) || NOT_A_DOING.contains(v.word(w))) {
                continue;
            }
            for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
                int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
                if (line != null && line.length > 0 && line[line.length - 1] != v.find("?") && saidIn(line, w)) {
                    return true;
                }
            }
            for (int[] line : talk.toldLines()) {
                // Or told of longer ago ("the shed is behind the mill .").
                if (line.length > 0 && line[line.length - 1] != v.find("?") && saidIn(line, w)) {
                    return true;
                }
            }
        }
        for (int w : heard) {
            if (w >= 0 && memory != null && memory.weight(w) == 0f && tones != null && !tones.common(w)
                    && Character.isLetter(v.word(w).charAt(0)) && Arrays.stream(names).noneMatch(g -> g[0] == w)) {
                return true;
            }
        }
        return false;
    }

    private static final Set<String> WHAT_THEY_DO = Set.of("what do i do ?", "what do i do for work ?",
            "what do i do for a living ?", "what is my job ?", "what's my job ?", "what is my work ?");
    /** Not what someone does: "i have a cat .", "i like apples ." */
    private static final Set<String> NOT_DOING = Set.of("have", "like", "love", "want", "need", "think", "feel",
            "see", "hope", "know", "guess", "mean", "am", "can", "will", "don't", "can't");

    /**
     * "what do i do?" (for work): the latest line the asker told of what they do now, "i run the
     * bakery ." or "i'm a baker ."; not what they did ("i ate two apples .") or have. Null if not
     * asked or not told.
     */
    private int[] whatTheyDo(int[] heard) {
        Vocabulary v = language.vocabulary;
        StringBuilder asked = new StringBuilder();
        for (int w : heard) {
            asked.append(asked.length() == 0 ? "" : " ").append(w < 0 ? "" : v.word(w));
        }
        if (!WHAT_THEY_DO.contains(asked.toString())) {
            return null;
        }
        for (int k = 2; k <= TOLD_LINES && k <= heardLinesNext; k++) {
            int[] line = heardLines[(heardLinesNext - k) % heardLines.length];
            if (line == null || line.length < 3 || line[0] < 0 || line[1] < 0 || line[line.length - 1] == v.find("?")) {
                continue;
            }
            String who = v.word(line[0]), doing = v.word(line[1]);
            boolean now = !doing.endsWith("ed") && !NOT_DOING.contains(doing)
                    && (!IRREGULAR.containsKey(doing) || IRREGULAR.get(doing).equals(doing));
            if (who.equals("i'm") && Set.of("a", "an", "the").contains(doing) || who.equals("i") && now) {
                return line;
            }
        }
        return null;
    }

    private int[] ofAllSuch(int[] heard, int[] told) {
        Vocabulary v = language.vocabulary;
        int my = indexOf(heard, v.find("my"), 0);
        if (my < 0 || my + 2 >= heard.length || heard[my + 1] < 0 || !saidIn(told, heard[my + 1])) {
            return null;
        }
        boolean asks = false;
        for (int i = 0; i < heard.length - 1; i++) {
            String w = heard[i] < 0 ? "" : v.word(heard[i]);
            if (i == my || i == my + 1 || w.isEmpty() || ASKING_WORDS.contains(w) || !thingWord(heard[i])
                    || Set.of("do", "does", "did", "is", "are", "was", "were", "have", "has", "what's", "where's", "who's").contains(w)) {
                continue;
            }
            if (saidIn(told, heard[i])) {
                return null;
            }
            asks = true;
        }
        return asks ? withoutAt(heard, my) : null;
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
                if (sameWord(a, b) || stem(a).equals(stem(b))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A word without its ending, so forms of it match: "dried" and "dry", "escaped" and "escape",
     * "keeps" and "keep", "mayor's" and "mayor".
     */
    /** The past forms of common verbs that don't end in -ed, after their plain form. */
    private static final java.util.Map<String, String> IRREGULAR = irregular(
            "be was were been", "begin began begun", "break broke broken", "bring brought", "build built",
            "buy bought", "catch caught", "choose chose chosen", "come came", "cut cut", "dig dug",
            "do did done", "draw drew drawn", "drink drank drunk", "drive drove driven", "eat ate eaten",
            "fall fell fallen", "feed fed", "feel felt", "fight fought", "find found", "fly flew flown",
            "forget forgot forgotten", "freeze froze frozen", "get got gotten", "give gave given",
            "go went gone", "grow grew grown", "hang hung", "have had has", "hear heard", "hide hid hidden",
            "hit hit", "hold held", "hurt hurt", "keep kept", "know knew known", "lay laid", "lead led",
            "leave left", "lend lent", "light lit", "lose lost", "make made", "mean meant",
            "meet met", "pay paid", "put put", "read read", "ride rode ridden", "ring rang rung",
            "rise rose risen", "run ran", "say said", "see saw seen", "sell sold", "send sent", "set set",
            "shake shook shaken", "shine shone", "shoot shot", "show showed shown", "shut shut",
            "sing sang sung", "sink sank sunk", "sit sat", "sleep slept", "slide slid", "speak spoke spoken",
            "spend spent", "stand stood", "steal stole stolen", "stick stuck", "sting stung",
            "strike struck", "swim swam swum", "swing swung", "take took taken", "teach taught",
            "tear tore torn", "tell told", "use used", "think thought", "throw threw thrown", "wake woke woken",
            "wear wore worn", "win won", "write wrote written");

    private static java.util.Map<String, String> irregular(String... verbs) {
        java.util.Map<String, String> plain = new java.util.HashMap<>();
        for (String v : verbs) {
            String[] forms = v.split(" ");
            for (int i = 1; i < forms.length; i++) {
                plain.putIfAbsent(forms[i], forms[0]);
            }
        }
        return plain;
    }

    static String stem(String w) {
        String plain = IRREGULAR.get(w);
        if (plain != null) {
            w = plain;
        }
        if (w.endsWith("'s")) {
            w = w.substring(0, w.length() - 2);
        }
        if (w.endsWith("men") && w.length() > 4) {
            // "fishermen", "fisherman".
            w = w.substring(0, w.length() - 3) + "man";
        }
        if (w.endsWith("ves") && w.length() > 4 && !w.endsWith("oves")) {
            // "wolves", "wolf"; "knives", "knife".
            w = w.substring(0, w.length() - 3) + "f";
        }
        for (String end : new String[] {"ing", "ied", "ies", "ed", "es", "s"}) {
            if (w.endsWith(end) && w.length() - end.length() >= (end.startsWith("i") ? 2 : 3)) {
                w = w.substring(0, w.length() - end.length()) + (end.startsWith("i") ? "y" : "");
                break;
            }
        }
        return w.endsWith("e") && w.length() > 3 ? w.substring(0, w.length() - 1) : w;
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
            float drive = amp * Math.max(0f, babble[c]) * BABBLE_GAIN;
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
