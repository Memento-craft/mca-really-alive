package com.mementocraft.reallyalive.body;

import com.mementocraft.reallyalive.brain.WordCode;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;

/**
 * Books as a player has them: a book and quill is written in, a page at a time, and a book of
 * either kind is read. A villager writes a line the way a player types one onto the page, and
 * reads every line on every page, each as a line of its own.
 */
public final class Books {
    private Books() {}

    /** Whether this is a book and quill with room for another line. */
    public static boolean canWriteIn(ItemStack stack) {
        if (!stack.is(Items.WRITABLE_BOOK)) {
            return false;
        }
        WritableBookContent book = stack.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
        return book.pages().size() < WritableBookContent.MAX_PAGES
                || book.pages().get(book.pages().size() - 1).raw().length() < WritableBookContent.PAGE_EDIT_LENGTH - 64;
    }

    /** Writes a line at the end of the book: on the last page if it fits there, else on a new page. */
    public static void write(ItemStack stack, String line) {
        if (!canWriteIn(stack) || line.isBlank()) {
            return;
        }
        WritableBookContent book = stack.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
        List<Filterable<String>> pages = new ArrayList<>(book.pages());
        String last = pages.isEmpty() ? null : pages.get(pages.size() - 1).raw();
        if (last != null && last.length() + 1 + line.length() <= WritableBookContent.PAGE_EDIT_LENGTH) {
            pages.set(pages.size() - 1, Filterable.passThrough(last.isEmpty() ? line : last + "\n" + line));
        } else if (pages.size() < WritableBookContent.MAX_PAGES) {
            pages.add(Filterable.passThrough(line.substring(0, Math.min(line.length(), WritableBookContent.PAGE_EDIT_LENGTH))));
        }
        stack.set(DataComponents.WRITABLE_BOOK_CONTENT, book.withReplacedPages(pages));
    }

    /** Whether there is anything to read in it. */
    public static boolean hasText(ItemStack stack) {
        return !text(stack).isEmpty();
    }

    /** The book's lines, every page's in turn, as words; empty for anything that is no book. */
    public static List<List<String>> lines(ItemStack stack) {
        List<List<String>> out = new ArrayList<>();
        for (String page : text(stack)) {
            for (String line : page.split("\n")) {
                List<String> words = WordCode.tokenize(line);
                if (!words.isEmpty()) {
                    out.add(words);
                }
            }
        }
        return out;
    }

    /** Who signed the book, or null for a book and quill, which no one has signed. */
    public static String writer(ItemStack stack) {
        WrittenBookContent book = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return book == null || book.author().isBlank() ? null : book.author();
    }

    private static List<String> text(ItemStack stack) {
        WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writable != null) {
            return writable.getPages(false).filter(p -> !p.isBlank()).toList();
        }
        WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (written != null) {
            return written.getPages(false).stream().map(c -> c.getString()).filter(p -> !p.isBlank()).toList();
        }
        return List.of();
    }
}
