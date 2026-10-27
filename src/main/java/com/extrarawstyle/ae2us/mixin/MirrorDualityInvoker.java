package com.extrarawstyle.ae2us.mixin;

import appeng.helpers.DualityInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 镜像一次性写完样板槽之后要手动刷新配方表（批量写入时回调被临时关掉了）。
 */
@Mixin(value = DualityInterface.class, remap = false)
public interface MirrorDualityInvoker {

    @Invoker(value = "updateCraftingList", remap = false)
    void ae2us$updateCraftingList();
}
