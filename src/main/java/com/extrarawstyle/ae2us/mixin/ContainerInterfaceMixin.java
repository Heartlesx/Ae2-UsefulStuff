package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.api.ISmartBlocking;
import com.extrarawstyle.ae2us.api.ISmartBlockingContainer;

import appeng.api.util.IConfigManager;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerInterface;
import appeng.helpers.DualityInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 界面上的开关状态：借用 AE2 自己的 @GuiSync 通道同步给客户端，服务端每 tick 写一次。
 * 二合一接口的容器是 ContainerInterface 的子类且没有重写 loadSettingsFromHost，所以它同样吃这套同步。
 */
@Mixin(value = ContainerInterface.class, remap = false)
public abstract class ContainerInterfaceMixin implements ISmartBlockingContainer {

    // AE2 已占用通道 0/1/3/4/5/6/7/8/9，二合一接口占用 95/96/97，这里用 10。
    @Unique
    @GuiSync(10)
    public boolean ae2us$smartBlocking;

    @Shadow
    @Final
    private DualityInterface myDuality;

    @Override
    public DualityInterface ae2us$getInterfaceDuality() {
        return this.myDuality;
    }

    @Override
    public boolean ae2us$isSmartBlockingEnabled() {
        return this.ae2us$smartBlocking;
    }

    @Inject(method = "loadSettingsFromHost", at = @At("TAIL"))
    private void ae2us$syncSmartBlocking(final IConfigManager cm, final CallbackInfo ci) {
        this.ae2us$smartBlocking = ((ISmartBlocking) this.myDuality).ae2us$isSmartBlocking();
    }
}
