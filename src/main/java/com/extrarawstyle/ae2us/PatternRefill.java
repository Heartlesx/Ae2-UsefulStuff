package com.extrarawstyle.ae2us;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 空白样板自动补充的公共常量与纯逻辑（补货数量、物品 NBT 开关语义）。
 * 部件把同一把键写进自己的 NBT，无线终端写进终端物品的 NBT；键不存在视为开启。
 */
public final class PatternRefill {

    public static final String NBT_KEY = "ae2us_pattern_refill";

    /** 空白样板槽的上限，也是补货目标。 */
    public static final int CAPACITY = 64;

    private PatternRefill() {
    }

    /** 距离补满还差多少；已满返回 0。 */
    public static int missing(final int current) {
        return current >= CAPACITY ? 0 : CAPACITY - current;
    }

    /** 读开关：没有标签、没有键都视为开启。 */
    public static boolean read(final NBTTagCompound tag) {
        return tag == null || !tag.hasKey(NBT_KEY) || tag.getBoolean(NBT_KEY);
    }

    public static void write(final NBTTagCompound tag, final boolean value) {
        tag.setBoolean(NBT_KEY, value);
    }
}
