package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.api.ICraftableItemSource;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.implementations.ContainerMEMonitorable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 给终端容器提供"这个物品有没有样板"的查询。
 * 数据是 items 里的 craftable 标志——客户端打开终端时 AE2 已经把完整清单（含只可合成、库存为 0 的条目）
 * 同步过来了，和 tooltip 的"可合成"同源；所以不额外发包，也不受搜索框和视图模式过滤影响。
 */
@Mixin(value = ContainerMEMonitorable.class, remap = false)
public abstract class ContainerMEMonitorableMixin implements ICraftableItemSource {

    @Shadow
    @Final
    public IItemList<IAEItemStack> items;

    // null 表示清单变过、下次查询时重建：清单每更新一次只置空，实际重建被摊到下一次绘制。
    @Unique
    private Map<CraftableKey, ItemStack> ae2us$craftables;

    @Inject(method = "postUpdate", at = @At("TAIL"))
    private void ae2us$invalidateCraftables(final List<IAEItemStack> list, final CallbackInfo ci) {
        this.ae2us$craftables = null;
    }

    @Override
    public boolean ae2us$isCraftable(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (this.ae2us$craftables == null) {
            this.ae2us$craftables = this.ae2us$collectCraftables();
        }
        if (this.ae2us$craftables.containsKey(new CraftableKey(stack))) {
            return true;
        }

        // 兜底：个别槽位/库存实现会给物品补上或去掉无关 NBT，精确匹配落空时按"物品 + 元数据"再比一次。
        for (final ItemStack candidate : this.ae2us$craftables.values()) {
            if (candidate.getItem() == stack.getItem() && candidate.getMetadata() == stack.getMetadata()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<ItemStack> ae2us$craftableItems() {
        if (this.ae2us$craftables == null) {
            this.ae2us$craftables = this.ae2us$collectCraftables();
        }
        return this.ae2us$craftables.values();
    }

    @Unique
    private Map<CraftableKey, ItemStack> ae2us$collectCraftables() {
        final Map<CraftableKey, ItemStack> result = new LinkedHashMap<>();
        for (final IAEItemStack stack : this.items) {
            if (stack.isCraftable()) {
                final ItemStack definition = stack.getDefinition();
                if (definition != null && !definition.isEmpty()) {
                    result.put(new CraftableKey(definition), definition.copy());
                }
            }
        }
        return result;
    }

    /**
     * 1.12.2 的 ItemStack 没有重写 hashCode/equals（javap 确认过，是同一性比较），
     * 直接丢进 HashSet 会导致 contains 永远查不到，所以自建键：物品 + 元数据 + NBT。
     */
    @Unique
    private static final class CraftableKey {

        private final Item item;
        private final int meta;
        private final NBTTagCompound nbt;

        private CraftableKey(final ItemStack stack) {
            this.item = stack.getItem();
            this.meta = stack.getMetadata();
            this.nbt = stack.getTagCompound();
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof CraftableKey)) {
                return false;
            }
            final CraftableKey other = (CraftableKey) obj;
            if (this.item != other.item || this.meta != other.meta) {
                return false;
            }
            return this.nbt == null ? other.nbt == null : this.nbt.equals(other.nbt);
        }

        @Override
        public int hashCode() {
            // NBT 不参与哈希：item + 元数据一致就落同一个桶，再由 equals 精确判定，避免 NBT 哈希不一致带来的漏判。
            return 31 * this.item.hashCode() + this.meta;
        }
    }
}
