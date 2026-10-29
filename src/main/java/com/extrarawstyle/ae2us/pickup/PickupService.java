package com.extrarawstyle.ae2us.pickup;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.extrarawstyle.ae2us.FeatureConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * 中键拾取在服务端的那一半：真取物 / 开下单界面 / 把背包里已有的弄到手上。
 * 客户端只负责判断按哪条路走。
 * <p>
 * 服务端不信客户端的任何判断，全部重算一遍：
 * 终端还在不在、网络有没有货、背包有没有、物品到底有没有样板。
 */
public final class PickupService {

    private PickupService() {
    }

    /**
     * 玩家中键点了某个物品。
     *
     * @param wanted 玩家想拿的东西（来自 pickBlock，带 NBT 与 meta）
     */
    public static void handle(final EntityPlayerMP player, final ItemStack wanted) {
        if (!FeatureConfig.middleClickPickupEnabled()) {
            return;
        }
        if (wanted.isEmpty()) {
            return;
        }

        final PickupTerminals.Found terminal = PickupTerminals.find(player);
        if (terminal == null) {
            return;
        }

        // 背包里已经有（快捷栏或主背包都算）：不问网络，直接弄到手上。
        // 必须放在库存探测之前——网络里没货时（刚从中键取走最后一个就会这样）
        // 走不到背包检查，会误判"没货"去弹下单界面。原版中键也是只看背包。
        final int existing = PickupLogic.slotOf(player.inventory, wanted);
        if (existing >= 0) {
            moveToHand(player, existing);
            return;
        }

        final IAEItemStack request = AEItemStack.fromItemStack(wanted);
        if (request == null) {
            return;
        }
        // 探测模板只要一件：这里只问"有没有、能不能抽"，具体取多少由下面 amountToTake 决定。
        final IAEItemStack probe = request.copy();
        probe.setStackSize(1);

        // 库存多少不查清单，直接问"能抽多少"（SIMULATE，不真的动库存）。
        // 必须这样：网络清单（IMEMonitor 的列表）与真实可抽取量并不总是一致——
        // 无限元件这类存储会给出数量 0 或干脆不列出来，而真正 extractItems 是拿得到的。
        // 之前按清单判断"有没有货"，导致网络里明明有线缆/无限元件供应的圆石却被当成没货。
        final long available = terminal.simulateExtract(probe, PickupLogic.TARGET_AMOUNT);

        if (available <= 0) {
            // 网络没货：只有"确实有样板"时才下单，没样板就什么都不做。
            if (!hasPatternFor(player, terminal, probe)) {
                return;
            }
            PickupTerminals.openCraftingAmount(player, terminal, probe);
            return;
        }

        // 落格目标永远是手上格。手上格被占时先把手上的东西挪到快捷栏空格，
        // 腾不开（快捷栏全满）就提示后放弃——不去挤别人的格子。
        final InventoryPlayer inventory = player.inventory;
        final int hand = inventory.currentItem;
        final ItemStack inHand = inventory.mainInventory.get(hand);
        final int shelter = inHand.isEmpty() ? -1 : PickupLogic.firstEmptyHotbarSlot(inventory);
        if (!inHand.isEmpty() && shelter < 0) {
            player.sendMessage(new TextComponentTranslation("ae2us.pickup.no_hotbar_space"));
            return;
        }

        final ItemStack taken = extract(terminal, probe, PickupLogic.amountToTake(available));
        if (taken.isEmpty()) {
            return;
        }
        if (shelter >= 0) {
            inventory.mainInventory.set(shelter, inHand);
        }
        inventory.mainInventory.set(hand, taken);
        inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }

    /**
     * 把背包第 slot 格的东西弄到手上，原版生存中键同款行为：
     * 快捷栏里就直接切选中格；主背包里就和手上格交换，手上的东西去它原来的位置。
     * <p>
     * 格子改动通过 inventoryContainer 同步给客户端；选中格不是容器槽位，
     * 要单独发 {@link SPacketHeldItemChange}。
     */
    private static void moveToHand(final EntityPlayerMP player, final int slot) {
        final InventoryPlayer inventory = player.inventory;
        if (slot < PickupLogic.HOTBAR_SIZE) {
            inventory.currentItem = slot;
            player.connection.sendPacket(new SPacketHeldItemChange(slot));
            return;
        }
        final int hand = inventory.currentItem;
        final ItemStack held = inventory.mainInventory.get(hand);
        final ItemStack found = inventory.mainInventory.get(slot);
        inventory.mainInventory.set(slot, held);
        inventory.mainInventory.set(hand, found);
        inventory.markDirty();
        player.inventoryContainer.detectAndSendChanges();
    }

    /**
     * 从网络里抽出最多 amount 个，返回抽到的实物。
     * <p>
     * 走终端自己的 extractItems：它内部判过网络是不是真的连上，
     * 自己拼 MachineSource 在没绑定时会 NPE。
     * <p>
     * {@code extractItems} 的返回值按 AE2 的接口文档是<b>实际抽走的数量</b>
     * （"returns the number of items extracted, null"），不是剩余量。
     * 之前按"剩余量"去减，结果永远算出 0，表现就是网络里明明有货却什么都取不到。
     */
    static ItemStack extract(final PickupTerminals.Found terminal, final IAEItemStack request, final long amount) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        final IAEItemStack toExtract = request.copy().setStackSize(amount);
        final IAEItemStack got = terminal.extract(toExtract, Actionable.MODULATE);
        final long extracted = got == null ? 0 : Math.min(amount, got.getStackSize());
        if (extracted <= 0) {
            return ItemStack.EMPTY;
        }
        final ItemStack out = request.asItemStackRepresentation();
        out.setCount((int) Math.min(Integer.MAX_VALUE, extracted));
        return out;
    }

    /**
     * 网络里有没有这个物品的样板。
     * <p>
     * 用 AE2 自己的合成网格查，口径与终端里"能不能合成"完全一致；
     * 合成网格拿不到（终端网络异常）时按"没有样板"处理，什么都不做。
     */
    private static boolean hasPatternFor(final EntityPlayer player, final PickupTerminals.Found terminal,
            final IAEItemStack what) {
        try {
            final ICraftingGrid crafting = terminal.craftingGrid();
            if (crafting == null) {
                return false;
            }
            return !crafting.getCraftingFor(what, null, 0, player.getEntityWorld()).isEmpty();
        } catch (final Throwable unavailable) {
            return false;
        }
    }
}
