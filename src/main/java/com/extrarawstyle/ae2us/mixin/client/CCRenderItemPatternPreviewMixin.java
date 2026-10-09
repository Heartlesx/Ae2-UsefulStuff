package com.extrarawstyle.ae2us.mixin.client;

import appeng.items.misc.ItemEncodedPattern;

import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * CodeChickenLib 把 {@code Minecraft.renderItem} 换成 {@code CCRenderItem}，它的两参 renderItem
 * 对 {@code IItemRenderer} 模型走专属分支：{@code ((IItemRenderer) model).renderItem(stack, type)}。
 * AE2UEL 的 shift 预览把模型换成产物模型后，这里的 stack 还是**样板**——gtlitecore 的特殊物品
 * 渲染器（Transcendent/Glitch 等）拿到样板什么也画不出来，整格空白。
 * <p>
 * 修法：把传给 {@code IItemRenderer.renderItem} 的 stack 换成产物本身——等价于"这一格正常摆着
 * 产物"的渲染（glow、动画、视角都由产物自己的渲染器处理）。CCL 不在本模组编译依赖里，目标全用
 * 字符串（Mixin 的 virtual target）：CCL 不在时该类永不加载、Mixin 自己静默跳过。不要在插件里用
 * Class.forName 探测 CCL 是否存在——LaunchClassLoader 读不到 mod 类，会把目标类毒化进它的
 * invalidClasses，之后 CCL 自己再也加载不了这个类（2026-10-07 dev15 启动崩溃的根因）。
 */
@Mixin(targets = "codechicken.lib.render.item.CCRenderItem", remap = false)
public abstract class CCRenderItemPatternPreviewMixin {

    @ModifyArg(method = "func_180454_a(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At(value = "INVOKE",
                    target = "Lcodechicken/lib/render/item/IItemRenderer;renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V"),
            index = 0)
    private ItemStack ae2us$previewIItemRendererOutput(final ItemStack stack) {
        if (!(stack.getItem() instanceof ItemEncodedPattern)
                || !(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))) {
            return stack;
        }
        final ItemStack output = ((ItemEncodedPattern) stack.getItem()).getOutput(stack);
        if (output.isEmpty() || output.getItem() instanceof ItemEncodedPattern) {
            return stack;
        }
        return output;
    }
}
