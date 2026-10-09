package com.extrarawstyle.ae2us.alarm;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.GridFlags;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.GridAccessException;
import appeng.tile.grid.AENetworkTile;
import appeng.util.item.AEItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/** 库存报警器：监控全网物品实际存储数量，低于阈值时向有 READ 权限的在线玩家发聊天提醒。 */
public class TileStorageAlarm extends AENetworkTile implements IStackWatcherHost, ITickable {
    public static final int CAPACITY = 64;
    /** 30 秒重复提醒间隔（tick）。 */
    public static final long REPEAT_TICKS = 600;
    /** 周期自愈扫描间隔：watcher 之外每 1 秒对全部条目补一次计数核对。 */
    private static final long SCAN_TICKS = 20;
    /** 状态：0 运行中，1 无频道，2 断电。 */
    public static final int STATUS_ACTIVE = 0, STATUS_NO_CHANNEL = 1, STATUS_NO_POWER = 2;

    /** 单条监控配置；item 为空表示未设置。 */
    public static final class Entry {
        public ItemStack item = ItemStack.EMPTY;
        public long threshold = 1;
        public long count;
        public boolean below;
        public long lastAlert;
    }

    // 每个槽位在构造器中实例化，数组本身永远不含 null 元素。
    private final Entry[] entries = new Entry[CAPACITY];
    private IStackWatcher watcher;
    private long revision = 1;
    private boolean active;
    private boolean visualInitialized;
    private int statusOrdinal = STATUS_NO_CHANNEL;

    public TileStorageAlarm() {
        for (int i = 0; i < CAPACITY; i++) { this.entries[i] = new Entry(); }
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
    }

    public Entry entry(final int index) { return this.entries[index]; }

    public long revision() { return this.revision; }

    public boolean isActive() { return this.active; }

    public int statusOrdinal() { return this.statusOrdinal; }

    // ---------------------------------------------------------------- 配置接口（容器/网络包调用）

    public void setItem(final int index, final ItemStack stack) {
        final Entry entry = this.entries[index];
        final ItemStack template = stack.isEmpty() ? ItemStack.EMPTY : copyTemplate(stack);
        if (ItemStack.areItemStacksEqual(entry.item, template)) { return; }
        entry.item = template;
        entry.below = false;
        entry.lastAlert = 0;
        this.rebuildWatcher();
        this.bump();
        this.saveChanges();
        this.refreshAll();
        this.updateBlockState();
    }

    public void setThreshold(final int index, final long threshold) {
        final Entry entry = this.entries[index];
        if (entry.item.isEmpty() || threshold == entry.threshold) { return; }
        entry.threshold = threshold;
        entry.lastAlert = 0;
        this.bump();
        this.saveChanges();
        this.refreshAll();
    }

    public int configuredCount() {
        int total = 0;
        for (final Entry entry : this.entries) {
            if (!entry.item.isEmpty()) { total++; }
        }
        return total;
    }

    private static ItemStack copyTemplate(final ItemStack stack) {
        final ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    // ---------------------------------------------------------------- 判定状态机

    /** 纯判定：-1 恢复复位，0 无变化，1 跌破阈值（需立即报警）。 */
    public static int transition(final boolean below, final long count, final long threshold) {
        final boolean next = count < threshold;
        if (next && !below) { return 1; }
        return !next && below ? -1 : 0;
    }

    private void evaluate(final int index, final long count, final long now) {
        final Entry entry = this.entries[index];
        if (entry.item.isEmpty() || !this.active) { return; }
        final boolean countChanged = entry.count != count;
        entry.count = count;
        final int change = transition(entry.below, count, entry.threshold);
        if (change == 1) {
            entry.below = true;
            entry.lastAlert = now;
            this.bump();
            this.updateBlockState();
            this.alert(entry);
        } else if (change == -1) {
            entry.below = false;
            this.bump();
            this.updateBlockState();
        } else if (countChanged) {
            this.bump();
        }
    }

    private void refreshAll() {
        if (this.world == null || this.world.isRemote || !this.active) { return; }
        this.rescan(this.world.getTotalWorldTime());
        this.repeatAlerts(this.world.getTotalWorldTime());
    }

    private void rescan(final long now) {
        final IItemList<IAEItemStack> list = this.storageList();
        if (list == null) { return; }
        for (int i = 0; i < CAPACITY; i++) {
            final Entry entry = this.entries[i];
            if (entry.item.isEmpty()) { continue; }
            final IAEItemStack found = list.findPrecise(AEItemStack.fromItemStack(entry.item));
            this.evaluate(i, found == null ? 0 : found.getStackSize(), now);
        }
    }

    private void repeatAlerts(final long now) {
        for (final Entry entry : this.entries) {
            if (entry.item.isEmpty() || !entry.below || now - entry.lastAlert < REPEAT_TICKS) { continue; }
            entry.lastAlert = now;
            this.alert(entry);
        }
    }

    private IItemList<IAEItemStack> storageList() {
        try {
            final IMEMonitor<IAEItemStack> inventory = this.getProxy().getStorage()
                    .getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            return inventory.getStorageList();
        } catch (final GridAccessException ignored) {
            return null;
        }
    }

    private void alert(final Entry entry) {
        final World world = this.world;
        if (world == null || world.isRemote) { return; }
        final ISecurityGrid security;
        try {
            security = this.getProxy().getSecurity();
        } catch (final GridAccessException ignored) {
            return;
        }
        final BlockPos pos = this.pos;
        final ITextComponent message = new TextComponentTranslation("ae2us.alarm.low",
                entry.item.getTextComponent(), entry.count, entry.threshold,
                new TextComponentTranslation("ae2us.alarm.position",
                        world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ()));
        for (final Object object : world.playerEntities) {
            final EntityPlayer player = (EntityPlayer) object;
            if (security.hasPermission(player, SecurityPermissions.EXTRACT)) { player.sendMessage(message); }
        }
    }

    private void rebuildWatcher() {
        if (this.watcher == null) { return; }
        this.watcher.reset();
        for (final Entry entry : this.entries) {
            if (!entry.item.isEmpty()) { this.watcher.add(AEItemStack.fromItemStack(entry.item)); }
        }
    }

    // ---------------------------------------------------------------- 网格监听与周期

    @Override
    public void updateWatcher(final IStackWatcher newWatcher) {
        this.watcher = newWatcher;
        if (newWatcher == null) { return; }
        for (final Entry entry : this.entries) {
            if (!entry.item.isEmpty()) { newWatcher.add(AEItemStack.fromItemStack(entry.item)); }
        }
        if (this.world != null && !this.world.isRemote) { this.refreshAll(); }
    }

    @Override
    public void onStackChange(final IItemList<?> list, final IAEStack<?> fullStack, final IAEStack<?> diffStack,
                              final IActionSource source, final IStorageChannel<?> channel) {
        if (channel != AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)) { return; }
        if (!this.active || this.world == null || this.world.isRemote) { return; }
        final ItemStack changed = ((IAEItemStack) fullStack).getDefinition();
        final long now = this.world.getTotalWorldTime();
        for (int i = 0; i < CAPACITY; i++) {
            final Entry entry = this.entries[i];
            if (entry.item.isEmpty()) { continue; }
            if (ItemStack.areItemStacksEqual(entry.item, changed)) { this.evaluate(i, fullStack.getStackSize(), now); }
        }
    }

