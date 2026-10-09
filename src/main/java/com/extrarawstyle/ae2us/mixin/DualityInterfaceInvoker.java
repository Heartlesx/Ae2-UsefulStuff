package com.extrarawstyle.ae2us.mixin;

import appeng.helpers.DualityInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * DualityInterface 里判断"还有东西要发"的两个方法是私有的，用 Invoker 取出来。
 * 这两个方法决定接口在智能阻挡下还算不算"忙"。
 */
@Mixin(value = DualityInterface.class, remap = false)
public interface DualityInterfaceInvoker {

    @Invoker(value = "hasItemsToSend", remap = false)
    boolean ae2us$hasItemsToSend();

    @Invoker(value = "hasItemsToSendFacing", remap = false)
    boolean ae2us$hasItemsToSendFacing();
}
