package com.mementocraft.reallyalive.body;

import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mementocraft.reallyalive.brain.Motives;
import com.mementocraft.reallyalive.brain.SoundChannel;
import com.mementocraft.reallyalive.sense.Hearing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * A player's two mouse buttons for a brain-driven villager. Attack held on the block in the
 * crosshair digs it at a player's dig speed with the held tool, and progress is lost when the button
 * is released or the crosshair moves to another block; a click on a creature hits it. Use places the
 * held block against the face in the crosshair, repeating every 4 ticks while held. Reach, dig speed,
 * the 5-tick pause after breaking a block and attack cooldown all follow the player rules (the
 * client's MultiPlayerGameMode and Player). What it digs goes to its hand or inventory.
 */
public final class Hands {
    /** A survival player's reach for blocks and for creatures. */
    public static final double BLOCK_REACH = 4.5;
    public static final double ENTITY_REACH = 3.0;
    private static final int BREAK_PAUSE = 5;
    private static final int USE_REPEAT = 4;

    private final Villager body;
    private boolean attackWasDown;
    private boolean useWasDown;
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
     * further, and each block placed. Call once per tick, after the body has moved.
     */
    public float motivePleasure() {
        float pleasure = 0f;
        int now = countCarried();
        if (carried >= 0 && now > carried) {
            pleasure += Motives.COLLECT;
        }
        carried = now;
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

    /** Presses or releases the buttons for this tick. Runs on the server thread before the body moves. */
    public void tick(MotorCommand keys) {
        sinceSwing++;
        if (useDelay > 0) {
            useDelay--;
        }
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
        if (use && (!useWasDown || useDelay == 0)) {
            useDelay = USE_REPEAT;
            place();
        }
        useWasDown = use;
    }

    /** Lets go of everything, for when the brain hands the body back. */
    public void release() {
        stopDigging();
        attackWasDown = false;
        useWasDown = false;
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

    /** Places the held block against the face in the crosshair, never inside a creature. */
    private void place() {
        ItemStack held = body.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem blockItem) || !(target instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || !EventHooks.canEntityGrief(body.level(), body)) {
            return;
        }
        if (blockItem.place(new VillagerPlaceContext(body, held, hit)).consumesAction()) {
            body.swing(InteractionHand.MAIN_HAND);
            placedThisTick++;
            refillHand(blockItem);
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

    /** A block placement made by a villager: facing and sneaking come from its own look and keys. */
    private static final class VillagerPlaceContext extends BlockPlaceContext {
        private final Villager placer;

        VillagerPlaceContext(Villager placer, ItemStack stack, BlockHitResult hit) {
            super(placer.level(), null, InteractionHand.MAIN_HAND, stack, hit);
            this.placer = placer;
        }

        @Override
        public Direction getHorizontalDirection() {
            return placer.getDirection();
        }

        @Override
        public float getRotation() {
            return placer.getYRot();
        }

        @Override
        public boolean isSecondaryUseActive() {
            return placer.isShiftKeyDown();
        }

        @Override
        public Direction getNearestLookingDirection() {
            return Direction.orderedByNearest(placer)[0];
        }

        @Override
        public Direction getNearestLookingVerticalDirection() {
            return placer.getXRot() < 0f ? Direction.UP : Direction.DOWN;
        }

        @Override
        public Direction[] getNearestLookingDirections() {
            Direction[] order = Direction.orderedByNearest(placer);
            if (replacingClickedOnBlock()) {
                return order;
            }
            Direction against = getClickedFace().getOpposite();
            int i = 0;
            while (i < order.length && order[i] != against) {
                i++;
            }
            if (i > 0 && i < order.length) {
                System.arraycopy(order, 0, order, 1, i);
                order[0] = against;
            }
            return order;
        }
    }
}