    @Override
    public void update() {
        if (this.world == null || this.world.isRemote) { return; }
        final boolean nodeActive = this.getProxy().isActive();
        final int nextStatus = nodeActive ? STATUS_ACTIVE
                : this.getProxy().isPowered() ? STATUS_NO_CHANNEL : STATUS_NO_POWER;
        if (nodeActive != this.active || nextStatus != this.statusOrdinal) {
            this.active = nodeActive;
            this.statusOrdinal = nextStatus;
            if (!nodeActive) {
                for (final Entry entry : this.entries) {
                    if (entry.below) { entry.below = false; }
                }
            }
            this.bump();
            this.updateBlockState();
            if (nodeActive) { this.refreshAll(); }
        }
        if (!this.visualInitialized) {
            this.visualInitialized = true;
            this.updateBlockState();
        }
        if (!this.active) { return; }
        final long now = this.world.getTotalWorldTime();
        if (now % SCAN_TICKS == 0) { this.rescan(now); }
        this.repeatAlerts(now);
    }

    private void updateBlockState() {
        final World world = this.world;
        if (world == null || world.isRemote) { return; }
        final net.minecraft.block.state.IBlockState state = world.getBlockState(this.pos);
        if (!(state.getBlock() instanceof BlockStorageAlarm)) { return; }
        boolean alert = false;
        if (this.active) {
            for (final Entry entry : this.entries) {
                if (!entry.item.isEmpty() && entry.below) { alert = true; break; }
            }
        }
        if (state.getValue(BlockStorageAlarm.LIT) != this.active || state.getValue(BlockStorageAlarm.ALERT) != alert) {
            world.setBlockState(this.pos, state.withProperty(BlockStorageAlarm.LIT, this.active)
                    .withProperty(BlockStorageAlarm.ALERT, alert), 2);
        }
    }

    private void bump() { this.revision++; }

    // ---------------------------------------------------------------- NBT 保存/加载与物品配置

    public void writeConfig(final ItemStack stack) {
        final NBTTagCompound root = new NBTTagCompound();
        root.setTag("entries", saveEntries(this.entries));
        stack.setTagInfo("alarmConfig", root);
    }

    public void readConfig(final ItemStack stack) {
        if (!stack.hasTagCompound()) { return; }
        final NBTTagCompound root = stack.getTagCompound().getCompoundTag("alarmConfig");
        if (!root.hasKey("entries")) { return; }
        loadEntries(this.entries, root.getCompoundTag("entries"));
        this.rebuildWatcher();
        this.bump();
        this.refreshAll();
        this.updateBlockState();
    }

    public static NBTTagCompound saveEntries(final Entry[] entries) {
        final NBTTagCompound root = new NBTTagCompound();
        final NBTTagList list = new NBTTagList();
        for (int i = 0; i < entries.length; i++) {
            final Entry entry = entries[i];
            if (entry == null || entry.item.isEmpty()) { continue; }
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("slot", i);
            tag.setTag("item", entry.item.serializeNBT());
            tag.setLong("threshold", entry.threshold);
            list.appendTag(tag);
        }
        root.setTag("list", list);
        return root;
    }

    public static void loadEntries(final Entry[] entries, final NBTTagCompound root) {
        for (final Entry entry : entries) {
            if (entry != null) {
                entry.item = ItemStack.EMPTY;
                entry.threshold = 1;
                entry.count = 0;
                entry.below = false;
                entry.lastAlert = 0;
            }
        }
        final NBTTagList list = root.getTagList("list", 10);
        for (int i = 0; i < list.tagCount() && i < CAPACITY; i++) {
            final NBTTagCompound tag = list.getCompoundTagAt(i);
            final int slot = tag.getInteger("slot");
            if (slot < 0 || slot >= entries.length || entries[slot] == null) { continue; }
            final ItemStack item = new ItemStack(tag.getCompoundTag("item"));
            if (item.isEmpty()) { continue; }
            entries[slot].item = item;
            entries[slot].threshold = Math.max(1L, tag.getLong("threshold"));
            entries[slot].lastAlert = 0;
        }
    }
}
