package com.mementocraft.reallyalive.sense;

import com.mementocraft.reallyalive.body.Hands;
import com.mementocraft.reallyalive.brain.SenseLayout;
import com.mementocraft.reallyalive.brain.ThingCode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Arrays;

/**
 * What the eyes see while a screen is open, and what a thing up close looks like. A screen covers
 * the view as it does a player's: the eye rays see its slots instead of the world, in a window
 * centred on the mouse pointer, so the centre of the eye is wherever the pointer
 * is, as the crosshair is in the world. A slot looks like a near, solid square; a slot holding
 * something also looks like a creature does, so the inborn orienting reflex pulls the pointer
 * towards things, as it pulls the gaze towards creatures, and a slot of the thing being worked
 * catches the eye as a ladder does.
 */
public final class ScreenSight {
    /**
     * The window the eyes see, in screen pixels: twice a usual screen's size, so the whole screen is
     * in view wherever the pointer is, as a player sees all of it while looking at one slot. With
     * one screen's size, a villager holding a stack in the bottom row could not see the crafting
     * grid at the top, and never carried anything there.
     */
    public static final float VIEW_W = 352f;
    public static final float VIEW_H = 332f;
    private static final int PANEL = 0xC6C6C6;
    private static final int SLOT = 0x8B8B8B;
    /** How strongly a slot of the thing being worked catches the eye. */
    private static final float WORK_SLOT = 1f;
    /** How brightly a slot that catches the eye shows in the cells around its own. */
    private static final float GLOW = 0.7f;
    /** How many cells out from its own a slot that catches the eye still shows. */
    private static final int GLOW_REACH = 2;
    /**
     * How much other things still catch the eye beside something just made: nothing. At 0.3, the
     * log left in the grid, right under the eye, held the pointer beside the planks it had made.
     */
    private static final float OUTSHONE = 0f;
    /** How much a thing already put into the thing being worked still catches the eye. */
    private static final float PUT_IN = 0.3f;
    /**
     * How much the grid still catches the eye, with a stack in hand, once something shows in the
     * result. With nothing, one plank set down and a button showing sent the hand straight off to
     * put the rest away and take the button: three planks in a row never came about.
     */
    private static final float MADE_GRID = 0.5f;
    /**
     * How much finer the middle of the eye sees than its edge. With even cells (22 by 33 pixels,
     * wider than a 16-pixel slot) the orienting reflex stopped once a slot was in the middle cell,
     * which left the pointer off the slot two times in three; a result showing with the hand free
     * had the pointer on it only 3% of the time. Here the middle cells are 3 to 7 pixels across,
     * as a real eye sees sharpest where it looks.
     */
    private static final float FOVEA = 2f;

    private ScreenSight() {}

    /**
     * A recipe remembered from a book, laid on the grid of the screen being worked: which grid
     * slot (by menu index) the picture showed what in.
     */
    public record RecipeHint(java.util.Map<Integer, net.minecraft.world.item.crafting.Ingredient> want) {
        /** Whether this slot is one the picture showed the carried thing in, still waiting for it. */
        boolean wants(Slot s, ItemStack carried) {
            var i = want.get(s.index);
            return i != null && i.test(carried) && !i.test(s.getItem());
        }

        /**
         * Whether this grid slot holds more of a thing than its own place needs while another
         * place the picture showed still waits for it: some of it belongs over there.
         */
        boolean spare(AbstractContainerMenu menu, Slot s) {
            var mine = want.get(s.index);
            return mine != null && s.getItem().getCount() > 1 && mine.test(s.getItem()) && needs(menu, s.getItem());
        }

