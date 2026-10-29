package com.extrarawstyle.ae2us.pickup;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.extrarawstyle.ae2us.FeatureConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * 中键拾取在服务端的那一半：真取物 / 开下单界面。客户端只负责判断按哪条路走。
 * <p>
 * 服务端不信客户端的任何判断，全部重算一遍：
 * 终端还在不在、网络有没有货、快捷栏有没有位、物品到底有没有样板。
 */
public final class PickupService {

    private PickupService() {
    }

    /**
     * 玩家中键点了某个物品。
     *
     * @param wanted  玩家想拿的东西（来自 pickBlock，带 NBT 与 meta）
     * @param noRoom  客户端已经发现快捷栏满了。服务端仍然自己再判一次，
     *                只是用它决定要不要提示——重算才是真正的依据。
     */
    public static void handle(final EntityPlayerMP player, final ItemStack wanted, final boolean noRoom) {
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

        // 背包里已经有：不取物，只把快捷栏切过去。切换由客户端本地做（零延迟），
        // 服务端这一侧只做核验，不重复取物——避免"客户端说有其实没有"时白拿一份。
        final int existing = PickupLogic.hotbarSlotOf(player.inventory, wanted);
        if (existing >= 0) {
            return;
        }

        final int target = PickupLogic.firstEmptyHotbarSlot(player.inventory);
        if (target < 0) {
            notify(player, noRoom);
            return;
        }

        final ItemStack taken = extract(terminal, probe, PickupLogic.amountToTake(available));
        if (!taken.isEmpty()) {
            player.inventory.mainInventory.set(target, taken);
            player.inventory.markDirty();
        }
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

    /** 只在失败时提示；成功静默，跟原版中键的手感一致。 */
    private static void notify(final EntityPlayer player, final boolean noRoom) {
        if (noRoom) {
            player.sendMessage(new TextComponentTranslation("ae2us.pickup.no_hotbar_space"));
        }
    }
}
