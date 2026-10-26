package com.mementocraft.reallyalive.body;

import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mementocraft.reallyalive.brain.Motives;
import com.mementocraft.reallyalive.brain.SoundChannel;
import com.mementocraft.reallyalive.sense.Hearing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * A player's hands for a brain-driven villager: the two mouse buttons, the mouse wheel and the
 * inventory key. Attack held on the block in the crosshair digs it at a player's dig speed with the
 * held tool, and progress is lost when the button is released or the crosshair moves to another
 * block; a click on a creature hits it. Use does what a player's right click does, through a
 * {@link StandIn} player: work the block in the crosshair (a door, a crafting table, a brewing
 * stand, any modded block), use the held thing on it (place a block, till with a hoe, plant a seed),
 * use it on a creature (milk, shear, feed), or use it in the air (fill a bucket); holding use on
 * food or a potion eats or drinks it. Use repeats every 4 ticks while held. Reach, dig speed, the
 * 5-tick pause after breaking a block and attack cooldown all follow the player rules (the client's
 * MultiPlayerGameMode and Player). What it digs, and items it walks over, go to its hand or
 * inventory.
 *
 * <p>While a screen is open (its own inventory with the 2x2 crafting grid, or a block's screen) the
 * mouse moves a pointer over the screen instead of turning the head, the buttons click slots as a
 * player's do (shift-click moves a stack across), and the inventory key closes the screen. The
 * body stands still meanwhile, as a player's does.
 */
public final class Hands {
    /** A survival player's reach for blocks and for creatures. */
    public static final double BLOCK_REACH = 4.5;
    public static final double ENTITY_REACH = 3.0;
    private static final int BREAK_PAUSE = 5;
    private static final int USE_REPEAT = 4;
    /** The keyboard's repeat: half a second before the first repeat, then one every tick or so. */
    private static final int KEY_REPEAT_DELAY = 40;
    private static final int KEY_REPEAT_EVERY = 20;
    /** A player picks up items this close (Player.aiStep). */
    private static final double PICKUP_XZ = 1.0;
    private static final double PICKUP_Y = 0.5;
    /**
     * Screen pixels the pointer moves per degree the mouse would have turned the head. At one, a
     * newborn's pointer took longer to cross the screen to what it had made than the screen
     * stayed open.
     */
    public static final float POINTER_PER_DEGREE = 2f;

    private final Villager body;
    private boolean attackWasDown;
    private boolean useWasDown;
    /**
     * A mouse button pressed on a screen while holding a stack, as a player starts a drag: 0 left,
     * 1 right, -1 none. The slots dragged over share the stack when the button is let go.
     */
    private int dragButton = -1;
    private final java.util.LinkedHashSet<Slot> dragSlots = new java.util.LinkedHashSet<>();
    private boolean inventoryWasDown;
    /** Ticks the inventory key has been held, for the keyboard's repeat. */
    private int inventoryHeld;
    private boolean wheelWasDown;
    private StandIn standIn;
    /** The open screen, or null. Its own inventory counts as a screen while it is open. */
    private AbstractContainerMenu screen;
    private float pointerX;
    private float pointerY;
    /** The slot under the pointer, or null. */
    private Slot pointed;
    private int useDelay;
    private int destroyDelay;
    private BlockPos digging;
    private float digProgress;
    private int digTicks;
    /** Ticks since the last swing at a creature, for a player's attack cooldown. */
    private int sinceSwing = 100;
    private HitResult target;
    /** Items carried at the last {@link #motivePleasure()}, or -1 to take the next count as the start. */
    private int carried = -1;
    private boolean dugThisTick;
    private int placedThisTick;
    private int changedThisTick;
    private int madeThisTick;
    private int formedThisTick;
    private float movedThisTick;
    /** Clicks that moved something, for tests. */
    public int moves;
    /** How much it has lately been moving things about on screens; halves every minute. */
    private float movedLately;
    private static final float MOVED_HALF_LIFE = 1200f;
    private float noveltyThisTick;
    /** How familiar each kind of screen is, from 0 (never seen) to 1; fades back as it is forgotten. */
    private final java.util.Map<Object, Float> familiar = new java.util.HashMap<>();
    private final java.util.Map<Object, Long> lastSeen = new java.util.HashMap<>();
    /** Ticks for a familiar kind of screen to be about half forgotten. */
    private static final float FORGET_TICKS = 6000f;
    /** How much more familiar a kind of screen grows each time it is opened. */
    private static final float GET_USED = 0.3f;
    private long ticksLived;
    /** When each kind of thing it carries was last looked over in a screen. */
    private final java.util.Map<net.minecraft.world.item.Item, Long> lookedOver = new java.util.HashMap<>();
    /** Ticks after which a thing looked over is worth looking at again. */
    private static final long LOOK_AGAIN_TICKS = 6000L;
    /** Whether a result slot showed something after the last click, and what has been shown on this screen. */
    private boolean resultShown;
    private final java.util.Set<net.minecraft.world.item.Item> formedOnScreen = new java.util.HashSet<>();
    /** Diagnostics for benchmarks: tries to place, why they failed, and where the last block went. */
    public int placeTries;
    public int placeNoTarget;
    public int placeRefused;
    public int placedTotal;
    public BlockPos lastPlaced;
    /** Diagnostics: uses that worked a block, changed a block, opened a screen, or made something. */
    public int usedOnBlock;
    public int changedTotal;
    public int screensOpened;
    public int madeTotal;
    /** Diagnostics: clicks on a screen's slots, and results seen forming in a result slot. */
    public int screenClicks;
    public int clicksOnThings;
    /** Ticks since the screen was opened or last clicked on: how long it has gone without doing anything. */
    private int screenIdle;
    /** Ticks without a click after which a screen is wholly boring. */
    private static final int SCREEN_BORED_TICKS = 100;
    /** Ticks a screen stays fresh after opening it or clicking on it, before boredom sets in. */
    private static final int SCREEN_FRESH_TICKS = 40;
    public int formedTotal;

    public Hands(Villager body) {
        this.body = body;
    }

    /** How far digging the current block has got, 0..1. */
    public float digProgress() {
        return digging == null ? 0f : Math.min(1f, digProgress);
    }

    /** True when the crosshair rests on a block or creature within reach. */
    public boolean targetInReach() {
        return target != null && target.getType() != HitResult.Type.MISS;
    }

    public boolean holdingBlock() {
        return body.getMainHandItem().getItem() instanceof BlockItem;
    }

    /**
     * The inborn pleasure of using the hands since the last call: collecting (anything it carries
     * went up, whether a dig drop or an item picked up off the ground), each tick a block cracked
     * further, each block placed, each block its held thing turned into another, and each thing it
     * made. Moving things about on a screen is not collecting: what it carries is counted afresh
     * once the screen closes. Call once per tick, after the body has moved.
     */
    public float motivePleasure() {
        float pleasure = 0f;
        int now = countCarried();
        if (carried >= 0 && now > carried) {
            pleasure += Motives.COLLECT;
        }
        carried = screen != null ? -1 : now;
        pleasure += changedThisTick * Motives.CHANGE + madeThisTick * Motives.MAKE + formedThisTick * Motives.FORM
                + movedThisTick * Motives.MOVE_THINGS
                + noveltyThisTick * Motives.NEW_SCREEN;
        noveltyThisTick = 0f;
        changedThisTick = 0;
        madeThisTick = 0;
        formedThisTick = 0;
        movedThisTick = 0f;
        if (dugThisTick) {
            pleasure += Motives.DIG_PER_TICK;
        }
        pleasure += placedThisTick * Motives.PLACE;
        dugThisTick = false;
        placedThisTick = 0;
        return pleasure;
    }

    /** Forgets what it carried, for when something other than its own hands fills them. */
    public void recount() {
        carried = -1;
    }

    private int countCarried() {
        int n = body.getMainHandItem().getCount();
        var inventory = body.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            n += inventory.getItem(i).getCount();
        }
        return n;
    }

    /**
     * Presses or releases the buttons and keys for this tick. Runs on the server thread before the
     * body moves.
     */
    public void tick(MotorCommand keys) {
        ticksLived++;
        movedLately *= (float) Math.pow(0.5, 1.0 / MOVED_HALF_LIFE);
        if (screen != null) {
            forEachCarried(item -> lookedOver.put(item, ticksLived));
            if (standIn != null) {
                // What is on the pointer or in a crafting grid is in the stand-in's hands, and seen too.
                forEachIn(standIn.getInventory(), item -> lookedOver.put(item, ticksLived));
                if (!screen.getCarried().isEmpty()) {
                    lookedOver.put(screen.getCarried().getItem(), ticksLived);
                }
                for (net.minecraft.world.inventory.Slot s : screen.slots) {
                    if (s.hasItem()) {
                        lookedOver.put(s.getItem().getItem(), ticksLived);
                    }
                }
            }
        }
        sinceSwing++;
        if (useDelay > 0) {
            useDelay--;
        }
        pickUpNearby();
        boolean inventoryPressed = keys.inventory() && !inventoryWasDown;
        inventoryWasDown = keys.inventory();
        inventoryHeld = keys.inventory() ? inventoryHeld + 1 : 0;
        // A held key repeats after two seconds, and a screen takes each repeat as a press: holding
        // the inventory key closes the screen it opened, as it does for a player.
        if (screen != null && inventoryHeld > KEY_REPEAT_DELAY && (inventoryHeld - KEY_REPEAT_DELAY) % KEY_REPEAT_EVERY == 0) {
            inventoryPressed = true;
        }
        if (screen != null) {
            workScreen(keys, inventoryPressed);
            return;
        }
        if (inventoryPressed) {
            openOwnInventory();
            return;
        }
        if (keys.hotbar() && !wheelWasDown) {
            nextInHand();
        }
        wheelWasDown = keys.hotbar();
        target = pick();

        boolean attack = keys.attack();
        if (attack) {
            if (!attackWasDown) {
                startAttack();
            }
            if (target instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                continueDigging(hit.getBlockPos());
            } else {
                stopDigging();
            }
        } else {
            stopDigging();
        }
        attackWasDown = attack;

        boolean use = keys.use();
        if (body.isUsingItem()) {
            // Eating or drinking goes on while use is held, and stops when it is let go.
            if (!use) {
                body.releaseUsingItem();
            }
        } else if (use && (!useWasDown || useDelay == 0)) {
            useDelay = USE_REPEAT;
            use();
        }
        useWasDown = use;
    }

    /** Lets go of everything, for when the brain hands the body back. */
    public void release() {
        stopDigging();
        closeScreen();
        if (body.isUsingItem()) {
            body.stopUsingItem();
        }
        attackWasDown = false;
        useWasDown = false;
        inventoryWasDown = false;
        wheelWasDown = false;
    }

    /** What the crosshair rests on, or null: a block, a creature, or nothing within reach. */
    public HitResult target() {
        return targetInReach() ? target : null;
    }

    /**
     * How boring the open screen has become: hardly at all for two seconds after opening it,
     * clicking on it or while carrying a stack, then wholly after five more seconds of doing
     * nothing; 0 with no screen open. The urge to leave a screen grows with it, so a screen is
     * left soon once nothing is being done on it.
     */
    public float screenBoredom() {
        return screen == null ? 0f : Mth.clamp((screenIdle - SCREEN_FRESH_TICKS) / (float) SCREEN_BORED_TICKS, 0.05f, 1f);
    }

    /**
     * 1 while it carries something it has never looked over in a screen, or not for five minutes,
     * else 0; always 0 with a screen open. Curiosity drives the inventory key, so a villager now and
     * then opens its inventory to look at what it has, as a player does after picking something up.
     */
    public float curiosity() {
        if (screen != null) {
            return 0f;
        }
        boolean[] curious = {false};
        forEachCarried(item -> {
            Long seen = lookedOver.get(item);
            curious[0] |= seen == null || ticksLived - seen > LOOK_AGAIN_TICKS;
        });
        return curious[0] ? 1f : 0f;
    }

    private void forEachCarried(java.util.function.Consumer<net.minecraft.world.item.Item> action) {
        if (!body.getMainHandItem().isEmpty()) {
            action.accept(body.getMainHandItem().getItem());
        }
        forEachIn(body.getInventory(), action);
    }

    private static void forEachIn(net.minecraft.world.Container container,
            java.util.function.Consumer<net.minecraft.world.item.Item> action) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                action.accept(container.getItem(i).getItem());
            }
        }
    }

    /** True while a screen is open. */
    public boolean screenOpen() {
        return screen != null;
    }

    /** The open screen, or null. */
    public AbstractContainerMenu screen() {
        return screen;
    }

    public float pointerX() {
        return pointerX;
    }

    public float pointerY() {
        return pointerY;
    }

    /** The slot under the mouse pointer, or null. */
    public Slot pointedSlot() {
        return pointed;
    }

    /** The stack hanging on the mouse pointer, or empty. */
    public ItemStack onPointer() {
        return screen == null ? ItemStack.EMPTY : screen.getCarried();
    }

    /** How far eating or drinking has got, 0..1. */
    public float usingProgress() {
        if (!body.isUsingItem()) {
            return 0f;
        }
        int total = body.getUseItem().getUseDuration(body);
        return total <= 0 ? 0f : Math.min(1f, body.getTicksUsingItem() / (float) total);
    }

    /** Something it made came out of a result slot: crafted, smelted or brewed. */
    public void made() {
        madeThisTick++;
        madeTotal++;
    }

    // ---------------------------------------------------------------- picking up

    /** Walking over an item picks it up, as it does for a player, once the item may be picked up. */
    private void pickUpNearby() {
        if (!(body.level() instanceof ServerLevel level) || !body.isAlive()) {
            return;
        }
        AABB reach = body.getBoundingBox().inflate(PICKUP_XZ, PICKUP_Y, PICKUP_XZ);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, reach, e -> e.isAlive() && !e.hasPickUpDelay())) {
            ItemStack stack = item.getItem().copy();
            int before = stack.getCount();
            take(stack);
            int taken = before - stack.getCount();
            if (taken > 0) {
                body.take(item, taken);
                if (stack.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(stack);
                }
            }
        }
    }

    /** The mouse wheel: the next thing it carries comes into its hand, and what it held goes in its place. */
    private void nextInHand() {
        var inventory = body.getInventory();
        int size = inventory.getContainerSize();
        for (int k = 0; k < size; k++) {
            int i = k;
            ItemStack next = inventory.getItem(i);
            if (next.isEmpty()) {
                continue;
            }
            ItemStack hand = body.getMainHandItem();
            // Rotate: the item in hand goes to the end of the line, so turning the wheel goes
            // through everything it carries in turn.
            inventory.removeItemNoUpdate(i);
            for (int j = i; j < size - 1; j++) {
                inventory.setItem(j, inventory.getItem(j + 1));
            }
            inventory.setItem(size - 1, ItemStack.EMPTY);
            body.setItemSlot(EquipmentSlot.MAINHAND, next);
            if (!hand.isEmpty()) {
                ItemStack left = inventory.addItem(hand);
                if (!left.isEmpty()) {
                    body.level().addFreshEntity(new ItemEntity(body.level(), body.getX(), body.getY(), body.getZ(), left));
                }
            }
            return;
        }
    }

    // ---------------------------------------------------------------- aiming

    /** What the crosshair rests on: the nearer of the block within block reach and a creature within creature reach. */
    private HitResult pick() {
        Vec3 eye = body.getEyePosition();
        Vec3 look = body.getViewVector(1f);
        Vec3 blockEnd = eye.add(look.scale(BLOCK_REACH));
        BlockHitResult block = body.level().clip(new ClipContext(eye, blockEnd, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, body));
        double blockDistSqr = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : block.getLocation().distanceToSqr(eye);

        Vec3 entityEnd = eye.add(look.scale(ENTITY_REACH));
        AABB sweep = body.getBoundingBox().expandTowards(look.scale(ENTITY_REACH)).inflate(1.0);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(body, eye, entityEnd, sweep,
                e -> !e.isSpectator() && e.isPickable(), ENTITY_REACH * ENTITY_REACH);
        if (entity != null && entity.getLocation().distanceToSqr(eye) < blockDistSqr) {
            return entity;
        }
        return block;
    }

    // ---------------------------------------------------------------- left button

    private void startAttack() {
        if (target instanceof EntityHitResult hit) {
            hitCreature(hit.getEntity());
        }
        body.swing(InteractionHand.MAIN_HAND);
    }

    /** A player's melee hit: fist or weapon damage, scaled by how far the attack has recharged. */
    private void hitCreature(Entity victim) {
        if (!(body.level() instanceof ServerLevel level) || !victim.isAttackable()) {
            return;
        }
        ItemStack weapon = body.getMainHandItem();
        double[] damage = {1.0};
        double[] speed = {4.0};
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) {
                return;
            }
            if (attribute.is(Attributes.ATTACK_DAMAGE)) {
                damage[0] += modifier.amount();
            } else if (attribute.is(Attributes.ATTACK_SPEED)) {
                speed[0] += modifier.amount();
            }
        });
        float recharge = (float) Math.min(1.0, (sinceSwing + 0.5) / (20.0 / speed[0]));
        var source = body.damageSources().mobAttack(body);
        float amount = (float) damage[0];
        amount = EnchantmentHelper.modifyDamage(level, weapon, victim, source, amount);
        amount *= 0.2f + recharge * recharge * 0.8f;
        boolean critical = recharge > 0.9f && body.fallDistance > 0f && !body.onGround() && !body.onClimbable()
                && !body.isInWater() && !body.hasEffect(MobEffects.BLINDNESS) && !body.isPassenger() && !body.isSprinting();
        if (critical) {
            amount *= 1.5f;
        }
        boolean knockback = body.isSprinting() && recharge > 0.9f;
        if (victim.hurt(source, amount)) {
            if (knockback && victim instanceof LivingEntity living) {
                float yaw = body.getYRot() * ((float) Math.PI / 180f);
                living.knockback(0.5, Math.sin(yaw), -Math.cos(yaw));
                body.setDeltaMovement(body.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                body.setSprinting(false);
            }
            if (victim instanceof LivingEntity living && weapon.getItem().hurtEnemy(weapon, living, body)) {
                weapon.getItem().postHurtEnemy(weapon, living, body);
            }
        }
        sinceSwing = 0;
    }

    private void continueDigging(BlockPos pos) {
        if (destroyDelay > 0) {
            destroyDelay--;
            return;
        }
        if (!pos.equals(digging)) {
            // A new block: start on it, and unless it broke at once, dig into it this same tick
            // (vanilla's startAttack then continueAttack).
            startDigging(pos);
            if (!pos.equals(digging)) {
                return;
            }
        }
        BlockState state = body.level().getBlockState(pos);
        if (state.isAir()) {
            digging = null;
            return;
        }
        float dig = destroyProgress(state, pos);
        digProgress += dig;
        if (dig > 0f) {
            dugThisTick = true;
        }
        // A player's client plays the hit sound every 4 ticks of digging.
        if (digTicks++ % 4 == 0) {
            Hearing.sound(body.level(), Vec3.atCenterOf(pos), SoundChannel.DIGGING, 0.25f);
        }
        if (digProgress >= 1f) {
            breakBlock(pos);
            digProgress = 0f;
            destroyDelay = BREAK_PAUSE;
        }
        showCracks();
    }

    private void startDigging(BlockPos pos) {
        stopDigging();
        BlockState state = body.level().getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        if (destroyProgress(state, pos) >= 1f) {
            breakBlock(pos);
            return;
        }
        digging = pos;
        digProgress = 0f;
        digTicks = 0;
        showCracks();
    }

    private void stopDigging() {
        if (digging != null) {
            body.level().destroyBlockProgress(body.getId(), digging, -1);
        }
        digging = null;
        digProgress = 0f;
    }

    private void showCracks() {
        if (digging != null) {
            body.level().destroyBlockProgress(body.getId(), digging, (int) (digProgress * 10f) - 1);
        }
    }

    /** A player's dig speed for this block with the held tool (Player.getDigSpeed and BlockBehaviour.getDestroyProgress). */
    private float destroyProgress(BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(body.level(), pos);
        if (hardness < 0f || !EventHooks.canEntityGrief(body.level(), body)) {
            return 0f;
        }
        ItemStack tool = body.getMainHandItem();
        float speed = tool.getDestroySpeed(state);
        if (speed > 1f) {
            double[] efficiency = {0};
            EnchantmentHelper.forEachModifier(tool, EquipmentSlot.MAINHAND, (attribute, modifier) -> {
                if (attribute.is(Attributes.MINING_EFFICIENCY) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                    efficiency[0] += modifier.amount();
                }
            });
            speed += (float) efficiency[0];
        }
        if (MobEffectUtil.hasDigSpeed(body)) {
            speed *= 1f + (MobEffectUtil.getDigSpeedAmplification(body) + 1) * 0.2f;
        }
        if (body.hasEffect(MobEffects.DIG_SLOWDOWN)) {
            speed *= switch (body.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 8.1e-4f;
            };
        }
        if (body.isEyeInFluid(FluidTags.WATER)) {
            speed *= 0.2f;
        }
        if (!body.onGround()) {
            speed /= 5f;
        }
        boolean harvests = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        return speed / hardness / (harvests ? 30f : 100f);
    }

    private void breakBlock(BlockPos pos) {
        if (!(body.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        ItemStack tool = body.getMainHandItem();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        boolean harvests = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        if (!level.destroyBlock(pos, false, body)) {
            return;
        }
        Hearing.sound(level, Vec3.atCenterOf(pos), SoundChannel.DIGGING, 1f);
        if (harvests) {
            for (ItemStack drop : Block.getDrops(state, level, pos, blockEntity, body, tool)) {
                take(drop);
            }
            state.spawnAfterBreak(level, pos, tool, true);
        }
        if (!tool.isEmpty()) {
            tool.getItem().mineBlock(tool, level, state, pos, body);
        }
        digging = null;
    }

    /** Picks up an item like a player does: into the hand if it is free or holds the same, then the inventory. */
    private void take(ItemStack stack) {
        ItemStack hand = body.getMainHandItem();
        if (hand.isEmpty()) {
            body.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
            stack.setCount(0);
        } else if (ItemStack.isSameItemSameComponents(hand, stack)) {
            int moved = Math.min(stack.getCount(), hand.getMaxStackSize() - hand.getCount());
            hand.grow(moved);
            stack.shrink(moved);
        }
        if (!stack.isEmpty()) {
            ItemStack left = body.getInventory().addItem(stack);
            if (!left.isEmpty()) {
                body.level().addFreshEntity(new ItemEntity(body.level(), body.getX(), body.getY(), body.getZ(), left));
            }
        }
    }

    // ---------------------------------------------------------------- right button

    private StandIn standIn() {
        if (standIn == null || standIn.level() != body.level()) {
            closeScreen();
            standIn = StandIn.of(body, this);
        }
        return standIn;
    }

    /**
     * A player's right click (Minecraft.startUseItem): for each hand in turn, work the creature or
     * block in the crosshair, else use the held thing in the air; holding food or a potion starts
     * eating or drinking it. With mob griefing off, the hands are empty for anything that would change
     * the world, so doors and screens still work.
     */
    private void use() {
        if (!(body.level() instanceof ServerLevel level)) {
            return;
        }
        StandIn p = standIn();
        p.follow(body);
        p.takeFrom(body);
        boolean grief = EventHooks.canEntityGrief(level, body);
        boolean onBlock = target instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK;
        if (p.getMainHandItem().getItem() instanceof BlockItem) {
            placeTries++;
            placeNoTarget += onBlock && grief ? 0 : 1;
        }
        try {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = p.getItemInHand(hand);
                if (target instanceof EntityHitResult hit) {
                    if (!grief) {
                        break;
                    }
                    Entity creature = hit.getEntity();
                    InteractionResult r = creature.interactAt(p, hit.getLocation().subtract(creature.position()), hand);
                    if (!r.consumesAction()) {
                        r = p.interactOn(creature, hand);
                    }
                    if (r.consumesAction()) {
                        swing(r, hand);
                        return;
                    }
                } else if (onBlock && useOnBlock(level, p, hand, stack, (BlockHitResult) target, grief)) {
                    return;
                }
                stack = p.getItemInHand(hand);
                if (stack.isEmpty() || !grief) {
                    continue;
                }
                if (stack.getUseDuration(body) > 0) {
                    // Eating and drinking are the villager's own: the food and the potion's effects are its.
                    if (stack.has(DataComponents.FOOD) || stack.getItem() instanceof PotionItem
                            || stack.getUseAnimation() == UseAnim.DRINK || stack.getUseAnimation() == UseAnim.EAT) {
                        p.giveBack(body);
                        body.startUsingItem(hand);
                        p.takeFrom(body);
                        return;
                    }
                }
                InteractionResult r = p.gameMode.useItem(p, level, stack, hand);
                if (p.isUsingItem()) {
                    // A bow, a shield, a spyglass: things only a player can hold up. Let go of it.
                    p.stopUsingItem();
                }
                if (r.consumesAction()) {
                    swing(r, hand);
                    return;
                }
            }
        } finally {
            p.giveBack(body);
            if (p.inOtherScreen()) {
                openScreen(p.containerMenu);
            }
        }
    }

    /**
     * Uses one hand on the block in the crosshair, as ServerPlayerGameMode.useItemOn does, and notes
     * what it did: placed a block, turned a block into another, or just worked it.
     */
    private boolean useOnBlock(ServerLevel level, StandIn p, InteractionHand hand, ItemStack stack, BlockHitResult hit, boolean grief) {
        boolean placing = grief && hand == InteractionHand.MAIN_HAND && stack.getItem() instanceof BlockItem;
        BlockItem blockItem = placing ? (BlockItem) stack.getItem() : null;
        BlockPos clicked = hit.getBlockPos();
        BlockPos placeAt = placing ? new BlockPlaceContext(p, hand, stack, hit).getClickedPos() : clicked;
        BlockState clickedBefore = level.getBlockState(clicked);
        BlockState placeBefore = level.getBlockState(placeAt);

        ItemStack kept = null;
        if (!grief && !stack.isEmpty()) {
            kept = stack;
            p.setItemInHand(hand, ItemStack.EMPTY);
        }
        InteractionResult r;
        try {
            r = p.gameMode.useItemOn(p, level, p.getItemInHand(hand), hand, hit);
        } finally {
            if (kept != null) {
                p.setItemInHand(hand, kept);
            }
        }
        if (!r.consumesAction()) {
            if (placing) {
                placeRefused++;
            }
            return r == InteractionResult.FAIL;
        }
        swing(r, hand);
        usedOnBlock++;
        boolean placed = placing && placeBefore.getBlock() != blockItem.getBlock()
                && level.getBlockState(placeAt).getBlock() == blockItem.getBlock();
        if (placed) {
            placedThisTick++;
            placedTotal++;
            lastPlaced = placeAt;
            p.giveBack(body);
            refillHand(blockItem);
            p.takeFrom(body);
        } else if (placing) {
            placeRefused++;
        }
        if (!placed && !stack.isEmpty() && kept == null && level.getBlockState(clicked).getBlock() != clickedBefore.getBlock()) {
            changedThisTick++;
            changedTotal++;
        }
        return true;
    }

    private void swing(InteractionResult r, InteractionHand hand) {
        if (r.shouldSwing()) {
            body.swing(hand);
        }
    }

    /** When the last block in hand is placed, the next stack of the same block moves into the hand. */
    private void refillHand(BlockItem placed) {
        if (!body.getMainHandItem().isEmpty()) {
            return;
        }
        var inventory = body.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(placed)) {
                body.setItemSlot(EquipmentSlot.MAINHAND, inventory.removeItemNoUpdate(i));
                return;
            }
        }
    }

    // ---------------------------------------------------------------- screens

    /** The inventory key with no screen open: its own inventory, with the 2x2 crafting grid. */
    private void openOwnInventory() {
        if (!(body.level() instanceof ServerLevel)) {
            return;
        }
        StandIn p = standIn();
        p.follow(body);
        p.takeFrom(body);
        openScreen(p.inventoryMenu);
    }

    private void openScreen(AbstractContainerMenu menu) {
        stopDigging();
        screen = menu;
        screensOpened++;
        noveltyThisTick += novelty(menu);
        screenIdle = 0;
        resultShown = false;
        formedOnScreen.clear();
        // The pointer starts in the middle of the screen, where a player's mouse appears.
        float[] b = bounds(menu);
        pointerX = (b[0] + b[2]) / 2f;
        pointerY = (b[1] + b[3]) / 2f;
        pointed = slotAt(menu, pointerX, pointerY);
        attackWasDown = true;
        useWasDown = true;
    }

    /** Closes the open screen. Whatever is on the pointer or left in a crafting grid goes back to it. */
    private void closeScreen() {
        if (screen == null) {
            return;
        }
        StandIn p = standIn;
        if (p != null) {
            p.takeFrom(body);
            if (p.inOtherScreen()) {
                p.closeContainer();
            } else {
                p.doCloseContainer();
            }
            p.giveBack(body);
        }
        screen = null;
        pointed = null;
        dragButton = -1;
        dragSlots.clear();
        attackWasDown = true;
        useWasDown = true;
    }

    /**
     * One tick with a screen open: the mouse moves the pointer, the left button clicks a slot (with
     * shift, the stack moves across, as a player's shift-click does), the right button clicks with
     * the other button (half a stack, or one item put down), and the inventory key closes it.
     */
    private void workScreen(MotorCommand keys, boolean inventoryPressed) {
        StandIn p = standIn;
        if (p == null || p.level() != body.level()) {
            screen = null;
            return;
        }
        p.follow(body);
        p.takeFrom(body);
        try {
            if (inventoryPressed || screen != p.containerMenu || !screen.stillValid(p)) {
                p.giveBack(body);
                closeScreen();
                return;
            }
            float[] b = bounds(screen);
            pointerX = Mth.clamp(pointerX + keys.yawDelta() * POINTER_PER_DEGREE, b[0], b[2]);
            pointerY = Mth.clamp(pointerY + keys.pitchDelta() * POINTER_PER_DEGREE, b[1], b[3]);
            pointed = slotAt(screen, pointerX, pointerY);
            // Carrying a stack about is doing something, and something just made is worth looking
            // at; only an empty pointer over nothing new gets bored.
            screenIdle = screen.getCarried().isEmpty() && !resultShowing(screen) ? screenIdle + 1 : 0;
            boolean left = keys.attack() && !attackWasDown;
            boolean right = keys.use() && !useWasDown;
            boolean leftUp = !keys.attack() && attackWasDown;
            boolean rightUp = !keys.use() && useWasDown;
            attackWasDown = keys.attack();
            useWasDown = keys.use();
            mouse(p, keys.sneak(), left ? 0 : right ? 1 : -1, dragButton == 0 && leftUp || dragButton == 1 && rightUp);
        } finally {
            if (screen != null) {
                p.giveBack(body);
            }
        }
    }

    /**
     * How new this kind of screen feels, 0 to 1, and it grows more familiar for having been seen.
     * Familiarity is kept with the time it was last updated and fades when looked up.
     */
    private float novelty(AbstractContainerMenu menu) {
        Object kind = menu.getClass();
        float f = familiar.getOrDefault(kind, 0f);
        Long seen = lastSeen.get(kind);
        if (seen != null) {
            f *= (float) Math.pow(0.5, (ticksLived - seen) / FORGET_TICKS);
        }
        lastSeen.put(kind, ticksLived);
        familiar.put(kind, Math.min(1f, f + GET_USED));
        return 1f - f;
    }

    /**
     * The mouse on a screen, as a player's works: a press with nothing on the pointer clicks at
     * once (picking up, or shift-moving, what is under it); a press while holding a stack starts a
     * drag instead, every slot the pointer then passes over joins it, and letting go shares the
     * stack out over them (all of it into one slot, if only one was passed), or, if the pointer
     * never reached a slot that takes it, clicks where the pointer is.
     */
    private void mouse(StandIn p, boolean shift, int pressed, boolean released) {
        if (dragButton < 0 && pressed >= 0) {
            if (screen.getCarried().isEmpty()) {
                if (pointed != null) {
                    click(p, pointed.index, pressed, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP);
                }
            } else {
                dragButton = pressed;
                dragSlots.clear();
            }
        }
        if (dragButton < 0) {
            return;
        }
        ItemStack carried = screen.getCarried();
        if (pointed != null && !carried.isEmpty() && carried.getCount() > dragSlots.size()
                && AbstractContainerMenu.canItemQuickReplace(pointed, carried, true) && pointed.mayPlace(carried)
                && screen.canDragTo(pointed)) {
            dragSlots.add(pointed);
        }
        if (!released) {
            return;
        }
        if (!dragSlots.isEmpty()) {
            click(p, -999, AbstractContainerMenu.getQuickcraftMask(0, dragButton), ClickType.QUICK_CRAFT);
            for (Slot s : dragSlots) {
                click(p, s.index, AbstractContainerMenu.getQuickcraftMask(1, dragButton), ClickType.QUICK_CRAFT);
            }
            click(p, -999, AbstractContainerMenu.getQuickcraftMask(2, dragButton), ClickType.QUICK_CRAFT);
        } else if (pointed != null) {
            click(p, pointed.index, dragButton, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP);
        }
        dragButton = -1;
        dragSlots.clear();
    }

    private void click(StandIn p, int slot, int button, ClickType type) {
        screenIdle = 0;
        if (slot >= 0 && screen.getSlot(slot).hasItem()) {
            clicksOnThings++;
        }
        int before = contents(screen);
        screen.clicked(slot, button, type, p);
        screen.broadcastChanges();
        if (contents(screen) != before) {
            // Something moved: felt the less, the more it has lately been moving things about.
            movedThisTick += 1f / (1f + movedLately);
            movedLately += 1f;
            moves++;
        }
        if (slot >= 0) {
            screenClicks++;
        }
        noticeResult(screen);
    }

    /** A fingerprint of what lies where on a screen and on its pointer. */
    private static int contents(AbstractContainerMenu menu) {
        int h = ItemStack.hashItemAndComponents(menu.getCarried()) * 31 + menu.getCarried().getCount();
        for (Slot s : menu.slots) {
            ItemStack stack = s.getItem();
            h = h * 31 + (stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack) * 67 + stack.getCount());
        }
        return h;
    }

    private static boolean resultShowing(AbstractContainerMenu menu) {
        for (Slot s : menu.slots) {
            if (s instanceof ResultSlot && s.hasItem()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Something new appearing in a result slot after a click, the way planks show up as soon as a
     * log lands in the grid, is felt as a small pleasure: the first sign that the hands are making
     * something, as cracks are for digging. Felt once per kind of thing per screen, so putting the
     * same log in and out again does not keep paying; taking the result is what pays most.
     */
    private void noticeResult(AbstractContainerMenu menu) {
        ItemStack shown = ItemStack.EMPTY;
        for (Slot s : menu.slots) {
            if (s instanceof ResultSlot && s.hasItem()) {
                shown = s.getItem();
                break;
            }
        }
        if (!shown.isEmpty() && !resultShown && formedOnScreen.add(shown.getItem())) {
            formedThisTick++;
            formedTotal++;
        }
        resultShown = !shown.isEmpty();
    }

    /** Where a screen's slots lie, in its pixels, with a margin: min x, min y, max x, max y. */
    public static float[] bounds(AbstractContainerMenu menu) {
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (Slot s : menu.slots) {
            x0 = Math.min(x0, s.x);
            y0 = Math.min(y0, s.y);
            x1 = Math.max(x1, s.x + 16);
            y1 = Math.max(y1, s.y + 16);
        }
        if (x0 > x1) {
            return new float[] {0f, 0f, 176f, 166f};
        }
        return new float[] {x0 - 8f, y0 - 8f, x1 + 8f, y1 + 8f};
    }

    /** The slot whose 16x16 square, plus a player's one-pixel slack, holds this point. */
    public static Slot slotAt(AbstractContainerMenu menu, float x, float y) {
        for (Slot s : menu.slots) {
            if (s.isActive() && x >= s.x - 1 && x < s.x + 17 && y >= s.y - 1 && y < s.y + 17) {
                return s;
            }
        }
        return null;
    }
}
