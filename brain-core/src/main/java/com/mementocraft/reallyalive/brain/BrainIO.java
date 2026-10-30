package com.mementocraft.reallyalive.brain;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Saves and loads a whole brain, every neuron and synapse included. */
public final class BrainIO {
    private static final int MAGIC = 0x4252414E; // "BRAN"
    private static final int VERSION = 7;

    public static void write(Brain b, DataOutput out) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        float[] genes = b.genome.toArray();
        out.writeInt(genes.length);
        for (float g : genes) {
            out.writeFloat(g);
        }
        out.writeLong(b.rng.state());
        out.writeBoolean(b.rng.hasSpare());
        out.writeFloat(b.rng.spare());
        out.writeFloat(b.maturity);
        out.writeFloat(b.babbleScale);
        out.writeFloat(b.dopamine);
        out.writeFloat(b.dopamineBaseline);
        out.writeFloat(b.surprise);
        out.writeLong(b.ticks);
        out.writeLong(b.resets);
        writeFloats(out, b.poolRates);
        writeFloats(out, b.babble);
        writeFloats(out, b.emotionRates);
        for (int s : b.decoder.state()) {
            out.writeInt(s);
        }

        out.writeInt(b.n);
        for (int i = 0; i < b.n; i++) {
            out.writeByte(b.lobe[i]);
            out.writeByte(b.role[i]);
            out.writeBoolean(b.inhibitory[i]);
            out.writeInt(b.inputSlot[i]);
            out.writeFloat(b.posX[i]);
            out.writeFloat(b.posY[i]);
            out.writeFloat(b.potential[i]);
            out.writeFloat(b.threshold[i]);
            out.writeFloat(b.trace[i]);
            out.writeFloat(b.fastRate[i]);
            out.writeFloat(b.slowRate[i]);
            out.writeFloat(b.health[i]);
            out.writeFloat(b.cement[i]);
            out.writeByte(b.refractory[i]);
            out.writeBoolean(b.fired[i]);
            SynapseList s = b.out[i];
            out.writeInt(s.size);
            for (int k = 0; k < s.size; k++) {
                out.writeInt(s.target[k]);
                out.writeFloat(s.weight[k]);
                out.writeFloat(s.elig[k]);
            }
        }
        for (int[] pool : b.motorPools) {
            for (int i : pool) {
                out.writeInt(i);
            }
        }
        for (int[] pool : b.emotionPools) {
            for (int i : pool) {
                out.writeInt(i);
            }
        }
        for (int[] pool : b.speechPools) {
            for (int i : pool) {
                out.writeInt(i);
            }
        }
        writeFloats(out, b.speechRates);
        writeFloats(out, b.speechBabble);
        out.writeInt(b.voiceCooldown);

        ForwardModel fm = b.forwardModel;
        for (int k = 0; k < fm.weights.length; k++) {
            for (int i = 0; i < b.n; i++) {
                out.writeFloat(fm.weights[k][i]);
            }
            out.writeFloat(fm.bias[k]);
            out.writeFloat(fm.prediction[k]);
        }
        out.writeBoolean(fm.hasPrediction);
        out.writeFloat(fm.surprise);

