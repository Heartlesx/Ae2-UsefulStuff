package com.extrarawstyle.ae2us.pickup;

import appeng.api.AEApi;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.sync.GuiBridge;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * 从玩家身上找一把能用的无线终端，并访问它绑定的那个网络。
 * <p>
 * 判据统一走 AE2 的 {@link IWirelessTermHandler}：只要某个模组的终端注册了 handler 就自动兼容，
 * 不写死任何模组的类名（AE2 自带五种、AE2WUT 的通用终端都继承自 ToolWirelessTerminal）。
 * <p>
 * "能用"包含：终端有电、绑定了网络、且能真的拿到物品库存。
 * 拿不到库存的终端（样板终端、接口终端）不参与中键拾取。
 */
public final class PickupTerminals {

    private PickupTerminals() {
    }

    /** 一次命中的终端：物品、所在槽位、访问网络的入口。 */
    public static final class Found {
        public final ItemStack stack;
        public final int slot;
        public final boolean bauble;
        final WirelessTerminalGuiObject guiObject;

        Found(final ItemStack stack, final int slot, final boolean bauble, final WirelessTerminalGuiObject guiObject) {
            this.stack = stack;
            this.slot = slot;
            this.bauble = bauble;
            this.guiObject = guiObject;
        }

        /** 这个终端的物品库存；拿不到就是没连上。 */
        public IMEMonitor<IAEItemStack> storage() {
            return itemStorage(this.guiObject);
        }

        /** 这个终端所在网络的合成网格；拿不到就是没连上。 */
        public appeng.api.networking.crafting.ICraftingGrid craftingGrid() {
            final appeng.api.networking.IGrid grid = grid();
            return grid == null ? null : grid.getCache(appeng.api.networking.crafting.ICraftingGrid.class);
        }

        /**
         * 从网络抽物品。走终端自己的实现：它内部判过网络是否真的连上，
         * 自己拼动作来源在没绑定时会 NPE。动作来源借终端自己当机器宿主。
         * <p>
         * 返回值语义按 AE2 接口文档是<b>实际抽走的数量</b>
         * （"returns the number of items extracted, null"），不是剩余量。
         */
        public IAEItemStack extract(final IAEItemStack request, final appeng.api.config.Actionable mode) {
            return this.guiObject.extractItems(request, mode, new appeng.me.helpers.MachineSource(this.guiObject));
        }

        /**
         * 只探测能抽多少，不动库存（SIMULATE）。
         * <p>
         * 用探测而不是查网络清单：清单与真实可抽取量并不总一致——无限元件这类存储
         * 在清单里可能给出数量 0 或不列出来，但 extractItems 是拿得到的。
         */
        public long simulateExtract(final IAEItemStack what, final long max) {
            if (max <= 0) {
                return 0;
            }
            final IAEItemStack got = extract(what.copy().setStackSize(max), appeng.api.config.Actionable.SIMULATE);
            return got == null ? 0 : Math.min(max, got.getStackSize());
        }

        private appeng.api.networking.IGrid grid() {
            final appeng.api.networking.IGridNode node = this.guiObject.getActionableNode();
            return node == null ? null : node.getGrid();
        }
    }

