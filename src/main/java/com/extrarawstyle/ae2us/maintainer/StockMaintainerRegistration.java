package com.extrarawstyle.ae2us.maintainer;

import appeng.core.CreativeTab;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import java.util.ArrayList;
import java.util.List;

/** ME库存维持器注册：方块、物品、瓦片与无序合成配方。 */
@Mod.EventBusSubscriber(modid = Ae2us.MODID)
public final class StockMaintainerRegistration {
    public static final int GUI_ID = 101;
    public static final BlockStockMaintainer BLOCK = new BlockStockMaintainer();
    public static final Item ITEM = new ItemBlock(BLOCK).setRegistryName(BLOCK.getRegistryName());

    private StockMaintainerRegistration() { }

    public static void preInit() {
        GameRegistry.registerTileEntity(TileStockMaintainer.class, new ResourceLocation(Ae2us.MODID, "stock_maintainer"));
        FMLInterModComms.sendFunctionMessage("theoneprobe", "getTheOneProbe",
                "com.extrarawstyle.ae2us.maintainer.TopMaintainerIntegration");
        // 放 AE2 自己的创造页，和接口、发信器挨着；AE2 尚未初始化时退回本模组惯用的红石页。
        final CreativeTabs tab = CreativeTab.instance != null ? CreativeTab.instance : CreativeTabs.REDSTONE;
        ITEM.setCreativeTab(tab);
    }

    @SubscribeEvent
    public static void registerBlocks(final RegistryEvent.Register<Block> event) {
        event.getRegistry().register(BLOCK);
    }

    @SubscribeEvent
    public static void registerItems(final RegistryEvent.Register<Item> event) {
        event.getRegistry().register(ITEM);
    }

    @SubscribeEvent
    public static void registerRecipes(final RegistryEvent.Register<IRecipe> event) {
        final List<Object> inputs = new ArrayList<>();
        for (final ItemStack stack : BlockStockMaintainer.recipeInputs()) {
            if (!stack.isEmpty()) { inputs.add(stack); }
        }
        // 材料缺一件就不注册配方：宁可没有配方，也不产出用不了的材料清单。
        if (inputs.size() < 4) { return; }
        final ResourceLocation name = new ResourceLocation(Ae2us.MODID, "me_stock_maintainer");
        // 输出写在构造器里，材料按可变参数追加；材料用 AE2 definitions 取，不硬编码旧 metadata。
        final ItemStack output = new ItemStack(BLOCK);
        final Object[] recipe = inputs.toArray();
        event.getRegistry().register(new ShapelessOreRecipe(name, output, recipe).setRegistryName(name));
    }
}
