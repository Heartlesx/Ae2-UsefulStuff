package com.extrarawstyle.ae2us.mirror;

import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.core.sync.GuiBridge;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.IPriorityHost;
import appeng.parts.PartBasicState;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.InvOperation;
import com.extrarawstyle.ae2us.Ae2us;
import com.extrarawstyle.ae2us.api.IMirrorPatternSlots;
import com.extrarawstyle.ae2us.api.ISmartBlocking;
import com.extrarawstyle.ae2us.mixin.MirrorDualityInvoker;
import com.google.common.collect.ImmutableSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 镜像样板供应器：贴在线缆上的一个"只读接口"。
 * <p>
 * 内部就是一个 AE2 原生 {@link DualityInterface}，样板槽被放大到 {@link #PATTERN_SLOTS}，
 * 内容每 {@link #SYNC_INTERVAL} tick 从绑定的源接口实时读取，自己不存样板。
 * 推料、AE2FC 的假物品转真流体、阻挡模式、智能阻挡、CPU 的候选轮转因此全部是 AE2 原生逻辑，
 * 这里只负责把样板和设置搬过来。
 */
public final class PartMirrorProvider extends PartBasicState
        implements IGridTickable, IStorageMonitorable, IInventoryDestination, IInterfaceHost,
        IAEAppEngInventory, IPriorityHost {

    public static final ResourceLocation MODEL_OFF = new ResourceLocation(Ae2us.MODID, "part/mirror_provider_off");
    public static final ResourceLocation MODEL_ON = new ResourceLocation(Ae2us.MODID, "part/mirror_provider_on");
    public static final ResourceLocation MODEL_CHANNEL = new ResourceLocation(Ae2us.MODID, "part/mirror_provider_channel");

    // 每个 PartModel 只带一个模型文件：这些名字都会被 AE2 逐个烘焙，
    // 写了没注册的名字，渲染时就会抛 "unregistered part model" 把区块渲染打崩。
    // 共用底板由 JSON 的 parent 继承，不进这个列表。
    static final IPartModel[] MODELS = {
            new PartModel(MODEL_OFF),
            new PartModel(MODEL_ON),
            new PartModel(MODEL_CHANNEL)
    };

    /** 288 槽：够装 8 个塞满的正常接口（36 × 8）。 */
    static final int PATTERN_SLOTS = 288;
    /** 源接口的样板槽数，AE2 的接口固定这么多。 */
    static final int SOURCE_PATTERN_SLOTS = DualityInterface.NUMBER_OF_PATTERN_SLOTS;

    private static final int SYNC_INTERVAL = 10;

    private static final IItemHandler NO_PATTERNS = new IItemHandler() {
        @Override
        public int getSlots() {
            return 0;
        }

        @Override
        public ItemStack getStackInSlot(final int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(final int slot) {
            return 0;
        }
    };

    private final DualityInterface duality = new DualityInterface(this.getProxy(), this);
    private final List<MirrorBindings.Binding> bindings = new ArrayList<>();

    private int syncCountdown;
    private boolean truncated;

    public PartMirrorProvider(final ItemStack stack) {
        super(stack);
        ((IMirrorPatternSlots) this.duality).ae2us$resizePatternSlots(PATTERN_SLOTS);
        // 样板是只读的：让自己不出现在接口终端里，免得玩家往里塞样板又被下一次同步覆盖掉。
        this.duality.getConfigManager().registerSetting(Settings.INTERFACE_TERMINAL, YesNo.NO);
    }

    // ---------------------------------------------------------------- 绑定

    boolean isBoundTo(final int dimension, final BlockPos pos) {
        for (final MirrorBindings.Binding binding : this.bindings) {
            if (binding.matches(dimension, pos)) {
                return true;
            }
        }
        return false;
    }

    void bind(final int dimension, final BlockPos pos) {
        if (this.isBoundTo(dimension, pos)) {
            return;
        }
        this.bindings.add(new MirrorBindings.Binding(dimension, pos));
        this.syncCountdown = 0;
        this.saveChanges();
    }

    /** 只解掉指定的这一个源，镜像绑着的其它源不受影响。 */
    boolean unbind(final int dimension, final BlockPos pos) {
        for (int i = 0; i < this.bindings.size(); i++) {
            if (this.bindings.get(i).matches(dimension, pos)) {
                this.bindings.remove(i);
                this.syncCountdown = 0;
                this.saveChanges();
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 心跳

    /** 镜像必须一直醒着：源接口换样板时没有通知能叫醒它。 */
    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(5, SYNC_INTERVAL, false, true);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!this.getProxy().isActive()) {
            return TickRateModulation.SLEEP;
        }

        final TickRateModulation result = this.duality.tickingRequest(node, ticksSinceLastCall);

        if (--this.syncCountdown <= 0) {
            this.syncCountdown = SYNC_INTERVAL;
            final TileEntity tile = this.getTile();
            if (tile != null && !tile.getWorld().isRemote) {
                this.syncFromSources();
            }
        }

        // 同步本身要一直跑，所以不让芯片把它判成空闲（SLEEP 之后就再也不会被叫醒）。
        return result == TickRateModulation.SLEEP ? TickRateModulation.SLOWER : result;
    }

    // ---------------------------------------------------------------- 同步

    private void syncFromSources() {
        final List<IInterfaceHost> sources = new ArrayList<>();
        for (final MirrorBindings.Binding binding : this.bindings) {
            final IInterfaceHost source = this.resolve(binding);
            if (source != null) {
                sources.add(source);
            }
        }

        this.syncPatterns(sources);
        this.syncBlocking(sources);
        this.syncPriority(sources);
    }

    IInterfaceHost resolve(final MirrorBindings.Binding binding) {
        final TileEntity tile = this.getTile();
        if (tile == null || tile.getWorld().provider.getDimension() != binding.dimension) {
            return null;
        }
        return MirrorBindings.findSource(tile.getWorld(), binding.pos);
    }

    /**
     * 按绑定顺序把源接口的样板合并进来，同一样板只留一份。
     * 只在内容真的变了才写：AE2 的配方表用引用比较判断新旧，无条件重写会让它反复注销/重注册样板。
     */
    private void syncPatterns(final List<IInterfaceHost> sources) {
        final List<ItemStack> wanted = new ArrayList<>();
        boolean cut = false;

        for (final IInterfaceHost source : sources) {
            final IItemHandler patterns = source.getInterfaceDuality().getPatterns();
            for (int slot = 0; slot < Math.min(SOURCE_PATTERN_SLOTS, patterns.getSlots()); slot++) {
                final ItemStack stack = patterns.getStackInSlot(slot);
                if (stack.isEmpty() || containsSame(wanted, stack)) {
                    continue;
                }
                if (wanted.size() >= PATTERN_SLOTS) {
                    cut = true;
                    break;
                }
                wanted.add(stack.copy());
            }
            if (cut) {
                break;
            }
        }

        this.truncated = cut;

        final IItemHandler target = this.duality.getPatterns();
        boolean changed = false;
        for (int slot = 0; slot < PATTERN_SLOTS; slot++) {
            final ItemStack next = slot < wanted.size() ? wanted.get(slot) : ItemStack.EMPTY;
            if (sameStack(target.getStackInSlot(slot), next)) {
                continue;
            }
            changed = true;
            break;
        }
        if (!changed) {
            return;
        }

        final AppEngInternalInventory inventory = (AppEngInternalInventory) target;
        // 一次性写完再刷新配方表，否则每写一格都会重建一遍配方表并广播一次网络事件。
        inventory.setTileEntity(null);
        try {
            for (int slot = 0; slot < PATTERN_SLOTS; slot++) {
                inventory.setStackInSlot(slot, slot < wanted.size() ? wanted.get(slot) : ItemStack.EMPTY);
            }
        } finally {
            inventory.setTileEntity(this.duality);
        }
        ((MirrorDualityInvoker) this.duality).ae2us$updateCraftingList();
    }

    /** 阻挡模式与智能阻挡：任一源接口开着就算开。 */
    private void syncBlocking(final List<IInterfaceHost> sources) {
        boolean blocking = false;
        boolean smart = false;

        for (final IInterfaceHost source : sources) {
            final DualityInterface duality = source.getInterfaceDuality();
            if (duality.getConfigManager().getSetting(Settings.BLOCK) == YesNo.YES) {
                blocking = true;
            }
            if (((ISmartBlocking) duality).ae2us$isSmartBlocking()) {
                smart = true;
            }
        }

        final IConfigManager own = this.duality.getConfigManager();
        final YesNo wanted = blocking ? YesNo.YES : YesNo.NO;
        if (own.getSetting(Settings.BLOCK) != wanted) {
            own.putSetting(Settings.BLOCK, wanted);
        }
        if (((ISmartBlocking) this.duality).ae2us$isSmartBlocking() != smart) {
            ((ISmartBlocking) this.duality).ae2us$setSmartBlocking(smart);
        }
    }

    /** 优先级取所有源接口里的最大值。 */
    private void syncPriority(final List<IInterfaceHost> sources) {
        int highest = 0;
        for (final IInterfaceHost source : sources) {
            highest = Math.max(highest, source.getInterfaceDuality().getPriority());
        }
        if (this.duality.getPriority() != highest) {
            this.duality.setPriority(highest);
        }
    }

    private static boolean containsSame(final List<ItemStack> list, final ItemStack stack) {
        for (final ItemStack candidate : list) {
            if (sameStack(candidate, stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameStack(final ItemStack a, final ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() && b.isEmpty();
        }
        return a.getCount() == b.getCount() && ItemStack.areItemStacksEqual(a, b);
    }

    // ---------------------------------------------------------------- TOP 读取

    /** 绑定了几个源接口（含当前解析不到、只是坐标还留着的）。 */
    public int bindingCount() {
        return this.bindings.size();
    }

    /** 第一个绑定的源接口坐标；没有绑定时返回 null。只给 TOP 显示用。 */
    public BlockPos firstBindingPos() {
        return this.bindings.isEmpty() ? null : this.bindings.get(0).pos;
    }

    /** 已经镜像过来的样板张数（不算空槽）。 */
    public int mirroredPatternCount() {
        final IItemHandler patterns = this.duality.getPatterns();
        int used = 0;
        for (int slot = 0; slot < patterns.getSlots(); slot++) {
            if (!patterns.getStackInSlot(slot).isEmpty()) { used++; }
        }
        return used;
    }

    /** 上一次同步时是否因为超过槽位而截断。 */
    public boolean truncated() {
        return this.truncated;
    }

    // ---------------------------------------------------------------- 提示

    void describeTo(final EntityPlayer player) {
        if (this.bindings.isEmpty()) {
            player.sendMessage(new TextComponentTranslation("ae2us.mirror.status.empty"));
            return;
        }
        for (final MirrorBindings.Binding binding : this.bindings) {
            final String where = binding.pos.getX() + ", " + binding.pos.getY() + ", " + binding.pos.getZ();
            final boolean alive = this.resolve(binding) != null;
            player.sendMessage(new TextComponentTranslation(
                    alive ? "ae2us.mirror.status.bound" : "ae2us.mirror.status.offline", where));
        }
        if (this.truncated) {
            player.sendMessage(new TextComponentTranslation("ae2us.mirror.status.truncated", PATTERN_SLOTS));
        }
    }

    // ---------------------------------------------------------------- 透传

    @Override
    public void chanRender(final MENetworkChannelsChanged c) {
        this.duality.notifyNeighbors();
    }

    @Override
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.duality.notifyNeighbors();
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        return this.duality.getInstalledUpgrades(u);
    }

    @Override
    public void gridChanged() {
        this.duality.gridChanged();
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.duality.readFromNBT(data);
        this.bindings.clear();
        this.bindings.addAll(MirrorBindings.read(data));
        this.syncCountdown = 0;
    }

    /**
     * 样板槽不落盘：镜像只是别人样板的镜子，存副本会让存档凭空变大。
     * 这里删掉 AE2 写进去的 patterns 段，重新加载后由 {@link #syncFromSources()} 补回来。
     */
    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.duality.writeToNBT(data);
        data.removeTag("patterns");
        MirrorBindings.write(data, this.bindings);
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.duality.initialize();
    }

    /** 样板是源接口的，破坏镜像不掉样板；升级卡与暂存槽照常掉。 */
    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        final AppEngInternalInventory patterns = (AppEngInternalInventory) this.duality.getPatterns();
        final List<ItemStack> held = new ArrayList<>();
        patterns.setTileEntity(null);
        try {
            for (int slot = 0; slot < patterns.getSlots(); slot++) {
                held.add(patterns.getStackInSlot(slot));
                patterns.setStackInSlot(slot, ItemStack.EMPTY);
            }
            this.duality.addDrops(drops);
        } finally {
            for (int slot = 0; slot < held.size(); slot++) {
                patterns.setStackInSlot(slot, held.get(slot));
            }
            patterns.setTileEntity(this.duality);
        }
    }

    /** 设置一律由源接口决定，记忆卡不参与，免得改完 10 tick 又被同步覆盖。 */
    @Override
    public boolean useStandardMemoryCard() {
        return false;
    }

    @Override
    public float getCableConnectionLength(final AECableType cable) {
        return 4;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.duality.getConfigManager();
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        // 样板只读：对外一律是空库存，别的东西写不进来。
        return "patterns".equals(name) ? NO_PATTERNS : this.duality.getInventoryByName(name);
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (player.getHeldItem(hand).getItem() instanceof ItemMirrorBinder) {
            return false;
        }
        if (!player.getEntityWorld().isRemote) {
            this.describeTo(player);
        }
        return true;
    }

    @Override
    public boolean canInsert(final ItemStack stack) {
        return this.duality.canInsert(stack);
    }

    @Override
    public <T extends IAEStack<T>> IMEMonitor<T> getInventory(final IStorageChannel<T> channel) {
        return this.duality.getInventory(channel);
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        this.duality.onChangeInventory(inv, slot, mc, removedStack, newStack);
    }

    @Override
    public void onStackReturnNetwork(final IAEItemStack stack) {
        this.duality.onStackReturnedToNetwork(stack);
    }

    @Override
    public DualityInterface getInterfaceDuality() {
        return this.duality;
    }

    /** 跟 AE2 的接口面板一样，只往贴着的那一面推料。 */
    @Override
    public EnumSet<EnumFacing> getTargets() {
        return EnumSet.of(this.getSide().getFacing());
    }

    @Override
    public TileEntity getTileEntity() {
        return super.getHost().getTile();
    }

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        return this.duality.pushPattern(patternDetails, table);
    }

    @Override
    public boolean isBusy() {
        return this.duality.isBusy();
    }

    @Override
    public void provideCrafting(final ICraftingProviderHelper craftingTracker) {
        this.duality.provideCrafting(craftingTracker);
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.duality.getRequestedJobs();
    }

    @Override
    public IAEItemStack injectCraftedItems(final ICraftingLink link, final IAEItemStack items, final Actionable mode) {
        return this.duality.injectCraftedItems(link, items, mode);
    }

    @Override
    public void jobStateChange(final ICraftingLink link) {
        this.duality.jobStateChange(link);
    }

    @Override
    public int getPriority() {
        return this.duality.getPriority();
    }

    @Override
    public void setPriority(final int newValue) {
        this.duality.setPriority(newValue);
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return new ItemStack(MirrorRegistration.PROVIDER);
    }

    @Override
    public GuiBridge getGuiBridge() {
        return GuiBridge.GUI_INTERFACE;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS[this.isPowered() ? (this.isActive() ? 2 : 1) : 0];
    }
}
