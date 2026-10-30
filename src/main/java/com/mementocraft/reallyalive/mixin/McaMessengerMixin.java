package com.mementocraft.reallyalive.mixin;

import com.mementocraft.reallyalive.body.BrainDriven;
import com.mementocraft.reallyalive.speech.Speech;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A villager with a brain says only what its brain says: MCA's stock lines (greetings, chat and
 * joke replies, reactions to gifts, chore and mourning remarks) are not sent for it. All of them
 * go through this one method of MCA's Messenger. No effect on villagers without a brain, or
 * without MCA.
 */
@Pseudo
@Mixin(targets = "net.conczin.mca.entity.ai.Messenger")
public interface McaMessengerMixin {
    @Inject(method = "sendChatMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void reallyalive$noStockLines(Component message, Entity receiver, CallbackInfoReturnable<Component> cir) {
        if (BrainDriven.isDriven(this)) {
            Speech.STOCK_LINES_KEPT_BACK.incrementAndGet();
            cir.setReturnValue(message);
        }
    }
}
