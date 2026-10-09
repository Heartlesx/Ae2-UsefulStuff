package com.extrarawstyle.ae2us.orebus;

import appeng.api.AEApi;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import appeng.parts.PartModel;
import appeng.parts.automation.PartSharedItemBus;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PartOreExportBus extends PartSharedItemBus {
    public static final ResourceLocation OFF = new ResourceLocation(Ae2us.MODID, "part/ore_export_bus_off");
    public static final ResourceLocation ON = new ResourceLocation(Ae2us.MODID, "part/ore_export_bus_on");
    public static final ResourceLocation CHANNEL = new ResourceLocation(Ae2us.MODID, "part/ore_export_bus_channel");
    public static final String RULES_KEY = "oreExportRules";
    private static final IPartModel[] MODELS = {new PartModel(OFF), new PartModel(ON), new PartModel(CHANNEL)};

    private final OreExportRules rules = new OreExportRules();
    private final OreExportScheduler<IAEItemStack> scheduler = new OreExportScheduler<>();
    private final Map<Item, Map<Integer, int[]>> matches = new HashMap<>();
    private final IActionSource source = new MachineSource(this);
    private int cachedRevision = -1;
    private int cachedOreRevision = -1;
    private final OreExportTransfer transfer = new OreExportTransfer();

    public PartOreExportBus(final ItemStack stack) {
        super(stack);
        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.getProxy().setIdlePowerUsage(1.0);
    }

    public OreExportRules rules() { return this.rules; }

    @Override
    protected int getUpgradeSlots() { return 5; }

    @Override
    protected int calculateItemsToSend() {
        return OreExportScheduler.budget(this.getInstalledUpgrades(Upgrades.SPEED));
    }

    public void changed() {
        this.scheduler.reset();
        this.matches.clear();
        this.saveChanges();
        this.upgradesChanged();
    }

    @Override
    public RedstoneMode getRSMode() {
        return (RedstoneMode) this.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED);
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.ExportBus.getMin(), TickRates.ExportBus.getMax(), this.isSleeping(), false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        return this.isSleeping() ? TickRateModulation.SLEEP : this.doBusWork();
    }

    @Override
    protected boolean isSleeping() {
        return this.getHandler() == null || super.isSleeping();
    }

    @Override
    protected TickRateModulation doBusWork() {
        if (!this.getProxy().isActive() || !this.canDoBusWork()) { return TickRateModulation.IDLE; }
        try {
            final InventoryAdaptor destination = this.getHandler();
            if (destination == null) { return TickRateModulation.SLEEP; }
            final IMEMonitor<IAEItemStack> inventory = this.getProxy().getStorage().getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            if (!this.transfer.pending().isEmpty()) {
                final boolean recovered = this.transfer.recover(inventory, this.source);
                this.saveChanges();
                if (!recovered) { return TickRateModulation.SLOWER; }
            }
            if (this.rules.list(false).isEmpty()) { return TickRateModulation.SLOWER; }
            if (this.cachedRevision != this.rules.revision() || this.cachedOreRevision != OreBusRegistration.oreRevision) {
                this.matches.clear();
                this.cachedRevision = this.rules.revision();
                this.cachedOreRevision = OreBusRegistration.oreRevision;
            }
            final List<OreExportScheduler.Stock<IAEItemStack>> stock = new ArrayList<>();
            for (final IAEItemStack entry : inventory.getStorageList()) {
                if (entry.getStackSize() <= 0) { continue; }
                final ItemStack stack = entry.getDefinition();
                final Map<Integer, int[]> variants = this.matches.computeIfAbsent(stack.getItem(), ignored -> new HashMap<>());
                final int[] rows = variants.computeIfAbsent(stack.getItemDamage(), ignored -> this.match(stack));
                if (rows.length > 0) { stock.add(new OreExportScheduler.Stock<>(entry.copy(), entry.getStackSize(), rows)); }
            }
            final IEnergyGrid energy = this.getProxy().getEnergy();
            final int sent = this.scheduler.export(stock, this.rules.mode(), this.calculateItemsToSend(),
                    Platform.getRandom(), (item, limit) -> {
                        try {
                            return this.transfer.send(destination, inventory, energy, this.source, item, limit);
                        } finally {
                            if (!this.transfer.pending().isEmpty()) { this.saveChanges(); }
                        }
                    });
            return sent > 0 ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
        } catch (final GridAccessException ignored) {
            return TickRateModulation.IDLE;
        }
    }

    private int[] match(final ItemStack stack) {
        final int[] ids = OreDictionary.getOreIDs(stack);
        final String[] names = new String[ids.length];
        for (int i = 0; i < ids.length; i++) { names[i] = OreDictionary.getOreName(ids[i]); }
        return this.rules.matchingRows(names);
    }

    @Override
    public void readFromNBT(final NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.rules.read(tag.getCompoundTag(RULES_KEY));
        this.transfer.read(tag);
        this.scheduler.reset();
        this.matches.clear();
    }

    @Override
    public void writeToNBT(final NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag(RULES_KEY, this.rules.write());
        this.transfer.write(tag);
    }

    // 记忆卡复制：AE2 基类只带 config 管理器（红石设置）、优先级和 "config" 槽，矿典规则要自己补进载荷。
    @Override
    protected NBTTagCompound downloadSettings(final SettingsFrom from, final NBTTagCompound output) {
        final NBTTagCompound tag = super.downloadSettings(from, output);
        if (from == SettingsFrom.MEMORY_CARD) {
            tag.setTag(RULES_KEY, this.rules.write());
        }
        return tag;
    }

    @Override
    public void uploadSettings(final SettingsFrom from, final NBTTagCompound compound, final EntityPlayer player) {
        super.uploadSettings(from, compound, player);
        if (from == SettingsFrom.MEMORY_CARD) {
            this.rules.read(compound.getCompoundTag(RULES_KEY));
            this.changed();
        }
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        super.getDrops(drops, wrenched);
        if (!this.transfer.pending().isEmpty()) { drops.add(this.transfer.pending().copy()); }
    }

    @Override
    public void securityBreak() {
        if (this.getItemStack().getCount() > 0 && this.getGridNode() != null) {
            final List<ItemStack> drops = new ArrayList<>();
            this.getDrops(drops, false);
            super.securityBreak();
            Platform.spawnDrops(this.getTile().getWorld(), this.getTile().getPos(), drops);
        }
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d position) {
        if (!player.world.isRemote) {
            player.openGui(Ae2us.INSTANCE, this.getSide().ordinal(), player.world,
                    this.getTile().getPos().getX(), this.getTile().getPos().getY(), this.getTile().getPos().getZ());
        }
        return true;
    }

    @Override
    public void getBoxes(final IPartCollisionHelper boxes) {
        boxes.addBox(3, 3, 11, 13, 13, 13);
        boxes.addBox(5, 5, 13, 11, 11, 15);
    }

    @Override
    public float getCableConnectionLength(final AECableType cable) { return 5; }

    @Override
    public IPartModel getStaticModels() {
        return MODELS[this.isPowered() ? (this.isActive() ? 2 : 1) : 0];
    }
}
