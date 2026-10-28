package com.extrarawstyle.ae2us.mixin.client;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.implementations.GuiCraftingCPU;
import com.extrarawstyle.ae2us.client.ActiveFirstOrder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 合成情况界面：正在合成（绿色）排最前，计划合成（黄色）排第二，其余保持原顺序。
 *
 * 列表顺序本来只由服务器先后到达的物品决定，跟「跑到哪一步」无关，大单时正在跑
 * 的东西会散在各页。这里在每次 postUpdate 末尾做一次三档稳定分区：
 * 档位之间搬动，同档内部以及未分档条目之间的相对顺序都不动。
 *
 * 档位判据与界面画底色用的是同一份数据（active / pending），所以「画成绿色/黄色」
 * 与「排到最前/第二」永远一致。AE2FC 的流体版合成情况界面继承自这个类且没重写
 * postUpdate，两边一起生效。本工程不生成 refmap，成员名都是 AE2 自己的，原样写。
 */
@Mixin(value = GuiCraftingCPU.class, remap = false)
public abstract class GuiCraftingCPUActiveFirstMixin {

    @Shadow
    private IItemList<IAEItemStack> active;

    @Shadow
    private IItemList<IAEItemStack> pending;

    @Shadow
    private List<IAEItemStack> visual;

    @Inject(method = "postUpdate", at = @At("TAIL"))
    private void ae2us$activeFirst(final List<IAEItemStack> list, final byte ref, final CallbackInfo ci) {
        ActiveFirstOrder.apply(this.visual, this::ae2us$rank);
    }

    /** 绿→黄→其余。判据与界面画底色时完全一致：绿看 active，黄看 pending。 */
    @Unique
    private int ae2us$rank(final IAEItemStack stack) {
        if (ae2us$has(this.active, stack)) {
            return ActiveFirstOrder.RANK_ACTIVE;
        }
        if (ae2us$has(this.pending, stack)) {
            return ActiveFirstOrder.RANK_PENDING;
        }
        return ActiveFirstOrder.RANK_OTHER;
    }

    @Unique
    private static boolean ae2us$has(final IItemList<IAEItemStack> list, final IAEItemStack stack) {
        final IAEItemStack found = list.findPrecise(stack);
        return found != null && found.getStackSize() > 0;
    }
}
