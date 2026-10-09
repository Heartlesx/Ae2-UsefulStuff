package com.extrarawstyle.ae2us.mixin;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.NetworkMonitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkMonitor.class, remap = false)
public abstract class NetworkMonitorCraftablesMixin<T extends IAEStack<T>> {

    @Shadow
    private void notifyListenersOfChange(final Iterable<T> diff, final IActionSource source) {
        throw new AssertionError();
    }

    // AE2 updates the crafting cache here without notifying open terminals.
    @Inject(method = "updateCraftables", at = @At("TAIL"))
    private void ae2us$syncCraftables(final Iterable<T> changed, final IActionSource source, final CallbackInfo ci) {
        this.notifyListenersOfChange(changed, source);
    }
}
