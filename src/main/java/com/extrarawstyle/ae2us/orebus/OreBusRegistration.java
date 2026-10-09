package com.extrarawstyle.ae2us.orebus;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.parts.IPartItem;
import appeng.api.util.AEPartLocation;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapelessOreRecipe;

@Mod.EventBusSubscriber(modid = Ae2us.MODID)
public final class OreBusRegistration {
    public static final Item BUS = new BusItem();
    public static final Item STORAGE_BUS = new StorageBusItem();
    static int oreRevision;

    private OreBusRegistration() { }

    public static void preInit() {
        AEApi.instance().registries().partModels().registerModels(PartOreExportBus.OFF, PartOreExportBus.ON, PartOreExportBus.CHANNEL);
        AEApi.instance().registries().partModels().registerModels(PartOreStorageBus.OFF, PartOreStorageBus.ON, PartOreStorageBus.CHANNEL);
    }

    @SubscribeEvent
    public static void registerItems(final RegistryEvent.Register<Item> event) {
        event.getRegistry().register(BUS);
        event.getRegistry().register(STORAGE_BUS);
        Upgrades.STICKY.registerItem(new ItemStack(STORAGE_BUS), 1);
        Upgrades.SPEED.registerItem(new ItemStack(BUS), 4);
        Upgrades.REDSTONE.registerItem(new ItemStack(BUS), 1);
    }

    @SubscribeEvent
    public static void registerRecipes(final RegistryEvent.Register<IRecipe> event) {
        final ResourceLocation storageName = new ResourceLocation(Ae2us.MODID, "ore_storage_bus");
        event.getRegistry().register(new ShapelessOreRecipe(storageName, new ItemStack(STORAGE_BUS),
                AEApi.instance().definitions().parts().storageBus().maybeStack(1).get(),
                AEApi.instance().definitions().materials().logicProcessor().maybeStack(1).get(),
                AEApi.instance().definitions().materials().cardCapacity().maybeStack(1).get()).setRegistryName(storageName));
        final ResourceLocation name = new ResourceLocation(Ae2us.MODID, "ore_export_bus");
        event.getRegistry().register(new ShapelessOreRecipe(name, new ItemStack(BUS),
                AEApi.instance().definitions().parts().exportBus().maybeStack(1).get(),
                AEApi.instance().definitions().materials().logicProcessor().maybeStack(1).get(),
                AEApi.instance().definitions().materials().cardCapacity().maybeStack(1).get()).setRegistryName(name));
    }

    @SubscribeEvent
    public static void oreRegistered(final OreDictionary.OreRegisterEvent event) { oreRevision++; }

    public static PartOreExportBus find(final World world, final int side, final BlockPos pos) {
        final Object part = findPart(world, side, pos);
        return part instanceof PartOreExportBus ? (PartOreExportBus) part : null;
    }

    public static appeng.parts.automation.PartUpgradeable findPart(final World world, final int side, final BlockPos pos) {
        if (side < 0 || side >= AEPartLocation.values().length || !world.isBlockLoaded(pos)) { return null; }
        final Object part = AEApi.instance().partHelper().getPart(world, pos, AEPartLocation.values()[side]);
        return part instanceof PartOreExportBus || part instanceof PartOreStorageBus
                ? (appeng.parts.automation.PartUpgradeable) part : null;
    }

    private static final class StorageBusItem extends Item implements IPartItem<PartOreStorageBus> {
        private StorageBusItem() {
            this.setRegistryName(Ae2us.MODID, "ore_storage_bus");
            this.setTranslationKey("ae2us.ore_storage_bus");
            this.setCreativeTab(CreativeTabs.REDSTONE);
        }

        @Override
        public PartOreStorageBus createPartFromItemStack(final ItemStack stack) { return new PartOreStorageBus(stack); }

        @Override
        public EnumActionResult onItemUse(final EntityPlayer player, final World world, final BlockPos pos,
                                          final EnumHand hand, final EnumFacing side, final float x, final float y, final float z) {
            return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, side, player, hand, world);
        }
    }

    private static final class BusItem extends Item implements IPartItem<PartOreExportBus> {
        private BusItem() {
            this.setRegistryName(Ae2us.MODID, "ore_export_bus");
            this.setTranslationKey("ae2us.ore_export_bus");
            this.setCreativeTab(CreativeTabs.REDSTONE);
        }

        @Override
        public PartOreExportBus createPartFromItemStack(final ItemStack stack) { return new PartOreExportBus(stack); }

        @Override
        public EnumActionResult onItemUse(final EntityPlayer player, final World world, final BlockPos pos,
                                          final EnumHand hand, final EnumFacing side, final float x, final float y, final float z) {
            return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, side, player, hand, world);
        }
    }
}
