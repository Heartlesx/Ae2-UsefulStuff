package com.extrarawstyle.ae2us;

import zone.rong.mixinbooter.ILateMixinLoader;
import zone.rong.mixinbooter.MixinLoader;
import zone.rong.mixinbooter.Context;

import java.util.Collections;
import java.util.List;

/**
 * JEI（本整合包是 HadEnoughItems）的 mixin 必须走 late loader：
 * 早期 coremod 注册配置时，整合包 mods 目录里的 jar 还没挂到类加载器上，
 * Mixin 会因为找不到 mezz.jei.gui.recipes.RecipeLayout 而把这个类标记成"加载失败"，
 * 之后 HEI 真正要用它时就会 NoClassDefFoundError 崩溃（1.1.2 的实际崩溃原因）。
 * MixinBooter 会扫描带 @MixinLoader 注解的类，实现 ILateMixinLoader 的在 mods 加载完之后才注册配置。
 */
@MixinLoader
public class JeiMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.ae2us.jei.json");
    }

    @Override
    public boolean shouldMixinConfigQueue(final Context context) {
        FeatureConfig.randomComplementPresent |= context.isModPresent("random_complement");
        return true;
    }
}
