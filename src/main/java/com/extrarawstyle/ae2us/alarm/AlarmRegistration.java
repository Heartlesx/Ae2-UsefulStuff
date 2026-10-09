package com.extrarawstyle.ae2us.alarm;

import appeng.api.AEApi;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
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

/** 库存报警器注册：方块、物品、TileEntity、配方与 TheOneProbe 集成入口。 */
@Mod.EventBusSubscriber(modid = Ae2us.MODID)
public final class AlarmRegistration {
    public static final int GUI_ID = 100;
    public static final BlockStorageAlarm BLOCK = new BlockStorageAlarm();
    public static final Item ITEM = new ItemBlock(BLOCK).setRegistryName(BLOCK.getRegistryName());

    private AlarmRegistration() { }

    public static void preInit() {
        GameRegistry.registerTileEntity(TileStorageAlarm.class, new ResourceLocation(Ae2us.MODID, "storage_alarm"));
        FMLInterModComms.sendFunctionMessage("theoneprobe", "getTheOneProbe",
                "com.extrarawstyle.ae2us.alarm.TopAlarmIntegration");
    }

    @SubscribeEvent
    public static void registerBlocks(final RegistryEvent.Register<Block> event) {
        event.getRegistry().register(BLOCK);
    }

    @SubscribeEvent
    public static void registerItems(final RegistryEvent.Register<Item> event) {
        event.getRegistry().register(ITEM);
        ITEM.setCreativeTab(CreativeTabs.REDSTONE);
    }

    @SubscribeEvent
    public static void registerRecipes(final RegistryEvent.Register<IRecipe> event) {
        final ResourceLocation name = new ResourceLocation(Ae2us.MODID, "me_storage_alarm");
        event.getRegistry().register(new ShapelessOreRecipe(name, new ItemStack(BLOCK),
                AEApi.instance().definitions().parts().storageMonitor().maybeStack(1).get(),
                new ItemStack(Blocks.REDSTONE_TORCH),
                AEApi.instance().definitions().materials().logicProcessor().maybeStack(1).get()).setRegistryName(name));
    }
}
