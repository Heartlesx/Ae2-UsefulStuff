package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.PatternRefill;
import com.extrarawstyle.ae2us.api.IPatternRefillHost;

import appeng.parts.reporting.AbstractPartEncoder;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把自动补充开关存进样板终端部件（普通/扩展以及 Fluid Craft for AE2 的流体终端部件都继承自这个基类）。
 * 和智能阻挡一样，无论功能开关是否打开都照常读写，关掉功能不会丢设置。
 */
@Mixin(value = AbstractPartEncoder.class, remap = false)
public abstract class PatternRefillHostMixin implements IPatternRefillHost {

    @Unique
    private boolean ae2us$patternRefill = true;

    @Override
    public boolean ae2us$isPatternRefill() {
        return this.ae2us$patternRefill;
    }

    @Override
    public void ae2us$setPatternRefill(final boolean value) {
        this.ae2us$patternRefill = value;
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void ae2us$readFromNBT(final NBTTagCompound data, final CallbackInfo ci) {
        this.ae2us$patternRefill = PatternRefill.read(data);
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void ae2us$writeToNBT(final NBTTagCompound data, final CallbackInfo ci) {
        PatternRefill.write(data, this.ae2us$patternRefill);
    }
}
