package com.extrarawstyle.ae2us.api;

import net.minecraft.item.ItemStack;

import java.util.Collection;

/**
 * 由 ContainerMEMonitorable 的 Mixin 织入：回答"这个物品在网络里有没有样板"。
 * 口径与悬停 tooltip 里的"可合成"一致（网络里存在能产出它的样板）。
 */
public interface ICraftableItemSource {

    boolean ae2us$isCraftable(ItemStack stack);

    /** 诊断用：当前判定为"有样板"的物品快照。 */
    Collection<ItemStack> ae2us$craftableItems();
}
