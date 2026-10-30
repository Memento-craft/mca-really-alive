package com.mementocraft.reallyalive.test;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Someone crafting at a table in front of villagers, standing in for a player: a fake player
 * that clicks its way through a crafting screen exactly as a player's clicks arrive at the server.
 */
final class Teacher {
    /** A click on the screen: slot, button, and kind, as a player's mouse sends them. */
    record Click(int slot, int button, ClickType type) {}

    private final FakePlayer player;

    Teacher(ServerLevel level, String name) {
        player = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(("reallyalive-teacher-" + name).getBytes()), "teacher"));
    }

    /** Stands at a place, looking at another. */
    void stand(Vec3 at, Vec3 lookAt) {
        player.setPos(at.x, at.y, at.z);
        Vec3 d = lookAt.subtract(player.getEyePosition());
        player.setYRot((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0));
        player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
        player.setYHeadRot(player.getYRot());
    }

    /**
     * Opens a crafting screen at a table holding these things in its pockets and makes these
     * clicks. Whatever is left (what was made, too) is thrown away after.
     */
    void craft(BlockPos table, List<ItemStack> things, List<Click> clicks) {
        CraftingMenu menu = open(table, things);
        for (Click c : clicks) {
            menu.clicked(c.slot(), c.button(), c.type(), player);
        }
        menu.setCarried(ItemStack.EMPTY);
        player.containerMenu = player.inventoryMenu;
        player.getInventory().clearContent();
    }

    /**
     * Makes a shaped recipe at a table the way a player does: for each kind of thing in it, picks
     * the stack up from its pocket, puts one down in each place it goes with the right button and
     * puts the rest back, then takes what was made.
     */
    void craft(BlockPos table, net.minecraft.world.item.crafting.ShapedRecipe recipe) {
        java.util.LinkedHashMap<net.minecraft.world.item.Item, java.util.List<Integer>> places = new java.util.LinkedHashMap<>();
        var ingredients = recipe.getIngredients();
        for (int i = 0; i < ingredients.size(); i++) {
            var items = ingredients.get(i).getItems();
            if (items.length > 0) {
                places.computeIfAbsent(items[0].getItem(), k -> new java.util.ArrayList<>())
                        .add(1 + (i / recipe.getWidth()) * 3 + i % recipe.getWidth());
            }
        }
        List<ItemStack> things = new java.util.ArrayList<>();
        List<Click> clicks = new java.util.ArrayList<>();
        int pocket = 0;
        for (var e : places.entrySet()) {
            things.add(new ItemStack(e.getKey(), e.getValue().size()));
            clicks.add(new Click(pocket(pocket), 0, ClickType.PICKUP));
            for (int slot : e.getValue()) {
                clicks.add(new Click(slot, 1, ClickType.PICKUP));
            }
            pocket++;
        }
        clicks.add(new Click(0, 0, ClickType.PICKUP));
        craft(table, things, clicks);
    }

    /** Opens a crafting screen at a table, with only these things in its pockets. */
    CraftingMenu open(BlockPos table, List<ItemStack> things) {
        player.getInventory().clearContent();
        for (ItemStack s : things) {
            player.getInventory().add(s.copy());
        }
        CraftingMenu menu = new CraftingMenu(0, player.getInventory(), ContainerLevelAccess.create(player.level(), table));
        player.containerMenu = menu;
        return menu;
    }

    /** Clicks on the screen it has open. */
    void click(CraftingMenu menu, Click c) {
        menu.clicked(c.slot(), c.button(), c.type(), player);
    }

    /** The screen slot of pocket i (0-8 the hotbar, as a player's inventory counts) on a crafting screen. */
    static int pocket(int i) {
        return i < 9 ? 37 + i : 10 + i - 9;
    }
}
