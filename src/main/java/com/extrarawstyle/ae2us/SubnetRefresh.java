package com.extrarawstyle.ae2us;

import appeng.api.storage.IMEMonitor;
import appeng.me.cache.NetworkMonitor;
import com.extrarawstyle.ae2us.mixin.NetworkMonitorRefreshAccessor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Ae2us.MODID)
public final class SubnetRefresh {
    private static final Set<NetworkMonitor<?>> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static Set<NetworkMonitor<?>> refreshing;

    private SubnetRefresh() { }

    public static void request(final IMEMonitor<?> inventory) {
        if (inventory instanceof NetworkMonitor) {
            final NetworkMonitor<?> monitor = (NetworkMonitor<?>) inventory;
            if (refreshing == null || !refreshing.contains(monitor)) { PENDING.add(monitor); }
        }
    }

    @SubscribeEvent
    public static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) { return; }
        refreshing = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            while (!PENDING.isEmpty()) {
                final NetworkMonitor<?>[] batch = PENDING.toArray(new NetworkMonitor<?>[0]);
                PENDING.clear();
                for (final NetworkMonitor<?> monitor : batch) {
                    // Rebuilds read live inventories, so revisiting a network in this wave is unnecessary.
                    if (refreshing.add(monitor)) {
                        ((NetworkMonitorRefreshAccessor) monitor).ae2us$refreshStorage();
                    }
                }
            }
        } finally {
            refreshing = null;
        }
    }
}
