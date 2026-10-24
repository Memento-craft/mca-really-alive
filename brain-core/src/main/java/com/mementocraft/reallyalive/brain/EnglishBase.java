package com.mementocraft.reallyalive.brain;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The English every villager grows up with: a language area trained on millions of words of
 * everyday conversation (see tools/corpus), frozen and shared by all brains, which each add what
 * they hear in their own world on top.
 */
public final class EnglishBase {
    public static final String RESOURCE = "/reallyalive/english.lang.gz";
    /** Context cells in each villager's own sheet. */
    static final int OWN_CELL_BITS = 16;

    private static EnglishBase shared;
    private static boolean tried;

    final Vocabulary vocabulary;
    final LanguageArea.Sheet sheet;

    EnglishBase(Vocabulary vocabulary, LanguageArea.Sheet sheet) {
        this.vocabulary = vocabulary;
        this.sheet = sheet;
    }

    /** The shipped English, loaded once, or null if the mod was built without it. */
    public static synchronized EnglishBase shared() {
        if (!tried) {
            tried = true;
            try (InputStream in = EnglishBase.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    shared = read(in);
                }
            } catch (IOException e) {
                shared = null;
            }
        }
        return shared;
    }

    /** A new brain's language area: the shared English below, an empty sheet of its own on top. */
    public static LanguageArea newArea() {
        EnglishBase base = shared();
        if (base == null) {
            return new LanguageArea(OWN_CELL_BITS);
        }
        return new LanguageArea(new LanguageArea.Sheet(OWN_CELL_BITS), base.sheet, base.vocabulary.copy());
    }

    /** Freezes a trained area's sheet, keeping synapses grown at least minCount times, and saves it. */
    public static void write(LanguageArea trained, int minCount, OutputStream out) throws IOException {
        GZIPOutputStream zip = new GZIPOutputStream(new BufferedOutputStream(out));
        DataOutputStream o = new DataOutputStream(zip);
        trained.vocabulary.write(o, 0);
        trained.own.frozen(minCount).write(o);
        o.flush();
        zip.finish();
        zip.flush();
    }

    public static EnglishBase read(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)));
        Vocabulary v = new Vocabulary();
        v.readInto(d);
        return new EnglishBase(v, LanguageArea.Sheet.read(d));
    }

    public int words() {
        return vocabulary.size();
    }
}
