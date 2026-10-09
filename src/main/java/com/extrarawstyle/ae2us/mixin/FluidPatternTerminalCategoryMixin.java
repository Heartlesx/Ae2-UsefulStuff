package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.PatternCategoryCapture;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = {
        "com.glodblock.github.client.container.ContainerFluidPatternTerminal",
        "com.glodblock.github.client.container.ContainerWirelessFluidPatternTerminal",
        "com.glodblock.github.client.container.ContainerExtendedFluidPatternTerminal"
}, remap = false)
public abstract class FluidPatternTerminalCategoryMixin {
    @Inject(method = "encode", at = @At("HEAD"))
    private void ae2us$replaceCircuitsBeforeEncoding(final CallbackInfo ci) {
        ((PatternCategoryCapture) this).ae2us$replaceCircuits();
    }

    @Inject(method = "acceptPattern", at = @At("TAIL"))
    private void ae2us$captureFluidTransfer(final Int2ObjectMap<ItemStack[]> inputs,
            final List<ItemStack> outputs, final boolean combine, final CallbackInfo ci) {
        ((PatternCategoryCapture) this).ae2us$captureTransferredRecipe();
    }

    @ModifyArg(method = "encodeFluidPattern", at = @At(value = "INVOKE",
            target = "Lappeng/container/slot/SlotRestrictedInput;func_75215_d(Lnet/minecraft/item/ItemStack;)V"), index = 0)
    private ItemStack ae2us$tagFluidPattern(final ItemStack stack) {
        return ((PatternCategoryCapture) this).ae2us$tagEncodedStack(stack);
    }
}