        Critic c = b.critic;
        for (int i = 0; i < b.n; i++) {
            out.writeFloat(c.weights[i]);
        }
        out.writeFloat(c.bias);
        out.writeFloat(c.lastValue);
        out.writeBoolean(c.hasLast);
        c.writeLast(out);
        writeLanguage(b, out);
        b.links.write(out, b.n);
        writeTalk(b, out);
    }

    public static Brain read(DataInput in) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new IOException("Not a brain file");
        }
        int version = in.readInt();
        if (version != VERSION && version != 6 && version != 5 && version != 4) {
            throw new IOException("Unsupported brain version " + version);
        }
        float[] genes = new float[in.readInt()];
        for (int i = 0; i < genes.length; i++) {
            genes[i] = in.readFloat();
        }
        long rngState = in.readLong();
        boolean hasSpare = in.readBoolean();
        float spare = in.readFloat();
        float maturity = in.readFloat();
        float babbleScale = in.readFloat();
        float dopamine = in.readFloat();
        float dopamineBaseline = in.readFloat();
        float surprise = in.readFloat();
        long ticks = in.readLong();
        long resets = in.readLong();
        float[] poolRates = readFloats(in, MotorChannel.COUNT);
        float[] babble = readFloats(in, MotorChannel.COUNT);
        float[] emotionRates = readFloats(in, SenseLayout.EMOTION_RECEPTORS);
        int[] decoderState = new int[7];
        for (int i = 0; i < decoderState.length; i++) {
            decoderState[i] = in.readInt();
        }

        int count = in.readInt();
        Brain b = new Brain(Genome.fromArray(genes), 1L, count + 64);
        b.rng.setState(rngState);
        b.rng.setSpare(hasSpare, spare);
        b.maturity = maturity;
        b.babbleScale = babbleScale;
        b.dopamine = dopamine;
        b.dopamineBaseline = dopamineBaseline;
        b.surprise = surprise;
        b.ticks = ticks;
        b.resets = resets;
        System.arraycopy(poolRates, 0, b.poolRates, 0, poolRates.length);
        System.arraycopy(babble, 0, b.babble, 0, babble.length);
        System.arraycopy(emotionRates, 0, b.emotionRates, 0, emotionRates.length);
        b.decoder.setState(decoderState);

        b.n = count;
        for (int i = 0; i < count; i++) {
            byte lobe = in.readByte();
            byte role = in.readByte();
            b.lobe[i] = lobe;
            b.role[i] = role;
            b.inhibitory[i] = in.readBoolean();
            b.inputSlot[i] = in.readInt();
            b.posX[i] = in.readFloat();
            b.posY[i] = in.readFloat();
            b.potential[i] = in.readFloat();
            b.threshold[i] = in.readFloat();
            b.trace[i] = in.readFloat();
            b.fastRate[i] = in.readFloat();
            b.slowRate[i] = in.readFloat();
            b.health[i] = in.readFloat();
            b.cement[i] = in.readFloat();
            b.refractory[i] = in.readByte();
            b.fired[i] = in.readBoolean();
            int size = in.readInt();
            SynapseList s = new SynapseList(size);
            for (int k = 0; k < size; k++) {
                s.add(in.readInt(), in.readFloat());
                s.elig[k] = in.readFloat();
            }
            b.out[i] = s;
            b.plastic[i] = true;
            b.index(i);
        }
        for (int i = 0; i < count; i++) {
            SynapseList s = b.out[i];
            for (int k = 0; k < s.size; k++) {
                b.inDegree[s.target[k]]++;
            }
        }
        for (int[] pool : b.motorPools) {
            for (int k = 0; k < pool.length; k++) {
                pool[k] = in.readInt();
            }
        }
        for (int[] pool : b.emotionPools) {
            for (int k = 0; k < pool.length; k++) {
                pool[k] = in.readInt();
            }
        }
        for (int[] pool : b.speechPools) {
            for (int k = 0; k < pool.length; k++) {
                pool[k] = in.readInt();
            }
        }
        System.arraycopy(readFloats(in, SenseLayout.WORD_FEATURES), 0, b.speechRates, 0, SenseLayout.WORD_FEATURES);
        System.arraycopy(readFloats(in, SenseLayout.WORD_FEATURES), 0, b.speechBabble, 0, SenseLayout.WORD_FEATURES);
        b.voiceCooldown = in.readInt();

        ForwardModel fm = b.forwardModel;
        fm.ensureCapacity(b.lobe.length);
        for (int k = 0; k < fm.weights.length; k++) {
            for (int i = 0; i < count; i++) {
                fm.weights[k][i] = in.readFloat();
            }
            fm.bias[k] = in.readFloat();
            fm.prediction[k] = in.readFloat();
        }
        fm.hasPrediction = in.readBoolean();
        fm.surprise = in.readFloat();

        Critic c = b.critic;
        c.ensureCapacity(b.lobe.length);
        for (int i = 0; i < count; i++) {
            c.weights[i] = in.readFloat();
        }
        c.bias = in.readFloat();
        c.lastValue = in.readFloat();
        c.hasLast = in.readBoolean();
        c.readLast(in);
        boolean sameEnglish = version < 5 || readLanguage(b, in);
        if (version >= 6) {
            b.links.read(in, count);
            if (!sameEnglish) {
                // Its word links are numbered against the English it was born with, too.
                b.links = new ConceptLinks();
                b.links.ensureNeurons(b.lobe.length);
            }
        } else {
            b.addMissingLobes();
        }
        if (version >= 7) {
            readTalk(b, in);
        }
        b.addMissingReceptors();
        return b;
    }

    /** Its remembered exchanges, as words, so they survive a change of the shipped English. */
    private static void writeTalk(Brain b, DataOutput out) throws IOException {
        ExchangeMemory.Own talk = b.talk;
        out.writeInt(talk.count);
        for (int i = 0; i < talk.count; i++) {
            for (int[] line : new int[][] {talk.cues[i], talk.answers[i]}) {
                out.writeShort(line.length);
                for (int w : line) {
                    out.writeUTF(b.language.vocabulary.word(w));
                }
            }
        }
    }

    private static void readTalk(Brain b, DataInput in) throws IOException {
        int count = in.readInt();
        ExchangeMemory memory = ExchangeMemory.shared();
        for (int i = 0; i < count; i++) {
            int[][] lines = new int[2][];
            for (int k = 0; k < 2; k++) {
                lines[k] = new int[in.readShort()];
                for (int j = 0; j < lines[k].length; j++) {
                    lines[k][j] = b.language.vocabulary.id(in.readUTF());
                }
            }
            if (memory != null) {
                b.talk.remember(lines[0], lines[1], memory);
            }
        }
    }

    private static void writeFloats(DataOutput out, float[] a) throws IOException {
        for (float f : a) {
            out.writeFloat(f);
        }
    }

    private static float[] readFloats(DataInput in, int count) throws IOException {
        float[] a = new float[count];
        for (int i = 0; i < count; i++) {
            a[i] = in.readFloat();
        }
        return a;
    }

    private BrainIO() {}

    /**
     * The language area: the words this brain learned beyond the shared English, and its own sheet.
     * The shared English itself ships with the mod and is not saved per brain.
     */
    private static void writeLanguage(Brain b, DataOutput out) throws IOException {
        EnglishBase base = EnglishBase.shared();
        int baseWords = base == null ? 0 : base.words();
        out.writeInt(baseWords);
        b.language.vocabulary.write(out, baseWords);
        b.language.own.write(out);
        out.writeFloat(b.replyUrge);
        out.writeInt(b.villagerLines);
        out.writeInt(b.lineWords);
        out.writeLong(b.voiceRng.state());
    }

    /** Returns false when the shipped English has changed, so what it learned on top was dropped. */
    private static boolean readLanguage(Brain b, DataInput in) throws IOException {
        EnglishBase base = EnglishBase.shared();
        int baseWords = in.readInt();
        Vocabulary v = base == null ? new Vocabulary() : base.vocabulary.copy();
        v.readInto(in);
        LanguageArea.Sheet own = LanguageArea.Sheet.read(in);
        // Its own synapses are numbered against the English it was born with; if the mod now ships
        // different English, what it learned no longer lines up and it starts its own sheet afresh.
        boolean same = baseWords == (base == null ? 0 : base.words());
        if (same) {
            b.language = new LanguageArea(own, base == null ? null : base.sheet, v);
        }
        b.replyUrge = in.readFloat();
        b.villagerLines = in.readInt();
        b.lineWords = in.readInt();
        b.voiceRng.setState(in.readLong());
        return same;
    }
}
