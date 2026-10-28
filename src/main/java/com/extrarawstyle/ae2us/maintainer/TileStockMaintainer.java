package com.extrarawstyle.ae2us.maintainer;

import appeng.api.AEApi;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridHost;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.GridAccessException;
import appeng.me.cluster.IAECluster;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import appeng.tile.crafting.TileCraftingTile;
import appeng.tile.grid.AENetworkTile;
import com.extrarawstyle.ae2us.maintainer.fluid.FluidMaintainerBridge;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * ME库存维持器：监控所在 ME 网络的实际库存，严格低于阈值时提交一笔固定批量的独立订单。
 *
 * 订单是 standalone（submitJob 的 requestingMachine 传 null），本设备只是观察者：
 * 拆掉方块、区块卸载都不影响已提交订单，本类也绝不对已提交订单调用 cancel()。
 *
 * 为什么不能用 link.isDone() 判结束：standalone 的 CraftingLink.markDone() 只通知 Nexus，
 * 而 standalone 链接从未注册进 Nexus，所以它永远返回 false。这里的做法是按 craftID 核对 CPU：
 * - 本网络某个 CPU 的 getLastCraftingLink() 同 craftID 且 CPU 忙 → 合成中
 * - 那台 CPU 空闲、或已换别的 craftID → 原单已结束或取消，释放关联
 * - CPU 所在区块没加载 / 维度不在 / 网络里看不到 → 状态未知，保留关联等待，绝不重下
 */
public class TileStockMaintainer extends AENetworkTile implements ITickable {
    /** 库存与 CPU 状态的常规核对间隔。 */
    public static final long SCAN_TICKS = 20;

    /** 0 运行中，1 无频道，2 断电。 */
    public static final int STATUS_ACTIVE = 0, STATUS_NO_CHANNEL = 1, STATUS_NO_POWER = 2;

    private final MaintainerBook book = new MaintainerBook();
    private final MaintainerQueue queue = new MaintainerQueue();
    private final IActionSource source = new MachineSource(this);
    /** 每条最多一个尚在计算的 Future。 */
    private final Map<Integer, Future<ICraftingJob>> pending = new HashMap<>();
    /** 已算好、等待可执行 CPU 的计划。 */
    private final Map<Integer, Plan> plans = new HashMap<>();

    private boolean active;
    private boolean visualInitialized;
    private int statusOrdinal = STATUS_NO_CHANNEL;

    /** 已算好、等待空闲 CPU 的计划；configRevision 用于拒绝过期结果。 */
    private static final class Plan {
        final ICraftingJob job;
        final long created;
        final long configRevision;
        final long batch;

        Plan(final ICraftingJob job, final long created, final long configRevision, final long batch) {
            this.job = job;
            this.created = created;
            this.configRevision = configRevision;
            this.batch = batch;
        }

        boolean expired(final long now) {
            return now - this.created > MaintainerQueue.PLAN_LIFETIME;
        }
    }