        /** Whether this thing is one the picture showed and some grid place still waits for. */
        boolean needs(AbstractContainerMenu menu, ItemStack stack) {
            for (var e : want.entrySet()) {
                if (e.getValue().test(stack) && !e.getValue().test(menu.getSlot(e.getKey()).getItem())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * How much the places and things a remembered recipe did not show still catch the eye, against
     * those it did.
     */
    private static final float UNHINTED = 0.3f;

    public static void see(AbstractContainerMenu menu, float pointerX, float pointerY, float[] vision, float[] colour) {
        see(menu, pointerX, pointerY, vision, colour, null);
    }

    public static void see(AbstractContainerMenu menu, float pointerX, float pointerY, float[] vision, float[] colour, RecipeHint hint) {
        Arrays.fill(vision, 0f);
        Arrays.fill(colour, 0f);
        float[] b = Hands.bounds(menu);
        // What catches the eye depends on the hand: with nothing on the pointer, things to pick up;
        // with a stack on it, the places it could go in the thing being worked. Even a faint pull
        // of the other kind (a crafting grid's five slots against one stack of logs) kept the
        // pointer off the logs most of the time.
        ItemStack carried = menu.getCarried();
        // Something just made, sitting in a result slot, outshines the things around it.
        boolean made = false;
        for (Slot s : menu.slots) {
            made |= isMade(menu, s);
        }
        for (int y = 0; y < SenseLayout.VISION_H; y++) {
            for (int x = 0; x < SenseLayout.VISION_W; x++) {
                float px = pointerX + offset(x + 0.5f, SenseLayout.VISION_W, VIEW_W);
                float py = pointerY + offset(y + 0.5f, SenseLayout.VISION_H, VIEW_H);
                if (px >= b[0] && px < b[2] && py >= b[1] && py < b[3]) {
                    vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = 0.9f;
                    paint(colour, x, y, PANEL);
                }
            }
        }
        for (Slot s : menu.slots) {
            if (!s.isActive()) {
                continue;
            }
            int x = cell(s.x + 8 - pointerX, SenseLayout.VISION_W, VIEW_W);
            int y = cell(s.y + 8 - pointerY, SenseLayout.VISION_H, VIEW_H);
            if (x < 0 || y < 0 || x >= SenseLayout.VISION_W || y >= SenseLayout.VISION_H) {
                continue;
            }
            ItemStack stack = s.getItem();
            vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_SOLID)] = 1f;
            boolean takes = !carried.isEmpty() && s.mayPlace(carried) && (stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, carried));
            if (takes && made && !(s.container instanceof Inventory) && !isMade(menu, s) && !(s instanceof ResultSlot)) {
                glow(vision, x, y, SenseLayout.CH_CLIMBABLE, WORK_SLOT * MADE_GRID);
            }
            if (takes && (s.container instanceof Inventory) == made) {
                // A slot of the thing being worked (a crafting grid, a brewing stand's bottles, a
                // chest) that could take the stack stands out from one's own pockets, as the grid
                // does on a player's screen, and draws the eye the way a ladder does. Once
                // something has been made, it is the other way round: the hand looks for a pocket
                // to put the rest away in, so it is free to take what was made.
                // A recipe remembered from a book: the places its picture showed this thing in
                // stand out from the rest of the grid.
                boolean hinted = hint == null || made || hint.wants(s, carried);
                glow(vision, x, y, SenseLayout.CH_CLIMBABLE, hinted ? WORK_SLOT : WORK_SLOT * UNHINTED);
            }
            if (stack.isEmpty()) {
                paint(colour, x, y, SLOT);
            } else {
                vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = 0.95f;
                // With nothing made yet, the hand reaches for its own things to put to work; what
                // is already in the thing being worked (wart in a brewing stand, a log in the grid)
                // draws it less, or it kept taking back what it had just put in.
                float pull = made ? (isMade(menu, s) ? 1f : OUTSHONE)
                        : s.container instanceof Inventory ? 1f : PUT_IN;
                if (hint != null && !made && s.container instanceof Inventory && !hint.needs(menu, stack)) {
                    pull *= UNHINTED;
                }
                if (hint != null && !made && hint.spare(menu, s)) {
                    // A whole stack put in one place the picture showed, with another place
                    // still empty: what is there to share out catches the eye again.
                    pull = 1f;
                }
                if (carried.isEmpty() && pull > 0f) {
                    glow(vision, x, y, SenseLayout.CH_CREATURE, pull);
                }
                paint(colour, x, y, colourOf(stack));
            }
        }
    }

