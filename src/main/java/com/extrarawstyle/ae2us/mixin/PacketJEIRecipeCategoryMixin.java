package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.PatternCategoryCapture;

import appeng.container.implementations.ContainerPatternEncoder;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.core.sync.packets.PacketJEIRecipe;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PacketJEIRecipe.class, remap = false)
public abstract class PacketJEIRecipeCategoryMixin {
    @Unique private boolean ae2us$applied;

    @Inject(method = "serverPacketData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/inventory/Container;func_75130_a(Lnet/minecraft/inventory/IInventory;)V"))
    private void ae2us$appliedRecipe(final INetworkInfo manager, final AppEngPacket packet,
            final EntityPlayer player, final CallbackInfo ci) {
        ae2us$applied = true;
    }

    @Inject(method = "serverPacketData", at = @At("TAIL"))
    private void ae2us$captureRecipe(final INetworkInfo manager, final AppEngPacket packet,
            final EntityPlayer player, final CallbackInfo ci) {
        if (ae2us$applied && player.openContainer instanceof ContainerPatternEncoder) {
            ((PatternCategoryCapture) player.openContainer).ae2us$captureTransferredRecipe();
        }
    }
}
