package com.mementocraft.reallyalive.sense;

import com.mementocraft.reallyalive.body.Hands;
import com.mementocraft.reallyalive.brain.SenseLayout;
import com.mementocraft.reallyalive.brain.ThingCode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
    private static final float WORK_SLOT = 0.6f;
    /** How brightly a slot that catches the eye shows in the cells around its own. */
    private static final float GLOW = 0.7f;

    private ScreenSight() {}

    public static void see(AbstractContainerMenu menu, float pointerX, float pointerY, float[] vision, float[] colour) {
        Arrays.fill(vision, 0f);
        Arrays.fill(colour, 0f);
        float cellW = VIEW_W / SenseLayout.VISION_W;
        float cellH = VIEW_H / SenseLayout.VISION_H;
        float left = pointerX - VIEW_W / 2f;
        float top = pointerY - VIEW_H / 2f;
        float[] b = Hands.bounds(menu);
        // What catches the eye depends on the hand: with nothing on the pointer, things to pick up;
        // with a stack on it, the places to put it, the slots of the thing being worked.
        boolean carrying = !menu.getCarried().isEmpty();
        float thingPull = carrying ? 0.2f : 1f;
        float workPull = carrying ? WORK_SLOT : 0.2f * WORK_SLOT;
        for (int y = 0; y < SenseLayout.VISION_H; y++) {
            for (int x = 0; x < SenseLayout.VISION_W; x++) {
                float px = left + (x + 0.5f) * cellW;
                float py = top + (y + 0.5f) * cellH;
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
            int x = (int) Math.floor((s.x + 8 - left) / cellW);
            int y = (int) Math.floor((s.y + 8 - top) / cellH);
            if (x < 0 || y < 0 || x >= SenseLayout.VISION_W || y >= SenseLayout.VISION_H) {
                continue;
            }
            ItemStack stack = s.getItem();
            vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_SOLID)] = 1f;
            if (!(s.container instanceof Inventory)) {
                // The slots of the thing being worked (a crafting grid and its result, a brewing
                // stand's bottles, a chest's contents) stand out from one's own pockets, as the
                // grid and its arrow do on a player's screen, and draw the eye the way a ladder does.
                glow(vision, x, y, SenseLayout.CH_CLIMBABLE, workPull);
            }
            if (stack.isEmpty()) {
                paint(colour, x, y, SLOT);
            } else {
                float full = stack.getCount() / (float) Math.max(1, stack.getMaxStackSize());
                vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = 0.95f;
                glow(vision, x, y, SenseLayout.CH_CREATURE, thingPull * (0.5f + 0.5f * full));
                paint(colour, x, y, colourOf(stack));
            }
        }
    }

    /**
     * Marks a slot that catches the eye in its cell and, more faintly, the cells around it, the
     * way a thing stands out a little beyond its edges. A slot fills a single cell, and one cell
     * pulled the gaze far too weakly to move the pointer: a creature in the world fills many.
     */
    private static void glow(float[] vision, int x, int y, int channel, float value) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = x + dx;
                int cy = y + dy;
                if (cx < 0 || cy < 0 || cx >= SenseLayout.VISION_W || cy >= SenseLayout.VISION_H) {
                    continue;
                }
                float v = dx == 0 && dy == 0 ? value : GLOW * value;
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
