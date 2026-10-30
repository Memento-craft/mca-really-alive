package com.mementocraft.reallyalive.mixin;

import com.mementocraft.reallyalive.body.Watching;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clicks on crafting screens are seen by villagers watching, as hand moves. Changes nothing. */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"))
    private void reallyalive$watchClick(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        Watching.clicked((AbstractContainerMenu) (Object) this, slot, button, type, player);
    }
}
