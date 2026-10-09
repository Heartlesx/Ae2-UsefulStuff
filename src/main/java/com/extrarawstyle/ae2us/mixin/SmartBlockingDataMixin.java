package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.api.ISmartBlocking;
import appeng.helpers.DualityInterface;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keep saved interface settings even while the smart blocking feature is disabled.
@Mixin(value = DualityInterface.class, remap = false)
public abstract class SmartBlockingDataMixin implements ISmartBlocking {
    @Unique
    private static final String AE2US_NBT_KEY = "ae2us_smart_blocking";

    @Unique
    private boolean ae2us$smart;

    @Override
    public boolean ae2us$isSmartBlocking() {
        return this.ae2us$smart;
    }

    @Override
    public void ae2us$setSmartBlocking(final boolean value) {
        this.ae2us$smart = value;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void ae2us$writeToNBT(final NBTTagCompound data, final CallbackInfo ci) {
        data.setBoolean(AE2US_NBT_KEY, this.ae2us$smart);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void ae2us$readFromNBT(final NBTTagCompound data, final CallbackInfo ci) {
        this.ae2us$smart = data.getBoolean(AE2US_NBT_KEY);
    }
}
