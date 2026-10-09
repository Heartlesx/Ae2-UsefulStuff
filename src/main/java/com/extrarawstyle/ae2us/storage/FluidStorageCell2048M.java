package com.extrarawstyle.ae2us.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.helper.FluidCellConfig;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** 2048M 流体存储元件：256 类型 / 2,147,483,647 字节。暂无配方，创造模式获取。 */
public final class FluidStorageCell2048M extends AbstractSuperStorageCell<IAEFluidStack> {

    public FluidStorageCell2048M() {
        this.setRegistryName(Ae2us.MODID, "fluid_storage_cell_2048m");
        this.setTranslationKey("ae2us.fluid_storage_cell_2048m");
    }

    @Override
    public IStorageChannel<IAEFluidStack> getChannel() {
        return AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
    }

    @Override
    public IItemHandler getConfigInventory(final ItemStack is) {
        return new FluidCellConfig(is);
    }
}
