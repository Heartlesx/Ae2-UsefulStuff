package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.PatternCategoryCapture;

import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
        "com.glodblock.github.client.container.ContainerFluidPatternTerminal",
        "com.glodblock.github.client.container.ContainerWirelessFluidPatternTerminal"
}, remap = false)
public abstract class FluidCraftPatternCategoryMixin {
    @Inject(method = "encodeFluidCraftPattern", at = @At("HEAD"))
    private void ae2us$replaceCircuitsBeforeFluidCraftEncoding(final CallbackInfo ci) {
        ((PatternCategoryCapture) this).ae2us$replaceCircuits();
    }

    @ModifyArg(method = "encodeFluidCraftPattern", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;func_77982_d(Lnet/minecraft/nbt/NBTTagCompound;)V"), index = 0)
    private NBTTagCompound ae2us$tagFluidCraftPattern(final NBTTagCompound tag) {
        return ((PatternCategoryCapture) this).ae2us$tagEncodedData(tag);
    }
}
