package com.extrarawstyle.ae2us.mixin.client;

import com.extrarawstyle.ae2us.client.PatternUploadClient;
import mezz.jei.input.InputHandler;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 上传 / 定位覆盖层打开时，HEI 不再处理该屏幕的鼠标输入。
 *
 * <p>HEI 4.35 的 InputHandler.onGuiMouseEvent 是 receiveCanceled = true 的 HIGHEST 订阅者：
 * 覆盖层在 MouseInputEvent.Pre 里的 setCanceled 拦不住它，按定位按钮时若按钮底下恰好是
 * HEI 物品列表里的格子，会顺带打开该物品的配方查询（Config.mouseClickToSeeRecipe）。
 * 键盘不受影响——HEI 的键盘入口只在它自己获得键盘焦点时才处理。</p>
 *
 * <p>在 handleMouseEvent 入口让整个鼠标事件空转（含滚轮与拖拽跟进），与覆盖层
 * "打开期间吞掉本屏幕全部鼠标输入"的既有行为一致。</p>
 */
@Mixin(value = InputHandler.class, remap = false)
public abstract class InputHandlerMixin {
    @Inject(method = "handleMouseEvent", at = @At("HEAD"), cancellable = true)
    private void ae2us$ignoreUnderUploadOverlay(final GuiScreen guiScreen, final int mouseX, final int mouseY,
            final CallbackInfoReturnable<Boolean> cir) {
        if (PatternUploadClient.blocksHeiMouseInput(guiScreen)) {
            cir.setReturnValue(false);
        }
    }
}
