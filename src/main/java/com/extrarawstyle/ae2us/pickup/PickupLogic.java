package com.extrarawstyle.ae2us.pickup;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * 中键拾取的纯逻辑：找一个能放东西的快捷栏槽、判断背包里是不是已经有了。
 * <p>
 * 这里刻意不碰 AE2 的单例（网络、存储），全部靠参数传进来，
 * 这样离线自检能直接把 {@link InventoryPlayer} 造出来验。
 */
public final class PickupLogic {

    /** 快捷栏槽数（原版固定 9 格）。 */
    public static final int HOTBAR_SIZE = 9;

    /** 一次想拿多少个：目标是一整堆。 */
    public static final int TARGET_AMOUNT = 64;

    private PickupLogic() {
    }

    /**
     * 背包里已经有这个物品时，返回它所在的槽号（快捷栏 0~8 或主背包 9~35）；否则返回 -1。
     * <p>
     * 之前只认快捷栏，主背包里的会被当成"背包没有"，网络也没货时就误弹下单界面。
     * 扫描从 0 开始：快捷栏和主背包都有时优先认快捷栏的（离手最近）。
     */
    public static int slotOf(final InventoryPlayer inventory, final ItemStack what) {
        for (int slot = 0; slot < inventory.mainInventory.size(); slot++) {
            final ItemStack present = inventory.mainInventory.get(slot);
            if (!present.isEmpty() && sameItem(present, what)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * 在快捷栏里找一个空格。找不到返回 -1——这时候整个操作要作罢，
     * 不去主背包找位，也不挤掉已有的东西。
     */
    public static int firstEmptyHotbarSlot(final InventoryPlayer inventory) {
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (inventory.mainInventory.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * 这次要的数量：目标一整堆，但不超过网络里实际有的。
     */
    public static long amountToTake(final long available) {
        if (available <= 0) {
            return 0;
        }
        return Math.min(TARGET_AMOUNT, available);
    }

    /**
     * 同一个物品：完整比对 NBT 与损伤值，跟 AE2 自己的物品比对口径一致。
     */
    public static boolean sameItem(final ItemStack a, final ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() && b.isEmpty();
        }
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }
}