    /**
     * Whether a slot holds something just made, waiting to be taken: what a result slot shows,
     * or a potion a brewing stand has brewed (anything in its bottle slots but plain water).
     */
    public static boolean isMade(AbstractContainerMenu menu, Slot s) {
        if (!s.hasItem()) {
            return false;
        }
        if (s instanceof ResultSlot) {
            return true;
        }
        if (menu instanceof net.minecraft.world.inventory.BrewingStandMenu && s.index <= 2) {
            var contents = s.getItem().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            return contents != null && !contents.is(net.minecraft.world.item.alchemy.Potions.WATER);
        }
        return false;
    }

    /** How far from the pointer, in pixels, a point at {@code at} cells across the eye is seen. */
    static float offset(float at, int cells, float view) {
        float u = 2f * at / cells - 1f;
        return Math.signum(u) * (float) Math.pow(Math.abs(u), FOVEA) * view / 2f;
    }

    /** Which cell across the eye sees a point {@code d} pixels from the pointer. */
    static int cell(float d, int cells, float view) {
        float u = Math.signum(d) * (float) Math.pow(Math.min(1f, Math.abs(d) / (view / 2f)), 1f / FOVEA);
        if (Math.abs(d) >= view / 2f) {
            return d < 0 ? -1 : cells;
        }
        return (int) Math.floor((u + 1f) / 2f * cells);
    }

    /**
     * Marks a slot that catches the eye in its cell and, fainter with distance, the cells around it, the
     * way a thing stands out a little beyond its edges. A slot fills a single cell, and one cell
     * pulled the gaze far too weakly to move the pointer: a creature in the world fills many.
     */
    private static void glow(float[] vision, int x, int y, int channel, float value) {
        for (int dy = -GLOW_REACH; dy <= GLOW_REACH; dy++) {
            for (int dx = -GLOW_REACH; dx <= GLOW_REACH; dx++) {
                int cx = x + dx;
                int cy = y + dy;
                if (cx < 0 || cy < 0 || cx >= SenseLayout.VISION_W || cy >= SenseLayout.VISION_H) {
                    continue;
                }
                float v = value * (float) Math.pow(GLOW, Math.max(Math.abs(dx), Math.abs(dy)));
                int i = SenseLayout.visionIndex(cx, cy, channel);
                vision[i] = Math.max(vision[i], v);
            }
        }
    }

    private static void paint(float[] colour, int x, int y, int rgb) {
        colour[SenseLayout.colourIndex(x, y, 0)] = (rgb >> 16 & 0xFF) / 255f;
        colour[SenseLayout.colourIndex(x, y, 1)] = (rgb >> 8 & 0xFF) / 255f;
        colour[SenseLayout.colourIndex(x, y, 2)] = (rgb & 0xFF) / 255f;
    }

    /** A block item looks like its block; other items get a steady colour of their own. */
    private static int colourOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem block) {
            return block.getBlock().defaultMapColor().col;
        }
        int h = BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
        return 0x404040 | (h & 0xBFBFBF);
    }

    /** Fills a thing's features: the item on a stack, or nothing for an empty one. */
    public static void item(ItemStack stack, float[] into) {
        if (stack.isEmpty()) {
            ThingCode.fill(into, null, null);
        } else {
            ThingCode.fill(into, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getItem().getClass().getSimpleName());
        }
    }

    /** Fills the features of what the crosshair rests on: a block, a creature, or nothing. */
    public static void target(HitResult target, net.minecraft.world.level.Level level, float[] into) {
        if (target instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = level.getBlockState(hit.getBlockPos());
            ThingCode.fill(into, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), state.getBlock().getClass().getSimpleName());
        } else if (target instanceof EntityHitResult hit) {
            Entity e = hit.getEntity();
            ThingCode.fill(into, BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(), e.getClass().getSimpleName());
        } else {
            ThingCode.fill(into, null, null);
        }
    }
}
