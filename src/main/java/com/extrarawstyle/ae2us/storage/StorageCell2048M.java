package com.extrarawstyle.ae2us.storage;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.items.contents.CellConfig;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** 2048M 物品存储元件：256 类型 / 2,147,483,647 字节。暂无配方，创造模式获取。 */
public final class StorageCell2048M extends AbstractSuperStorageCell<IAEItemStack> {

    public StorageCell2048M() {
        this.setRegistryName(Ae2us.MODID, "storage_cell_2048m");
        this.setTranslationKey("ae2us.storage_cell_2048m");
    }

    @Override
    public IStorageChannel<IAEItemStack> getChannel() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @Override
    public IItemHandler getConfigInventory(final ItemStack is) {
        return new CellConfig(is);
    }
}
