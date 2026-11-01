package com.extrarawstyle.ae2us.mixin;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.me.GridAccessException;
import appeng.parts.misc.PartStorageBus;
import com.extrarawstyle.ae2us.SubnetRefresh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PartStorageBus.class, remap = false)
public abstract class StorageBusSubnetMixin {
    @Inject(method = "onListUpdate", at = @At("HEAD"))
    private void ae2us$forwardListUpdate(final CallbackInfo ci) {
        final PartStorageBus bus = (PartStorageBus) (Object) this;
        final AccessRestriction access = (AccessRestriction) bus.getConfigManager().getSetting(Settings.ACCESS);
        if (!bus.getProxy().isActive() || !access.hasPermission(AccessRestriction.READ)) { return; }
        try {
            SubnetRefresh.request(bus.getProxy().getStorage().getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)));
        } catch (final GridAccessException unavailable) {
            // A detached bus will be counted when its network becomes active again.
        }
    }
}
