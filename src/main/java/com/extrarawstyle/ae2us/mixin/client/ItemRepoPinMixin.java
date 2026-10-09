package com.extrarawstyle.ae2us.mixin.client;

import com.extrarawstyle.ae2us.FeatureConfig;
import com.extrarawstyle.ae2us.client.CraftedItemPinClient;
import com.extrarawstyle.ae2us.craftpin.ItemKey;
import com.extrarawstyle.ae2us.craftpin.PinOrder;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.client.me.ItemRepo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

/**
 * 把"刚下单/正在合成"的物品冒到 ME 终端列表最前。
 * <p>
 * 分区本身在 {@link PinOrder}：置顶条目以**完整清单**（{@code list}）为准，被当前搜索过滤掉的也捞回最前，
 * 所以搜索别的东西时置顶不会"消失"。这里只负责时机——什么时候该重排、以及怎么把结果写回 view。
 * <p>
 * 注入点选 updateView 的 TAIL。这个方法虽然会因为"没变化"提前返回，但它的提前返回和正常结束
 * 都跳到同一个 return 指令（javap 确认过），所以 TAIL 两种情况都能覆盖到。
 * <p>
 * 什么时候需要重排：见 {@link CraftedItemPinClient#needsReapply}。要点是**列表被 AE2 重建过就得重来一次**，
 * 光看"收到过新数据"会漏掉物品到货那一次（1.3.5-dev3 的 bug）。
 * 判断"重建过"靠比较 view 的对象身份：AE2 每次重建都是 new ArrayList（字节码确认过），O(1) 且不必每 tick 重排。
 */
@Mixin(value = ItemRepo.class, remap = false)
public abstract class ItemRepoPinMixin {

    @Shadow
    private List<IAEItemStack> view;

    @Shadow
    private boolean changed;

    /** 完整物品清单，不受搜索过滤。 */
    @Shadow
    @Final
    private IItemList<IAEItemStack> list;

    /** 上次重排过的那个列表对象；AE2 重建后这个身份就对不上了。 */
    @Unique
    private List<IAEItemStack> ae2us$orderedView;

    /** 上次是否真的动过列表，用于置顶取消后把顺序和内容还原。 */
    @Unique
    private boolean ae2us$pinned;

    @Inject(method = "updateView", at = @At("TAIL"))
    private void ae2us$applyPinOrder(final CallbackInfo ci) {
        if (!FeatureConfig.craftedItemPinEnabled()) {
            return;
        }
        final List<IAEItemStack> current = this.view;
        if (!CraftedItemPinClient.needsReapply(current, this.ae2us$orderedView, CraftedItemPinClient.isDirty())) {
            return;
        }
        this.ae2us$orderedView = current;
        CraftedItemPinClient.clearDirty();

        final Set<ItemKey> keys = CraftedItemPinClient.activeKeys();
        final List<IAEItemStack> ordered = current == null ? null : PinOrder.apply(current, this.list, keys);
        if (ordered == null) {
            if (this.ae2us$pinned) {
                // 置顶取消了：标脏让 AE2 下一 tick 自己重建一次，把顺序和被补进来的条目一起还原。
                this.ae2us$pinned = false;
                this.changed = true;
            }
            return;
        }

        current.clear();
        current.addAll(ordered);
        this.ae2us$pinned = true;
    }
}
