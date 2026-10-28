package com.mementocraft.reallyalive.body;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * An invisible player that stands exactly where a brain villager stands, looks where it looks and
 * carries what it carries, so that the villager's right mouse button can do whatever a player's
 * does: open doors, till with a hoe, plant, milk a cow, open a crafting table or a brewing stand,
 * and work any modded block that answers a player's click. Before each use the villager's things
 * are put in the stand-in's inventory (hand in the first hotbar slot) and afterwards taken back, so
 * the villager never has a second inventory. Unlike NeoForge's own fake player it can open screens,
 * which the villager then works with a mouse pointer (see {@link Hands}).
 */
public final class StandIn extends FakePlayer {
    /** Villager inventory slots the stand-in mirrors: its whole main inventory after the hand slot. */
    static final int MIRRORED = 35;
    private static final Map<StandIn, Hands> OWNERS = new WeakHashMap<>();

    private int containerCounter;

    private StandIn(ServerLevel level, GameProfile profile) {
        super(level, profile);
    }

    /** A stand-in for this villager in its current level, named after it. */
    static StandIn of(Villager body, Hands hands) {
        UUID id = UUID.nameUUIDFromBytes(("reallyalive:" + body.getUUID()).getBytes(StandardCharsets.UTF_8));
        String name = body.getName().getString();
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        StandIn s = new StandIn((ServerLevel) body.level(), new GameProfile(id, name.isEmpty() ? "Villager" : name));
        OWNERS.put(s, hands);
        return s;
    }

    /** The villager hands a stand-in works for, or null for any other player. */
    @Nullable
    public static Hands owner(Object player) {
        return player instanceof StandIn s ? OWNERS.get(s) : null;
    }

    /** Stands where the villager stands, eyes at its eyes, looking where it looks, sneaking as it sneaks. */
    void follow(Villager body) {
        Vec3 eye = body.getEyePosition();
        setPos(eye.x, eye.y - getEyeHeight(), eye.z);
        setYRot(body.getYRot());
        setXRot(body.getXRot());
        setYHeadRot(body.getYHeadRot());
        yRotO = getYRot();
        xRotO = getXRot();
        setShiftKeyDown(body.isShiftKeyDown());
        setOnGround(body.onGround());
    }

    /** Puts what the villager carries into the stand-in's hands and inventory. */
    void takeFrom(Villager body) {
        Inventory inv = getInventory();
        inv.selected = 0;
        inv.items.set(0, body.getMainHandItem());
        SimpleContainer own = body.getInventory();
        int n = Math.min(own.getContainerSize(), MIRRORED);
        for (int i = 0; i < MIRRORED; i++) {
            inv.items.set(1 + i, i < n ? own.getItem(i) : ItemStack.EMPTY);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                inv.armor.set(slot.getIndex(), body.getItemBySlot(slot));
            }
        }
        inv.offhand.set(0, body.getOffhandItem());
    }

    /** Gives back to the villager what the stand-in now carries; what does not fit falls at its feet. */
    void giveBack(Villager body) {
        Inventory inv = getInventory();
        if (body.getMainHandItem() != inv.items.get(0)) {
            body.setItemSlot(EquipmentSlot.MAINHAND, inv.items.get(0));
        }
        if (body.getOffhandItem() != inv.offhand.get(0)) {
            body.setItemSlot(EquipmentSlot.OFFHAND, inv.offhand.get(0));
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && body.getItemBySlot(slot) != inv.armor.get(slot.getIndex())) {
                body.setItemSlot(slot, inv.armor.get(slot.getIndex()));
            }
        }
        SimpleContainer own = body.getInventory();
        int n = Math.min(own.getContainerSize(), MIRRORED);
        for (int i = 0; i < n; i++) {
            if (own.getItem(i) != inv.items.get(1 + i)) {
                own.setItem(i, inv.items.get(1 + i));
            }
        }
        for (int i = n; i < MIRRORED; i++) {
            ItemStack extra = inv.items.get(1 + i);
            if (!extra.isEmpty()) {
                inv.items.set(1 + i, ItemStack.EMPTY);
                ItemStack left = own.addItem(extra);
                if (!left.isEmpty()) {
                    body.level().addFreshEntity(new ItemEntity(body.level(), body.getX(), body.getY(), body.getZ(), left));
                }
            }
        }
    }

    /**
     * Draws what is in this hand for as long as the villager drew it and lets it loose: a bow
     * shoots, a trident is thrown, a crossbow is loaded. True when something left its hands.
     */
    boolean letLoose(net.minecraft.world.InteractionHand hand, int remaining) {
        startUsingItem(hand);
        if (!isUsingItem()) {
            return false;
        }
        useItemRemaining = remaining;
        int before = level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                getBoundingBox().inflate(4.0)).size();
        releaseUsingItem();
        return level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                getBoundingBox().inflate(4.0)).size() > before;
    }

    /** True while a screen other than its own inventory is open. */
    boolean inOtherScreen() {
        return containerMenu != inventoryMenu;
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider provider) {
        return openMenu(provider, (Consumer<RegistryFriendlyByteBuf>) null);
    }

    /**
     * Opens the screen as for a real player, without telling any client: the menu is what the
     * villager works.
     */
    @Override
    public OptionalInt openMenu(@Nullable MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extraData) {
        if (provider == null) {
            return OptionalInt.empty();
        }
        if (containerMenu != inventoryMenu) {
            doCloseContainer();
        }
        containerCounter = containerCounter % 100 + 1;
        AbstractContainerMenu menu = provider.createMenu(containerCounter, getInventory(), this);
        if (menu == null) {
            return OptionalInt.empty();
        }
        containerMenu = menu;
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Open(this, menu));
        return OptionalInt.of(containerCounter);
    }
    /**
     * Reading a book: a recipe book opens as a screen the villager can look at, a page at a time.
     * Other books have only text, which villagers cannot read yet.
     */
    @Override
    public void openItemGui(ItemStack stack, net.minecraft.world.InteractionHand hand) {
        if (RecipeBook.isRecipeBook(stack)) {
            openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, player) -> new RecipeBook.Menu(id, stack, level()),
                    stack.getHoverName()));
        }
    }
}
