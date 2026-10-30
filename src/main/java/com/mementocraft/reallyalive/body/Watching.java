package com.mementocraft.reallyalive.body;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The hand moves someone makes on a crafting screen, as one watching over their shoulder sees
 * them: what they picked up from where, and where they put it down. A villager who watches the
 * whole of it, up to taking what was made, remembers the moves with the recipe and can follow
 * them on its own grid (see {@link Hands#copyTarget}).
 */
public final class Watching {
    /** Where a move was made: a grid place (row * 3 + column) or one of these. */
    public static final int POCKET = -1;
    public static final int RESULT = -2;
    /** How many moves are kept from one go at a grid: enough for any recipe and a few fumbles. */
    private static final int MOVES_KEPT = 32;

    /**
     * One move: something picked up (from a pocket, a grid place or the result) or put down in a
     * grid place.
     */
    public record Move(int place, Item item, boolean put) {}

    private record Recording(AbstractContainerMenu menu, List<Move> moves) {}

    private static final Map<UUID, Recording> RECORDING = new java.util.concurrent.ConcurrentHashMap<>();

    private Watching() {}

    /** A click someone made on a screen, before it takes effect. */
    public static void clicked(AbstractContainerMenu menu, int slot, int button, ClickType type, Player player) {
        if (!(menu instanceof CraftingMenu || menu instanceof InventoryMenu) || player.level().isClientSide()) {
            return;
        }
        Recording r = RECORDING.get(player.getUUID());
        if (r == null || r.menu() != menu) {
            r = new Recording(menu, new ArrayList<>());
            RECORDING.put(player.getUUID(), r);
        }
        if (slot < 0 || slot >= menu.slots.size()) {
            return;
        }
        Slot s = menu.getSlot(slot);
        ItemStack carried = menu.getCarried();
        int place = placeOf(menu, s);
        Move move = null;
        if (type == ClickType.QUICK_CRAFT) {
            // A drag only reaches the slots it passes over at stage 1.
            if (AbstractContainerMenu.getQuickcraftHeader(button) == 1 && !carried.isEmpty() && place >= 0) {
                move = new Move(place, carried.getItem(), true);
            }
        } else if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
            if (!carried.isEmpty() && place >= 0) {
                move = new Move(place, carried.getItem(), true);
            } else if (carried.isEmpty() && s.hasItem() && place != Integer.MIN_VALUE) {
                move = new Move(place, s.getItem().getItem(), false);
            }
        }
        if (move != null && r.moves().size() < MOVES_KEPT) {
            r.moves().add(move);
        }
    }

    /** The moves someone made on the screen they took a result from; they start afresh after. */
    public static List<Move> took(Player player) {
        Recording r = RECORDING.remove(player.getUUID());
        return r == null ? List.of() : List.copyOf(r.moves());
    }

    /** Where a slot is, as a move names it, or Integer.MIN_VALUE for a slot that is none of these. */
    public static int placeOf(AbstractContainerMenu menu, Slot s) {
        if (s instanceof ResultSlot) {
            return RESULT;
        }
        if (s.container instanceof Inventory) {
            return POCKET;
        }
        int width = gridWidth(menu);
        int i = s.index - 1;
        if (width == 0 || i < 0 || i >= width * width) {
            return Integer.MIN_VALUE;
        }
        return (i / width) * 3 + i % width;
    }

    /** The grid slot at a place, on this screen, or null when its grid is too small for it. */
    public static Slot gridSlot(AbstractContainerMenu menu, int place) {
        int width = gridWidth(menu);
        int row = place / 3;
        int col = place % 3;
        if (row >= width || col >= width) {
            return null;
        }
        return menu.getSlot(1 + row * width + col);
    }

    static int gridWidth(AbstractContainerMenu menu) {
        return menu instanceof CraftingMenu ? 3 : menu instanceof InventoryMenu ? 2 : 0;
    }
}
