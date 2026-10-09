package com.extrarawstyle.ae2us.mixin.client;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.implementations.GuiCraftConfirm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 合成计划（下单）界面：把"有缺失"的物品排到材料列表最前面，不用自己翻找。
 *
 * 列表顺序本来只由服务器三个包（可用 / 待合成 / 缺失）到达的先后决定，跟"缺不缺"无关，
 * 所以缺的东西会散在中间或很后面。这里在每次 postUpdate 末尾做一次稳定分区：
 * 有缺失的往前搬，组内相对顺序不变。
 * 缺失清单只有模拟视图才有（AE2 真实下单那轮不发），那时这里就是空操作，列表顺序不变。
 * AE2FC 的流体版界面继承自这个类且没重写 postUpdate，所以两边一起生效。
 * 本工程不生成 refmap：postUpdate / missing / visual 都是 AE2 自己的成员名，原样写。
 */
@Mixin(value = GuiCraftConfirm.class, remap = false)
public abstract class GuiCraftConfirmMixin {

    @Shadow
    @Final
    private IItemList<IAEItemStack> missing;

    @Shadow
    @Final
    private List<IAEItemStack> visual;

    @Inject(method = "postUpdate", at = @At("TAIL"))
    private void ae2us$missingFirst(final List<IAEItemStack> list, final byte ref, final CallbackInfo ci) {
        // List.sort 是稳定排序：比较结果为 0 的条目保持原相对顺序。
        this.visual.sort((a, b) -> {
            final boolean missingA = ae2us$isMissing(a);
            return missingA == ae2us$isMissing(b) ? 0 : missingA ? -1 : 1;
        });
    }

    @Unique
    private boolean ae2us$isMissing(final IAEItemStack stack) {
        final IAEItemStack m = this.missing.findPrecise(stack);
        return m != null && m.getStackSize() > 0;
    }
}
