package com.extrarawstyle.ae2us.mixin;

import appeng.me.storage.AbstractCellInventory;
import com.extrarawstyle.ae2us.storage.AbstractSuperStorageCell;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 把 AE2 存储内核"一个元件最多 63 种类型"的上限放开到 256。
 * <p>
 * AE2UEL 把这个 63 写死在 {@code AbstractCellInventory} 里：构造器给 maxItemTypes 做钳制
 * （3 处常量），静态块按 63 开好存储键数组并循环填充（3 处常量）。两个方法里的 63 全部改 256：
 * 申报 ≤63 的元件（原版与其他模组）结果分毫不变，只放行申报更大值的元件（本模组的 2048M 元件）。
 * 补丁依赖上游字节码形状，{@code StorageCellSelfCheck} 会盯住（上游一变先在离线自检报警）。
 * <p>
 * 目标 AE2UEL 是 coremod，启动最早期其类即可读取，放早期配置（mixins.ae2us.json）安全。
 */
@Mixin(AbstractCellInventory.class)
public abstract class CellTypeLimitMixin {

    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 63))
    private int ae2us$raiseTypeLimit(final int original) {
        return AbstractSuperStorageCell.TYPES;
    }

    @ModifyConstant(method = "<clinit>", constant = @Constant(intValue = 63))
    private static int ae2us$raiseTypeLimitArrays(final int original) {
        return AbstractSuperStorageCell.TYPES;
    }
}
