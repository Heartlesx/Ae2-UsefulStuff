package com.extrarawstyle.ae2us.parallel;

import appeng.tile.crafting.TileCraftingTile;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * 高级并行处理单元的瓦片：按原版并行单元参与合成 CPU（isAccelerator 为真），
 * 并声明自己等效 N 个并行数；多出的 N-1 由 CraftingCPUClusterMixin 在组块时补上。
 */
public class TileParallelUnit extends TileCraftingTile implements IParallelCraftingUnit {

    @Override
    public boolean isAccelerator() {
        return true;
    }

    @Override
    public int extraParallels() {
        final ParallelTier tier = this.tier();
        return tier == null ? 0 : tier.extraParallels();
    }

    /** 从所在方块读档位；未加载或已被替换时返回 null。 */
    public ParallelTier tier() {
        if (this.world == null || this.notLoaded()) {
            return null;
        }
        final Block block = this.world.getBlockState(this.pos).getBlock();
        return block instanceof BlockParallelUnit ? ((BlockParallelUnit) block).tier : null;
    }

    /** 掉落与网络可视化用本档方块，而不是原版并行单元（基类对 isAccelerator 的默认实现会返回原版物品）。 */
    @Override
    protected ItemStack getItemFromTile(final Object obj) {
        final ParallelTier tier = this.tier();
        if (tier != null) {
            return new ItemStack(ParallelUnitRegistration.BLOCKS[tier.ordinal()]);
        }
        return new ItemStack(ParallelUnitRegistration.BLOCKS[0]);
    }
}
