package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.compat.RecipeTransferButtonCompat;
import com.extrarawstyle.ae2us.mixin.FeatureMixinPlugin;
import com.extrarawstyle.ae2us.mixin.SmartBlockingDataMixin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zone.rong.mixinbooter.Context;

import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public final class FeatureConfigSelfCheck {
    public static void main(final String[] args) throws Exception {
        final Path directory = Files.createTempDirectory("ae2us-feature-check-");
        final File file = directory.resolve("ae2us.cfg").toFile();
        // Standalone checks do not run Forge's launcher, which normally supplies this path.
        final Field home = FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        final Object previousHome = home.get(null);
        home.set(null, directory.toFile());
        try {
            FeatureConfig.load(file);
            check(file.isFile(), "missing config is generated");
            check(FeatureConfig.enableSmartBlocking && FeatureConfig.enableTerminalCraftableMarks
                    && FeatureConfig.enableRecipeCraftableMarks && FeatureConfig.autoDisableRandomComplementConflicts,
                    "all features and auto avoidance default on");
            check(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains("crafted item pin"),
                    "RandomComplement comment names the crafted item pin");

            final Configuration config = new Configuration(file);
            config.load();
            config.get("features", "enableSmartBlocking", true).set(false);
            config.get("features", "enableTerminalCraftableMarks", true).set(false);
            config.get("features", "enableRecipeCraftableMarks", true).set(false);
            config.save();
            FeatureConfig.load(file);
            check(!FeatureConfig.enableSmartBlocking && !FeatureConfig.enableTerminalCraftableMarks
                    && !FeatureConfig.enableRecipeCraftableMarks, "disabled values load from disk");

            final FeatureMixinPlugin plugin = new FeatureMixinPlugin();
            for (int flags = 0; flags < 8; flags++) {
                FeatureConfig.enableSmartBlocking = (flags & 1) != 0;
                FeatureConfig.enableTerminalCraftableMarks = (flags & 2) != 0;
                FeatureConfig.enableRecipeCraftableMarks = (flags & 4) != 0;
                checkMixins(plugin, "mixins.ae2us.json");
                checkMixins(plugin, "mixins.ae2us.jei.json");
                checkMixins(plugin, "mixins.ae2us.ccl.json");
            }

            FeatureConfig.enableSmartBlocking = true;
            FeatureConfig.enableTerminalCraftableMarks = true;
            FeatureConfig.enableRecipeCraftableMarks = true;
            new EarlyMixinLoader().shouldMixinConfigQueue(new Context("mixins.ae2us.json",
                    Collections.singleton("random_complement")));
            check(FeatureConfig.randomComplementPresent, "early loader detects RandomComplement by mod ID");
            check(!FeatureConfig.smartBlockingEnabled() && !FeatureConfig.terminalCraftableMarksEnabled()
                    && !FeatureConfig.recipeCraftableMarksEnabled(), "installed RandomComplement disables all conflicts");
            checkMixins(plugin, "mixins.ae2us.json");
            checkMixins(plugin, "mixins.ae2us.jei.json");
            check(FeatureConfig.enableSmartBlocking && FeatureConfig.enableTerminalCraftableMarks
                    && FeatureConfig.enableRecipeCraftableMarks, "auto avoidance preserves manual settings");
            FeatureConfig.autoDisableRandomComplementConflicts = false;
            check(FeatureConfig.smartBlockingEnabled() && FeatureConfig.terminalCraftableMarksEnabled()
                    && FeatureConfig.recipeCraftableMarksEnabled(), "manual override restores selected features");
            FeatureConfig.autoDisableRandomComplementConflicts = true;
            FeatureConfig.randomComplementPresent = false;
            new EarlyMixinLoader().shouldMixinConfigQueue(new Context("mixins.ae2us.json", Collections.<String>emptySet()));
            check(FeatureConfig.smartBlockingEnabled(), "without RandomComplement manual settings apply");

            FeatureConfig.enableSmartBlocking = false;
            final SmartBlockingDataMixin original = new SmartBlockingDataMixin() { };
            original.ae2us$setSmartBlocking(true);
            final NBTTagCompound tag = new NBTTagCompound();
            invokeData(original, "ae2us$writeToNBT", tag);
            check(tag.getBoolean("ae2us_smart_blocking"), "disabled feature preserves old NBT key");
            final SmartBlockingDataMixin restored = new SmartBlockingDataMixin() { };
            invokeData(restored, "ae2us$readFromNBT", tag);
            check(restored.ae2us$isSmartBlocking(), "saved setting survives disabled feature");
            System.out.println("Feature config checks passed (8 switch combinations, auto avoidance and saved settings)");
        } finally {
            home.set(null, previousHome);
            Files.deleteIfExists(file.toPath());
            Files.delete(directory);
        }
    }

    private static void checkMixins(final FeatureMixinPlugin plugin, final String resource) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                FeatureConfigSelfCheck.class.getClassLoader().getResourceAsStream(resource), StandardCharsets.UTF_8)) {
            final JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            check(FeatureMixinPlugin.class.getName().equals(json.get("plugin").getAsString()),
                    "plugin registered in " + resource);
            for (final String section : new String[]{"mixins", "client"}) {
                final JsonArray mixins = json.getAsJsonArray(section);
                for (final JsonElement element : mixins) {
                    final String name = element.getAsString();
                    final boolean expected;
                    switch (name) {
                        case "DualityInterfaceMixin":
                        case "DualityInterfaceInvoker":
                        case "ContainerInterfaceMixin":
                        case "client.GuiInterfaceMixin":
                        case "client.GuiUpgradeableAccessor":
                            expected = FeatureConfig.smartBlockingEnabled();
                            break;
                        case "client.AEBaseGuiMixin":
                            expected = FeatureConfig.terminalCraftableMarksEnabled();
                            break;
                        case "client.RecipeLayoutMixin":
                            expected = FeatureConfig.recipeCraftableMarksEnabled();
                            break;
                        case "ContainerMEMonitorableMixin":
                        case "NetworkMonitorCraftablesMixin":
                            expected = FeatureConfig.terminalCraftableMarksEnabled() || FeatureConfig.recipeCraftableMarksEnabled();
                            break;
                        case "ContainerMEMonitorablePinMixin":
                        case "CraftingCPUClusterRequesterAccessor":
                        case "client.ItemRepoPinMixin":
                        case "client.AEBaseGuiPinMixin":
                            expected = FeatureConfig.craftedItemPinEnabled();
                            break;
                        case "compat.RecipeTransferButtonCompatMixin":
                            // 这个不由功能开关决定，而是看目标类缺不缺 init。期望值直接问同一个判定，
                            // 这样一旦插件漏了这个名字（落到 default 返回 true）就会失败。
                            expected = RecipeTransferButtonCompat.state() == RecipeTransferButtonCompat.State.NEEDED;
                            break;
                        // client.CCRenderItemPatternPreviewMixin 不列 case：CCL 目标用纯字符串声明
                        // （Mixin 的 virtual target），CCL 不在时 Mixin 自己按"目标未找到"静默跳过，
                        // 插件必须恒定放行。曾经用 Class.forName 探 CCL 是否存在，会把目标类毒化进
                        // LaunchClassLoader 的 invalidClasses（见 2026-10-07 dev15 崩溃），不许再加。
                        default:
                            expected = true;
                    }
                    check(plugin.shouldApplyMixin("unused", "com.extrarawstyle.ae2us.mixin." + name) == expected,
                            "mixin selection: " + name);
                }
            }
        }
    }

    private static void invokeData(final SmartBlockingDataMixin instance, final String name,
            final NBTTagCompound tag) throws Exception {
        final Method method = SmartBlockingDataMixin.class.getDeclaredMethod(name, NBTTagCompound.class, CallbackInfo.class);
        method.setAccessible(true);
        method.invoke(instance, tag, new CallbackInfo(name, false));
    }

    private static void check(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
