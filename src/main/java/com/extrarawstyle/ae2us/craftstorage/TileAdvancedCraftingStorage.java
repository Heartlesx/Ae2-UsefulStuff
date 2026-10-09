package com.extrarawstyle.ae2us.craftstorage;

import appeng.tile.crafting.TileCraftingStorageTile;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * 高级合成存储器的瓦片：按原版合成存储器参与合成 CPU——isStorage 为真、getStorageBytes 给出档位容量；
 * 集群组块时直接把它计入可用容量（这条路径走瓦片方法，不需要任何补丁）。
 * 父类必须是 TileCraftingStorageTile 而不是 TileCraftingTile：CraftingGridCache 重建 CPU 列表时
 * 按 TileCraftingStorageTile.class::isAssignableFrom 过滤机器类，直系 TileCraftingTile 子类成型也进不了列表。
 */
public class TileAdvancedCraftingStorage extends TileCraftingStorageTile {

    @Override
    public int getStorageBytes() {
        final StorageTier tier = this.tier();
        return tier == null ? 0 : tier.bytes();
    }

    /** 从所在方块读档位；未加载或已被替换时返回 null。 */
    public StorageTier tier() {
        if (this.world == null || this.notLoaded()) {
            return null;
        }
        final Block block = this.world.getBlockState(this.pos).getBlock();
        return block instanceof BlockAdvancedCraftingStorage ? ((BlockAdvancedCraftingStorage) block).tier : null;
    }

    /** 掉落与网络可视化用本档方块，而不是原版合成存储器（基类默认会返回原版配方方块）。 */
    @Override
    protected ItemStack getItemFromTile(final Object obj) {
        final StorageTier tier = this.tier();
        if (tier != null) {
            return new ItemStack(CraftingStorageRegistration.BLOCKS[tier.ordinal()]);
        }
        return new ItemStack(CraftingStorageRegistration.BLOCKS[0]);
    }
}
