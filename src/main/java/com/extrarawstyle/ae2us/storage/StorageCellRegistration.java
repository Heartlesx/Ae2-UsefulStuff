package com.extrarawstyle.ae2us.storage;

import appeng.api.config.Upgrades;
import appeng.core.CreativeTab;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** 2048M 存储元件（物品 + 流体）的注册。暂无配方，创造模式获取。 */
@Mod.EventBusSubscriber(modid = Ae2us.MODID)
public final class StorageCellRegistration {
    public static final Item ITEM_CELL = new StorageCell2048M();
    public static final Item FLUID_CELL = new FluidStorageCell2048M();

    private StorageCellRegistration() {
    }

    public static void preInit() {
        // 放 AE2 自己的创造页，和原版存储元件挨着；AE2 尚未初始化时退回本模组惯用的红石页。
        final CreativeTabs tab = CreativeTab.instance != null ? CreativeTab.instance : CreativeTabs.REDSTONE;
        ITEM_CELL.setCreativeTab(tab);
        FLUID_CELL.setCreativeTab(tab);
    }

    @SubscribeEvent
    public static void registerItems(final RegistryEvent.Register<Item> event) {
        event.getRegistry().register(ITEM_CELL);
        event.getRegistry().register(FLUID_CELL);
        // 跟原版元件一样允许装模糊卡 / 反向卡 / 粘性卡，否则工作台的升级槽会拒收。
        Upgrades.FUZZY.registerItem(new ItemStack(ITEM_CELL), 1);
        Upgrades.INVERTER.registerItem(new ItemStack(ITEM_CELL), 1);
        Upgrades.STICKY.registerItem(new ItemStack(ITEM_CELL), 1);
        Upgrades.FUZZY.registerItem(new ItemStack(FLUID_CELL), 1);
        Upgrades.INVERTER.registerItem(new ItemStack(FLUID_CELL), 1);
        Upgrades.STICKY.registerItem(new ItemStack(FLUID_CELL), 1);
    }
}
