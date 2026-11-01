package com.extrarawstyle.ae2us.mixin;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.NetworkMonitor;
import com.extrarawstyle.ae2us.SubnetRefresh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkMonitor.class, remap = false)
public abstract class NetworkMonitorSubnetMixin<T extends IAEStack<T>> {
    // The early return discards a second filtered path, even if the first path delivered an empty diff.
    @Inject(method = "postChange", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    private void ae2us$refreshSkippedPath(final boolean add, final Iterable<T> changes,
            final IActionSource source, final CallbackInfo ci) {
        SubnetRefresh.request((NetworkMonitor<?>) (Object) this);
    }
}