    /**
     * 客户端侧能不能用：只判"带着一把注册过的、有电的、绑定了网络的终端"。
     * <p>
     * 客户端拿不到服务端的网络数据（库存、合成网格都在服务端），所以这里不做
     * "网络里有没有货"的判断——那一步交给服务端。范围校验 {@code rangeCheck()}
     * 依赖服务端的无线接入点，客户端也拿不到，同样交给服务端复核。
     */
    public static boolean clientUsable(final EntityPlayer player) {
        if (clientUsable(player.getHeldItemMainhand()) || clientUsable(player.getHeldItemOffhand())) {
            return true;
        }
        for (int slot = 0; slot < player.inventory.mainInventory.size(); slot++) {
            if (clientUsable(player.inventory.mainInventory.get(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean clientUsable(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        final IWirelessTermHandler handler = AEApi.instance().registries().wireless().getWirelessTerminalHandler(stack);
        return handler != null && handler.canHandle(stack);
    }

    /**
     * 按 主手 → 副手 → 快捷栏 → 饰品 的顺序找第一把可用终端。
     * 找不到返回 null，调用方就完全不干预。
     */
    public static Found find(final EntityPlayer player) {
        final Found main = probe(player, player.getHeldItemMainhand(), player.inventory.currentItem, false);
        if (main != null) {
            return main;
        }

        final int offhand = player.inventory.mainInventory.size() + player.inventory.armorInventory.size();
        final Found off = probe(player, player.getHeldItemOffhand(), offhand, false);
        if (off != null) {
            return off;
        }

        for (int slot = 0; slot < player.inventory.mainInventory.size(); slot++) {
            if (slot == player.inventory.currentItem) {
                continue;
            }
            final Found found = probe(player, player.inventory.mainInventory.get(slot), slot, false);
            if (found != null) {
                return found;
            }
        }

        return findInBaubles(player);
    }

    /**
     * 饰品栏是可选的（装了 Baubles 才有）。用反射调，免得把 Baubles 变成硬依赖。
     */
    private static Found findInBaubles(final EntityPlayer player) {
        try {
            final Class<?> api = Class.forName("baubles.api.BaublesApi");
            final Object handler = api.getMethod("getBaublesHandler", EntityPlayer.class).invoke(null, player);
            if (handler == null) {
                return null;
            }
            final IItemHandler inventory = (IItemHandler) handler;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                final Found found = probe(player, inventory.getStackInSlot(slot), slot, true);
                if (found != null) {
                    return found;
                }
            }
        } catch (final Throwable ignored) {
            // 没装 Baubles：饰品槽本来就不存在。
        }
        return null;
    }

    /** 这一格能不能当终端用；能用就返回它访问到的网络入口。 */
    private static Found probe(final EntityPlayer player, final ItemStack stack, final int slot, final boolean bauble) {
        if (stack.isEmpty()) {
            return null;
        }
        final IWirelessTermHandler handler = AEApi.instance().registries().wireless().getWirelessTerminalHandler(stack);
        if (handler == null || !handler.canHandle(stack)) {
            return null;
        }
        // 没电的终端不接管中键：否则玩家的中键会被一把没电的终端永久占掉。
        if (!handler.hasPower(player, 0, stack)) {
            return null;
        }

        final WirelessTerminalGuiObject object =
                new WirelessTerminalGuiObject(handler, stack, player, player.getEntityWorld(), slot, bauble ? 1 : 0, 0);
        if (itemStorage(object) == null) {
            return null;
        }
        // 超出无线访问点范围时不给用。
        if (!object.rangeCheck()) {
            return null;
        }
        return new Found(stack, slot, bauble, object);
    }

    /**
     * 这个终端访问到的物品库存；拿不到（终端没绑网络、或不是带物品存储的终端）返回 null。
     * <p>
     * 先看 networkReady()——它内部判了 sg 非空，直接调 getInventory 在没绑定时会 NPE。
     */
    public static IMEMonitor<IAEItemStack> itemStorage(final WirelessTerminalGuiObject object) {
        if (!networkReady(object)) {
            return null;
        }
        try {
            return object.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
        } catch (final Throwable notReady) {
            return null;
        }
    }

    /**
     * 终端是不是真的连上了一个能用的存储网络。AE2 用一个内部字段判断，
     * 这里走的是不需要 NPE 风险的那条路：拿可用物品列表，失败就是没连上。
     */
    private static boolean networkReady(final WirelessTerminalGuiObject object) {
        try {
            return object.getStorageList() != null;
        } catch (final Throwable notReady) {
            return false;
        }
    }

    /**
     * 打开 AE2 原生的下单界面（数量界面 → 确认界面），目标物品填好。
     * <p>
     * 直接按槽位打开，不切换玩家手上的物品：GuiBridge 会按槽号去背包里取那一格作宿主。
     */
    public static void openCraftingAmount(final EntityPlayerMP player, final Found found, final IAEItemStack what) {
        Platform.openGUI(player, found.slot, GuiBridge.GUI_CRAFTING_AMOUNT, found.bauble);

        if (player.openContainer instanceof appeng.container.implementations.ContainerCraftAmount) {
            final appeng.container.implementations.ContainerCraftAmount amount =
                    (appeng.container.implementations.ContainerCraftAmount) player.openContainer;
            amount.getCraftingItem().putStack(what.asItemStackRepresentation());
            amount.setItemToCraft(what);
            amount.detectAndSendChanges();
        }
    }
}
