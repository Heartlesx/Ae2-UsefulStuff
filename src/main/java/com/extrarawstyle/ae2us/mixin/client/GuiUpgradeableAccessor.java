package com.extrarawstyle.ae2us.mixin.client;

import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.container.implementations.ContainerUpgradeable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 取 GUI 里的容器实例，用于读智能阻挡的开关状态。 */
@Mixin(value = GuiUpgradeable.class, remap = false)
public interface GuiUpgradeableAccessor {

    @Accessor(value = "cvb", remap = false)
    ContainerUpgradeable ae2us$container();
}
