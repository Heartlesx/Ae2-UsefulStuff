package com.extrarawstyle.ae2us.mirror;

import appeng.api.AEApi;
import appeng.api.parts.IPartItem;
import appeng.core.CreativeTab;
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
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.ShapelessOreRecipe;

@Mod.EventBusSubscriber(modid = Ae2us.MODID)
public final class MirrorRegistration {

    public static final Item PROVIDER = new ProviderItem();
    public static final Item BINDER = new ItemMirrorBinder();

    private MirrorRegistration() {
    }

    public static void preInit() {
        AEApi.instance().registries().partModels().registerModels(
                PartMirrorProvider.MODEL_OFF, PartMirrorProvider.MODEL_ON, PartMirrorProvider.MODEL_CHANNEL);
        FMLInterModComms.sendFunctionMessage("theoneprobe", "getTheOneProbe",
                "com.extrarawstyle.ae2us.mirror.TopMirrorIntegration");
    }

    @SubscribeEvent
    public static void registerItems(final RegistryEvent.Register<Item> event) {
        event.getRegistry().register(PROVIDER);
        event.getRegistry().register(BINDER);
    }

    @SubscribeEvent
    public static void registerRecipes(final RegistryEvent.Register<IRecipe> event) {
        // 镜像样板供应器 = 接口 + 逻辑处理器 + 石英纤维（只读、不带料，所以比接口便宜）
        final ResourceLocation providerName = new ResourceLocation(Ae2us.MODID, "mirror_pattern_provider");
        event.getRegistry().register(new ShapelessOreRecipe(providerName,
                new ItemStack(PROVIDER),
                AEApi.instance().definitions().parts().iface().maybeStack(1).orElse(ItemStack.EMPTY),
                AEApi.instance().definitions().materials().logicProcessor().maybeStack(1).orElse(ItemStack.EMPTY),
                AEApi.instance().definitions().materials().cardPatternExpansion().maybeStack(1).orElse(ItemStack.EMPTY))
                .setRegistryName(providerName));

        // 绑定器 = 石英切割刀 + 逻辑处理器
        final ResourceLocation binderName = new ResourceLocation(Ae2us.MODID, "mirror_binder");
        event.getRegistry().register(new ShapelessOreRecipe(binderName,
                new ItemStack(BINDER),
                AEApi.instance().definitions().items().memoryCard().maybeStack(1).orElse(ItemStack.EMPTY),
                AEApi.instance().definitions().materials().logicProcessor().maybeStack(1).orElse(ItemStack.EMPTY))
                .setRegistryName(binderName));
    }

    private static CreativeTabs tab() {
        return CreativeTab.instance != null ? CreativeTab.instance : CreativeTabs.REDSTONE;
    }

    private static final class ProviderItem extends Item implements IPartItem<PartMirrorProvider> {
        private ProviderItem() {
            this.setRegistryName(Ae2us.MODID, "mirror_pattern_provider");
            this.setTranslationKey("ae2us.mirror_pattern_provider");
            this.setCreativeTab(tab());
        }

        @Override
        public PartMirrorProvider createPartFromItemStack(final ItemStack stack) {
            return new PartMirrorProvider(stack);
        }

        @Override
        public EnumActionResult onItemUse(final EntityPlayer player, final World world, final BlockPos pos,
                final EnumHand hand, final EnumFacing side, final float x, final float y, final float z) {
            return AEApi.instance().partHelper().placeBus(player.getHeldItem(hand), pos, side, player, hand, world);
        }
    }
}
