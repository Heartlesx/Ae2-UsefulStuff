package com.extrarawstyle.ae2us;

import zone.rong.mixinbooter.ILateMixinLoader;
import zone.rong.mixinbooter.MixinLoader;

import java.util.Collections;
import java.util.List;

/**
 * CodeChickenLib 的 mixin 必须走 late loader：CCL 不是 coremod，早期 coremod 注册配置时
 * mods 目录里的 jar 还没挂到类加载器上，Mixin 会读不到目标类字节、把这次失败缓存进
 * LaunchClassLoader 的 negativeResourceCache，之后 CCL 自己加载该类时拿到缓存的 null 直接崩
 * （dev15/dev16 两次启动崩溃的根因，与 {@link JeiMixinLoader} 记录的 1.1.2 崩溃同源）。
 * late loader 在 mods 加载完之后才注册配置，那时 mod 类可正常读取。
 */
@MixinLoader
public class CclMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.ae2us.ccl.json");
    }
}
