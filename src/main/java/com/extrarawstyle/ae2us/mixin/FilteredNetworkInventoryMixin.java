package com.extrarawstyle.ae2us.mixin;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.NetworkInventoryHandler;
import com.extrarawstyle.ae2us.FilteredSubnetScan;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Deque;
import java.util.List;
import java.util.NavigableMap;

@Mixin(value = NetworkInventoryHandler.class, remap = false)
public abstract class FilteredNetworkInventoryMixin<T extends IAEStack<T>> {
    @Shadow @Final private IStorageChannel<T> myChannel;
    @Shadow @Final private NavigableMap<Integer, List<IMEInventoryHandler<T>>> priorityInventory;
    @Shadow @Final private NavigableMap<Integer, List<IMEInventoryHandler<T>>> stickyPriorityInventory;
    @Shadow @Final private NavigableMap<Integer, List<IMEInventoryHandler<T>>> craftingPriorityInventory;
    @Shadow private Deque<Object> getDepth(final Actionable type) { throw new AssertionError(); }

    @Inject(method = "getAvailableItems", at = @At("HEAD"), cancellable = true)
    private void ae2us$scanFilteredSources(final IItemList<T> out, final CallbackInfoReturnable<IItemList<T>> cir) {
        cir.setReturnValue(FilteredSubnetScan.scan(this, myChannel, getDepth(Actionable.SIMULATE), out,
                priorityInventory.values(), stickyPriorityInventory.values(), craftingPriorityInventory.values()));
    }
}
