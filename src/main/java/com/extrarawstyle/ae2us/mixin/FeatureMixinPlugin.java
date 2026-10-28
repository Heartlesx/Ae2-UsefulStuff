package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.FeatureConfig;
import com.extrarawstyle.ae2us.compat.RecipeTransferButtonCompat;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.util.List;
import java.util.Set;

public final class FeatureMixinPlugin implements IMixinConfigPlugin {
    private static boolean loaded;

    @Override
    public void onLoad(final String mixinPackage) {
        if (!loaded) {
            FeatureConfig.load(new File((File) FMLInjectionData.data()[6], "config/ae2us.cfg"));
            loaded = true;
        }
        LogManager.getLogger("AE2US").info("Feature switches: RandomComplement={} autoAvoid={} smartBlocking={} terminalMarks={} recipeMarks={} patternRefill={} itemPin={}",
                FeatureConfig.randomComplementPresent, FeatureConfig.autoDisableRandomComplementConflicts,
                FeatureConfig.smartBlockingEnabled(), FeatureConfig.terminalCraftableMarksEnabled(),
                FeatureConfig.recipeCraftableMarksEnabled(), FeatureConfig.patternRefillEnabled(),
                FeatureConfig.craftedItemPinEnabled());
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        final String name = mixinClassName.substring("com.extrarawstyle.ae2us.mixin.".length());
        switch (name) {
            case "DualityInterfaceMixin":
            case "DualityInterfaceInvoker":
            case "ContainerInterfaceMixin":
            case "client.GuiInterfaceMixin":
            case "client.GuiUpgradeableAccessor":
                return FeatureConfig.smartBlockingEnabled();
            case "client.AEBaseGuiMixin":
                return FeatureConfig.terminalCraftableMarksEnabled();
            case "client.RecipeLayoutMixin":
                return FeatureConfig.recipeCraftableMarksEnabled();
            case "ContainerMEMonitorableMixin":
            case "NetworkMonitorCraftablesMixin":
                return FeatureConfig.terminalCraftableMarksEnabled() || FeatureConfig.recipeCraftableMarksEnabled();
            case "ContainerMEMonitorablePinMixin":
            case "CraftingCPUClusterRequesterAccessor":
            case "client.ItemRepoPinMixin":
            case "client.AEBaseGuiPinMixin":
                return FeatureConfig.craftedItemPinEnabled();
            case "compat.RecipeTransferButtonCompatMixin": {
                // 只在目标类确实缺 init 时才补；旧版 HEI 自带 init，硬加会方法冲突。
                final RecipeTransferButtonCompat.State state = RecipeTransferButtonCompat.state();
                LogManager.getLogger("AE2US").info("HEI RecipeTransferButton 兼容层：{}", state);
                return state == RecipeTransferButtonCompat.State.NEEDED;
            }
            case "client.RenderItemPatternPreviewMixin":
                // 产物预览修复：目标在原版类上，恒定启用。
                return true;
            default:
                return true;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName,
            final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName,
            final IMixinInfo mixinInfo) {
    }
}
