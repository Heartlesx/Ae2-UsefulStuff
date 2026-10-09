package com.extrarawstyle.ae2us.craftpin;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 1.12.2 的 ItemStack 没有重写 hashCode/equals（javap 确认过，是同一性比较），不能直接当 Map/Set 的键。
 * 这里按"物品 + 元数据 + NBT"建键：NBT 不参与哈希、只在 equals 里比，
 * 让哈希一致的候选落进同一个桶再精确判定，避免 NBT 哈希不一致造成的漏判。
 */
public final class ItemKey {

    private final Item item;
    private final int meta;
    private final NBTTagCompound nbt;

    public ItemKey(final ItemStack stack) {
        this.item = stack.getItem();
        this.meta = stack.getMetadata();
        this.nbt = stack.getTagCompound();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof ItemKey)) {
            return false;
        }
        final ItemKey other = (ItemKey) obj;
        if (this.item != other.item || this.meta != other.meta) {
            return false;
        }
        return this.nbt == null ? other.nbt == null : this.nbt.equals(other.nbt);
    }

    @Override
    public int hashCode() {
        return 31 * this.item.hashCode() + this.meta;
    }
}