    public TileStockMaintainer() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(2.0);
    }

    public MaintainerBook book() {
        return this.book;
    }

    public boolean isActive() {
        return this.active;
    }

    public int statusOrdinal() {
        return this.statusOrdinal;
    }

    public long revision() {
        return this.book.revision();
    }

    // ---------------------------------------------------------------- 主循环

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
                this.abandonRuntimeWork();
            }
            this.updateBlockState();
            if (nodeActive) {
                this.queue.reset();
                this.scan();
                this.trackOrders();
            }
        }
        if (!this.visualInitialized) {
            this.visualInitialized = true;
            this.updateBlockState();
        }
        if (!this.active) { return; }

        final long now = this.world.getTotalWorldTime();
        // Future 完成时刻不一定落在常规核对上，独立收集，避免计划白等一秒。
        this.collectFutures(now);
        if (now % SCAN_TICKS != 0) { return; }
        this.scan();
        this.trackOrders();
        this.refreshStatuses();
        this.tick(now);
        this.updateBlockState();
    }

    /** 断网/断电：放弃未提交的本地工作，已提交的订单留给 CPU。 */
    private void abandonRuntimeWork() {
        for (final Future<ICraftingJob> future : this.pending.values()) { future.cancel(false); }
        this.pending.clear();
        this.plans.clear();
        for (final MaintainerRule rule : this.book.rules()) {
            if (rule.target == null || rule.status == MaintainerStatus.DISABLED) { continue; }
            rule.status = MaintainerStatus.OFFLINE;
            rule.observed = 0;
            rule.observedValid = false;
        }
        this.book.bump();
    }

    // ---------------------------------------------------------------- 库存观测

    private void scan() {
        final IItemList<IAEItemStack> items;
        try {
            items = this.getProxy().getStorage()
                    .getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class))
                    .getStorageList();
        } catch (final GridAccessException offline) {
            for (final MaintainerRule rule : this.book.rules()) { rule.observedValid = false; }
            return;
        }
        // 流体清单只依赖 AE2UEL 自带的流体通道，未装 AE2FC 也能读到实际库存。
        final IItemList<?> fluids = this.fluidList();
        boolean changed = false;
        for (int i = 0; i < MaintainerBook.CAPACITY; i++) {
            final MaintainerRule rule = this.book.rules()[i];
            final long stored;
            final boolean valid;
            if (rule.target == null) {
                stored = 0L;
                valid = false;
            } else if (rule.mode == MaintainerRule.MODE_FLUID) {
                stored = fluids == null ? -1L : FluidMaintainerBridge.stored(fluids, rule.target);
                valid = stored >= 0;
            } else {
                stored = storedItems(items, rule.target);
                valid = true;
            }
            if (rule.observed != Math.max(0L, stored) || rule.observedValid != valid) {
                rule.observed = Math.max(0L, stored);
                rule.observedValid = valid;
                changed = true;
            }
        }
        // 只有数值真的变了才发状态包，避免每秒向客户端推一遍相同数据。
        if (changed) { this.book.bump(); }
    }

    private static long storedItems(final IItemList<IAEItemStack> items, final Object target) {
        final IAEItemStack found = items.findPrecise((IAEItemStack) target);
        return found == null ? 0L : found.getStackSize();
    }

    @SuppressWarnings("unchecked")
    private IItemList<?> fluidList() {
        try {
            final IStorageChannel<?> channel = FluidMaintainerBridge.channel();
            if (channel == null) { return null; }
            final IMEMonitor<IAEFluidStack> monitor = (IMEMonitor<IAEFluidStack>) this.getProxy().getStorage()
                    .getInventory((IStorageChannel<IAEFluidStack>) channel);
            return monitor.getStorageList();
        } catch (final GridAccessException offline) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 订单跟踪

    /**
     * 核对每条规则的当前订单。只处理有 craftId 的条目，不改动配置。
     * 结束时只解除本地的关联，不去 cancel 任何东西。
     */
    private void trackOrders() {
        for (int i = 0; i < MaintainerBook.CAPACITY; i++) {
            final MaintainerRule rule = this.book.rules()[i];
            if (rule.craftId == null) { continue; }
            final OrderLookup lookup = this.lookupOrder(rule);
            switch (lookup.state) {
                case OrderLookup.RUNNING:
                    this.setStatus(i, MaintainerStatus.CRAFTING);
                    break;
                case OrderLookup.FINISHED:
                    rule.clearOrder();
                    this.queue.clearCooldown(i);
                    this.setStatus(i, rule.enabled ? MaintainerStatus.QUEUED : MaintainerStatus.DISABLED);
                    break;
                default:
                    // 订单状态未知必须让玩家看到，不能停在“合成中”假装一切正常。
                    this.setStatus(i, MaintainerStatus.ORDER_UNKNOWN);
                    break;
            }
        }
    }

    /**
     * 先查本网络，再查记录里那台 CPU 的坐标。
     * 只有拿到确凿证据才判结束；一切“看不到”都算未知，绝不重下。
     */
    private OrderLookup lookupOrder(final MaintainerRule rule) {
        final String craftId = rule.craftId;
        try {
            final ICraftingGrid crafting = this.getProxy().getCrafting();
            for (final ICraftingCPU cpu : crafting.getCpus()) {
                if (!(cpu instanceof CraftingCPUCluster)) { continue; }
                final CraftingCPUCluster cluster = (CraftingCPUCluster) cpu;
                final ICraftingLink link = cluster.getLastCraftingLink();
                if (link == null || !craftId.equals(link.getCraftingID())) { continue; }
                if (cluster.isBusy()) {
                    // 见到这单真的在跑：有的 CPU 压根给不出方块坐标（虚拟合成处理器），
                    // 只能靠这个标记在它消失时判断"确实跑完了"。
                    if (!rule.seenRunning) {
                        rule.seenRunning = true;
                        this.saveChanges();
                    }
                    this.rememberCore(rule, cluster);
                    return OrderLookup.RUNNING_ORDER;
                }
                // 同 craftID 但 CPU 已经不忙：这一单结束了（完成或取消，本设备不需要区分）。
                return OrderLookup.FINISHED_ORDER;
            }
        } catch (final GridAccessException offline) {
            return OrderLookup.UNKNOWN_ORDER;
        }
        // 本网络没有这台 CPU 的痕迹：去记录位置看它是否已换任务。
        final OrderLookup remembered = this.lookupRemembered(rule);
        // 记录位置也看不到：见过它就说明跑完了；从没见过才是真的证据不足。
        return remembered == null ? OrderLookup.forMissingCpu(rule.seenRunning) : remembered;
    }

    /** 查记录位置那台 CPU；返回 null 表示没有可用线索（区块没加载或没有坐标，属真正的未知）。 */
    private OrderLookup lookupRemembered(final MaintainerRule rule) {
        if (!rule.hasCpuPos) { return null; }
        final World world = this.dimensionWorld(rule.cpuDim);
        if (world == null) { return null; }
        final BlockPos pos = BlockPos.fromLong(rule.cpuPos);
        // 不主动加载区块：没加载就是没证据，等它回来。
        if (!world.isBlockLoaded(pos)) { return null; }
        final TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileCraftingTile)) {
            return OrderLookup.forRememberedCpu(false, false, false, false);
        }
        final IAECluster cluster = ((TileCraftingTile) tile).getCluster();
        if (!(cluster instanceof CraftingCPUCluster)) { return null; }
        final CraftingCPUCluster cpu = (CraftingCPUCluster) cluster;
        final ICraftingLink link = cpu.getLastCraftingLink();
        final boolean hasLink = link != null;
        final boolean matches = hasLink && rule.craftId.equals(link.getCraftingID());
        return OrderLookup.forRememberedCpu(true, matches, hasLink, cpu.isBusy());
    }

    private World dimensionWorld(final int dimension) {
        if (this.world != null && this.world.provider.getDimension() == dimension) { return this.world; }
        return DimensionManager.getWorld(dimension, false);
    }

    /** 记下 CPU 核心方块坐标，作为以后在网络不可见时的恢复线索。 */
    private void rememberCore(final MaintainerRule rule, final CraftingCPUCluster cluster) {
        final Iterator<IGridHost> tiles = cluster.getTiles();
        while (tiles.hasNext()) {
            final Object host = tiles.next();
            if (!(host instanceof TileCraftingTile)) { continue; }
            final TileCraftingTile tile = (TileCraftingTile) host;
            if (!tile.isCoreBlock()) { continue; }
            final World tileWorld = tile.getWorld();
            if (tileWorld == null) { continue; }
            rule.hasCpuPos = true;
            rule.cpuDim = tileWorld.provider.getDimension();
            rule.cpuPos = tile.getPos().toLong();
            return;
        }
    }

    // ---------------------------------------------------------------- 队列与提交

    private void tick(final long now) {
        final int index = this.queue.pick(this.book, now);
        if (index < 0) { return; }
        final MaintainerRule rule = this.book.rule(index);
        if (!rule.observedValid) {
            rule.status = MaintainerStatus.OFFLINE;
            this.book.bump();
            return;
        }
        if (!rule.belowThreshold(rule.observed)) {
            rule.status = MaintainerStatus.STOCKED;
            this.book.bump();
            return;
        }
        if (rule.mode == MaintainerRule.MODE_FLUID && !this.hasFluidDiscretizer()) {
            rule.status = MaintainerStatus.NO_DISCRETIZER;
            this.queue.coolDown(index, now, MaintainerQueue.RETRY_NO_PATTERN);
            this.book.bump();
            return;
        }
        this.advance(index, rule, now);
    }

    /** 拿到可用计划就提交，没有就按需发起计算。 */
    private void advance(final int index, final MaintainerRule rule, final long now) {
        final Plan plan = this.plans.get(index);
        if (plan != null) {
            if (plan.expired(now) || plan.configRevision != this.book.configRevision()) {
                this.plans.remove(index);
            } else {
                // 计划在等 CPU 时也要让出轮转：本轮游标已前进，这一条排到队尾。
                this.submitJob(index, rule, plan, now);
                return;
            }
        }
        if (this.pending.containsKey(index)) {
            rule.status = MaintainerStatus.CALCULATING;
            this.book.bump();
            return;
        }
        this.beginCalculation(index, rule, now);
    }

    private void beginCalculation(final int index, final MaintainerRule rule, final long now) {
        final IAEItemStack request = this.requestStack(rule);
        if (request == null) {
            rule.status = MaintainerStatus.INCOMPLETE;
            this.book.bump();
            return;
        }
        rule.status = MaintainerStatus.CALCULATING;
        this.book.bump();
        try {
            final ICraftingGrid crafting = this.getProxy().getCrafting();
            final Future<ICraftingJob> future = crafting.beginCraftingJob(this.world,
                    this.getProxy().getGrid(), this.source, request, null);
            if (future == null) {
                this.fail(index, rule, now);
                return;
            }
            this.pending.put(index, future);
        } catch (final GridAccessException offline) {
            rule.status = MaintainerStatus.OFFLINE;
            this.book.bump();
        }
    }

    /** 计算请求：物品为 目标×批量；流体为 AE2FC 虚拟 drop，1 drop = 1 mB。 */
    private IAEItemStack requestStack(final MaintainerRule rule) {
        if (rule.mode == MaintainerRule.MODE_FLUID) {
            return FluidMaintainerBridge.request(rule.target, rule.batch);
        }
        final IAEItemStack stack = ((IAEItemStack) rule.target).copy();
        stack.setStackSize(rule.batch);
        stack.setCraftable(false);
        stack.setCountRequestable(0);
        return stack;
    }

    private void collectFutures(final long now) {
        if (this.pending.isEmpty()) { return; }
        final Iterator<Map.Entry<Integer, Future<ICraftingJob>>> it = this.pending.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Integer, Future<ICraftingJob>> entry = it.next();
            final Future<ICraftingJob> future = entry.getValue();
            if (!future.isDone()) { continue; }
            it.remove();
            final int index = entry.getKey();
            final MaintainerRule rule = this.book.rule(index);
            if (rule == null || rule.target == null) { continue; }
            ICraftingJob job = null;
            try {
                job = future.get();
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (final Exception failed) {
                // CancellationException / ExecutionException：都按“计划没算出来”处理。
                job = null;
            }
            if (job == null || job.isSimulation()) {
                // 缺材料、无样板或计算失败：让出队列，冷却后再算，不长期占着槽位。
                rule.status = MaintainerStatus.NO_PATTERN;
                this.queue.coolDown(index, now, MaintainerQueue.RETRY_NO_PATTERN);
                this.book.bump();
                continue;
            }
            final Plan plan = new Plan(job, now, this.book.configRevision(), rule.batch);
            this.plans.put(index, plan);
            this.submitJob(index, rule, plan, now);
        }
    }

    private void submitJob(final int index, final MaintainerRule rule, final Plan plan, final long now) {
        // 提交前复查：配置没变、仍然启用、库存仍不足。库存回到阈值就丢掉这笔计划。
        if (!rule.usable() || plan.configRevision != this.book.configRevision()
                || !rule.observedValid || !rule.belowThreshold(rule.observed)) {
            this.plans.remove(index);
            rule.status = rule.enabled ? MaintainerStatus.STOCKED : MaintainerStatus.DISABLED;
            this.book.bump();
            return;
        }
        final ICraftingLink link;
        try {
            link = this.getProxy().getCrafting().submitJob(plan.job, null, null, false, this.source);
        } catch (final GridAccessException offline) {
            this.plans.remove(index);
            rule.status = MaintainerStatus.OFFLINE;
            this.book.bump();
            return;
        }
        if (link != null) {
            this.plans.remove(index);
            rule.craftId = link.getCraftingID();
            rule.orderBatch = plan.batch;
            rule.hasCpuPos = false;
            rule.cpuDim = this.world.provider.getDimension();
            rule.cpuPos = 0L;
            rule.status = MaintainerStatus.CRAFTING;
            this.queue.clearCooldown(index);
            // 立刻定位一次 CPU，供日后网络里看不到时核对；失败也不影响订单。
            this.rememberCurrentCpu(rule);
            this.book.bump();
            return;
        }
        // submitJob 返回 null 有两种原因：没有空闲/容量足够的 CPU，或原料与权限在提交时变了。
        // 没有可用 CPU 时把算好的计划留着等它空出来（不每秒重算）；其余情况丢掉计划冷却重试。
        if (this.hasIdleCpuFor(plan)) {
            this.plans.remove(index);
            this.fail(index, rule, now);
            return;
        }
        if (plan.expired(now)) {
            this.plans.remove(index);
            rule.status = MaintainerStatus.QUEUED;
            this.book.bump();
            return;
        }
        rule.status = MaintainerStatus.WAITING_CPU;
        this.plans.put(index, plan);
        this.book.bump();
    }

    /** 网络里是否有能接下这份计划的空闲 CPU（与 AE 自己的挑选口径一致）。 */
    private boolean hasIdleCpuFor(final Plan plan) {
        try {
            for (final ICraftingCPU cpu : this.getProxy().getCrafting().getCpus()) {
                if (cpu.isBusy()) { continue; }
                if (cpu.getAvailableStorage() >= plan.job.getByteTotal()) { return true; }
            }
        } catch (final GridAccessException offline) {
            return false;
        }
        return false;
    }

    /** 刚提交完，在本网络的 CPU 里按 craftID 找一次并记下核心坐标。 */
    private void rememberCurrentCpu(final MaintainerRule rule) {
        try {
            for (final ICraftingCPU cpu : this.getProxy().getCrafting().getCpus()) {
                if (!(cpu instanceof CraftingCPUCluster)) { continue; }
                final CraftingCPUCluster cluster = (CraftingCPUCluster) cpu;
                final ICraftingLink link = cluster.getLastCraftingLink();
                if (link != null && rule.craftId.equals(link.getCraftingID())) {
                    this.rememberCore(rule, cluster);
                    return;
                }
            }
        } catch (final GridAccessException offline) {
            // 交给后续核对补。
        }
    }

    private void fail(final int index, final MaintainerRule rule, final long now) {
        rule.status = MaintainerStatus.SUBMIT_FAILED;
        this.queue.coolDown(index, now, MaintainerQueue.RETRY_SUBMIT_FAILED);
        this.book.bump();
    }

    // ---------------------------------------------------------------- 状态刷新

    /**
     * 把非订单状态按当前配置与库存重算一遍；占着订单或在等 CPU 的条目不覆盖。
     * 等 CPU、缺料、提交失败这些状态有实际含义（面板要能看到），只有在配置或库存本身
     * 发生变化时才回到 QUEUED——那种情况下面 evaluate 给出的结果自然会不同。
     */
    private void refreshStatuses() {
        for (int i = 0; i < MaintainerBook.CAPACITY; i++) {
            final MaintainerRule rule = this.book.rules()[i];
            if (rule.target == null) {
                if (rule.status != MaintainerStatus.EMPTY) {
                    rule.status = MaintainerStatus.EMPTY;
                    this.book.bump();
                }
                continue;
            }
            if (MaintainerStatus.holdsOrder(rule.status)) { continue; }
            if (this.pending.containsKey(i) || this.plans.containsKey(i)) { continue; }
            final int next = this.evaluate(rule);
            // 缺料/提交失败/等待 CPU 是“本轮尝试的结论”，库存或配置没变就保留，别每 20 tick 刷回排队中。
            if (MaintainerStatus.isAttemptResult(rule.status) && next == MaintainerStatus.QUEUED) { continue; }
            if (rule.status != next) {
                rule.status = next;
                this.book.bump();
            }
        }
    }

    /** 改状态并发同步包；状态没变时什么都不做，避免每秒推一次相同数据。 */
    private void setStatus(final int index, final int status) {
        final MaintainerRule rule = this.book.rule(index);
        if (rule.status == status) { return; }
        rule.status = status;
        this.book.bump();
    }

    private int evaluate(final MaintainerRule rule) {
        if (!rule.enabled) { return MaintainerStatus.DISABLED; }
        if (rule.threshold <= 0 || rule.batch <= 0) { return MaintainerStatus.INCOMPLETE; }
        if (!rule.observedValid) { return MaintainerStatus.OFFLINE; }
        if (!rule.belowThreshold(rule.observed)) { return MaintainerStatus.STOCKED; }
        if (rule.mode == MaintainerRule.MODE_FLUID && !this.hasFluidDiscretizer()) {
            return MaintainerStatus.NO_DISCRETIZER;
        }
        return MaintainerStatus.QUEUED;
    }

    private void updateBlockState() {
        if (this.world == null || this.world.isRemote) { return; }
        final net.minecraft.block.state.IBlockState state = this.world.getBlockState(this.pos);
        if (!(state.getBlock() instanceof BlockStockMaintainer)) { return; }
        boolean working = false;
        if (this.active) {
            for (final MaintainerRule rule : this.book.rules()) {
                if (MaintainerStatus.holdsOrder(rule.status)) { working = true; break; }
            }
        }
        if (state.getValue(BlockStockMaintainer.LIT) != this.active
                || state.getValue(BlockStockMaintainer.WORKING) != working) {
            this.world.setBlockState(this.pos, state.withProperty(BlockStockMaintainer.LIT, this.active)
                    .withProperty(BlockStockMaintainer.WORKING, working), 2);
        }
    }

    // ---------------------------------------------------------------- 配置操作

    /**
     * 设置目标；target 为 null 表示清空。
     * 返回 false 表示与另一条规则重复或类型与模式不符，本次未改动。
     */
    public boolean configure(final int index, final IAEStack<?> target, final int mode) {
        if (index < 0 || index >= MaintainerBook.CAPACITY) { return false; }
        final IAEStack<?> normalized = target == null ? null : target.copy().setStackSize(1);
        if (normalized != null && !MaintainerBookMode.accepts(normalized, mode)) { return false; }
        if (!this.book.setTarget(index, normalized, mode)) { return false; }
        this.plans.remove(index);
        this.queue.clearCooldown(index);
        this.saveChanges();
        this.updateBlockState();
        return true;
    }

    public void configureThreshold(final int index, final long value) {
        if (index < 0 || index >= MaintainerBook.CAPACITY) { return; }
        this.book.setThreshold(index, value);
        this.plans.remove(index);
        this.saveChanges();
    }

    public void configureBatch(final int index, final long value) {
        if (index < 0 || index >= MaintainerBook.CAPACITY) { return; }
        this.book.setBatch(index, value);
        this.plans.remove(index);
        this.saveChanges();
    }

    /** 停用只阻止后续提交，已提交订单继续。 */
    public void configureEnabled(final int index, final boolean value) {
        if (index < 0 || index >= MaintainerBook.CAPACITY) { return; }
        this.book.setEnabled(index, value);
        this.plans.remove(index);
        this.saveChanges();
        this.updateBlockState();
    }

    /** 供 GUI 判定流体环境提示用。 */
    public boolean fluidEnvironmentReady() {
        return this.hasFluidDiscretizer();
    }

    private boolean hasFluidDiscretizer() {
        return FluidMaintainerBridge.hasDiscretizer(this);
    }

    // ---------------------------------------------------------------- NBT 与掉落物

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        MaintainerNbt.load(this.book, data.getCompoundTag("maintainer"));
        this.book.markChanged();
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("maintainer", MaintainerNbt.save(this.book));
        return data;
    }

    /** 正常拆卸：规则与当前订单识别信息一起写进掉落物。 */
    public void writeConfig(final ItemStack stack) {
        stack.setTagInfo("maintainer", MaintainerNbt.save(this.book));
    }

    /** 重新放置：恢复规则与订单关联；运行期状态由下一轮核对重建。 */
    public void readConfig(final ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) { return; }
        final NBTTagCompound root = stack.getTagCompound().getCompoundTag("maintainer");
        if (root.isEmpty()) { return; }
        MaintainerNbt.load(this.book, root);
        this.book.markChanged();
    }
}
