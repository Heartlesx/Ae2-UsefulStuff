package com.extrarawstyle.ae2us.parallel;

import appeng.core.CreativeTab;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/** 六档高级并行处理单元的注册：方块、物品、瓦片。本版暂无配方，靠创造模式获取。 */
@Mod.EventBusSubscriber(modid = Ae2us.MODID)
public final class ParallelUnitRegistration {
    public static final BlockParallelUnit[] BLOCKS = new BlockParallelUnit[ParallelTier.values().length];
    public static final Item[] ITEMS = new Item[BLOCKS.length];

    static {
        final ParallelTier[] tiers = ParallelTier.values();
        for (int i = 0; i < tiers.length; i++) {
            BLOCKS[i] = new BlockParallelUnit(tiers[i]);
            ITEMS[i] = new ItemBlock(BLOCKS[i]).setRegistryName(BLOCKS[i].getRegistryName());
        }
    }

    private ParallelUnitRegistration() {
    }

    public static void preInit() {
        GameRegistry.registerTileEntity(TileParallelUnit.class, new ResourceLocation(Ae2us.MODID, "parallel_unit"));
        // 放 AE2 自己的创造页，和原版合成单元挨着；AE2 尚未初始化时退回本模组惯用的红石页。
        final CreativeTabs tab = CreativeTab.instance != null ? CreativeTab.instance : CreativeTabs.REDSTONE;
        for (final Item item : ITEMS) {
            item.setCreativeTab(tab);
        }
    }

    @SubscribeEvent
    public static void registerBlocks(final RegistryEvent.Register<Block> event) {
        for (final BlockParallelUnit block : BLOCKS) {
            event.getRegistry().register(block);
        }
    }

    @SubscribeEvent
    public static void registerItems(final RegistryEvent.Register<Item> event) {
        for (final Item item : ITEMS) {
            event.getRegistry().register(item);
        }
    }
}
