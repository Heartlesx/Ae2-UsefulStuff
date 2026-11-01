package com.extrarawstyle.ae2us.mixin;

import appeng.me.cache.NetworkMonitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = NetworkMonitor.class, remap = false)
public interface NetworkMonitorRefreshAccessor {
    @Invoker("forceUpdate")
    void ae2us$refreshStorage();
}
