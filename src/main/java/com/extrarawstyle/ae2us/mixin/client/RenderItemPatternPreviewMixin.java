package com.extrarawstyle.ae2us.mixin.client;

import appeng.items.misc.ItemEncodedPattern;

import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修 AE2UEL"按住 Shift 显示样板产物图标"在部分产物上整格画空的问题。
 * <p>
 * AE2UEL 的做法（{@code ItemEncodedPatternBakedModel}）是把样板的模型换成产物物品的模型，
 * 然后仍用**样板这摞物品**去渲染。产物模型带自定义渲染（内置渲染器 / CodeChickenLib 的
 * IItemRenderer）时，渲染实现按"样板物品"去查自己要画什么——查不到，于是整格空白
 * （栏位悬停提示还在，因为数据没丢）。
 * <p>
 * 这一层挂在两参 {@code renderItem}（部分容器把已换好的模型直接交到这里）：只处理
 * "产物模型是 IItemRenderer 之外的自定义渲染"的兜底；CCL 的分支见
 * {@link CCRenderItemPatternPreviewMixin}。沿用调用方算好的模型（位置、缩放、视角都在里面，
 * 不重算），只把"样板这摞物品"换成产物物品本身。
 * <p>
 * 注解里的名字是运行时 SRG 名（本工程不生成 refmap）：
 * {@code func_191962_a} = renderItemModelIntoGUI，{@code func_180454_a} = renderItem(ItemStack, IBakedModel)。
 */
@Mixin(value = RenderItem.class, remap = false)
public abstract class RenderItemPatternPreviewMixin {

    @Inject(method = "func_191962_a(Lnet/minecraft/item/ItemStack;IILnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At("HEAD"), cancellable = true)
    private void ae2us$previewEncodedOutput(final ItemStack stack, final int x, final int y,
            final IBakedModel model, final CallbackInfo ci) {
        if (!model.isBuiltInRenderer() || !(stack.getItem() instanceof ItemEncodedPattern) || !ae2us$shiftDown()) {
            return;
        }
        final ItemStack output = ((ItemEncodedPattern) stack.getItem()).getOutput(stack);
        if (output.isEmpty() || output.getItem() instanceof ItemEncodedPattern) {
            return;
        }
        ((RenderItem) (Object) this).renderItemIntoGUI(output, x, y);
        ci.cancel();
    }

    @Inject(method = "func_180454_a(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At("HEAD"), cancellable = true)
    private void ae2us$previewTwoArgPath(final ItemStack stack, final IBakedModel model, final CallbackInfo ci) {
        if (!model.isBuiltInRenderer() || !(stack.getItem() instanceof ItemEncodedPattern) || !ae2us$shiftDown()) {
            return;
        }
        final ItemStack output = ((ItemEncodedPattern) stack.getItem()).getOutput(stack);
        if (output.isEmpty() || output.getItem() instanceof ItemEncodedPattern) {
            return;
        }
        ((RenderItem) (Object) this).renderItem(output, model);
        ci.cancel();
    }

    private static boolean ae2us$shiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}
