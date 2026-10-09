package com.extrarawstyle.ae2us.mixin;

import appeng.api.config.StorageFilter;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;
import com.extrarawstyle.ae2us.FilteredSubnetScan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MEInventoryHandler.class, remap = false)
public abstract class FilteredMEInventoryMixin<T extends IAEStack<T>> {
    @Shadow public abstract StorageFilter getStorageFilter();
    @Shadow protected abstract boolean shouldItemBeAvailable(T stack);

    @Redirect(method = "getAvailableItems", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/IMEInventoryHandler;getAvailableItems(Lappeng/api/storage/data/IItemList;)Lappeng/api/storage/data/IItemList;"), require = 2)
    private IItemList<T> ae2us$carryFilter(final IMEInventoryHandler<T> internal, final IItemList<T> out) {
        return getStorageFilter() == StorageFilter.EXTRACTABLE_ONLY
                ? FilteredSubnetScan.filtered(internal, out, this::shouldItemBeAvailable)
                : internal.getAvailableItems(out);
    }
}
