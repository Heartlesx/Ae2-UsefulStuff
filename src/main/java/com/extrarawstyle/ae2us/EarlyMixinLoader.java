package com.extrarawstyle.ae2us;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;
import zone.rong.mixinbooter.Context;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 把 mixins.ae2us.json 交给 MixinBooter，并且必须在最早阶段注册。
 * 原因：注入目标里有 Minecraft 自己的类（GuiScreen 等），它们在游戏启动早期就已经加载完毕，
 * 用 ILateMixinLoader 晚注册会直接抛 "MixinTargetAlreadyLoadedException: was loaded too early"。
 * <p>
 * 这个类同时充当 FML 的 coremod 入口，写在 jar 清单的 FMLCorePlugin 里，MixinBooter 才会收集它。
 */
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class EarlyMixinLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.ae2us.json");
    }

    @Override
    public boolean shouldMixinConfigQueue(final Context context) {
        FeatureConfig.randomComplementPresent |= context.isModPresent("random_complement");
        return true;
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(final Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
