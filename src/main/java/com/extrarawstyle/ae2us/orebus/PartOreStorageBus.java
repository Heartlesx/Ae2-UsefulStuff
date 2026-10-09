package com.extrarawstyle.ae2us.orebus;

import appeng.api.AEApi;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Upgrades;
import appeng.api.parts.IPartModel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.storage.MEInventoryHandler;
import appeng.parts.PartModel;
import appeng.parts.misc.PartStorageBus;
import appeng.util.SettingsFrom;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class PartOreStorageBus extends PartStorageBus {
    public static final ResourceLocation OFF = new ResourceLocation(Ae2us.MODID, "part/ore_storage_bus_off");
    public static final ResourceLocation ON = new ResourceLocation(Ae2us.MODID, "part/ore_storage_bus_on");
    public static final ResourceLocation CHANNEL = new ResourceLocation(Ae2us.MODID, "part/ore_storage_bus_channel");
    public static final String RULES_KEY = "oreStorageRules";
    private static final IPartModel[] MODELS = {new PartModel(OFF), new PartModel(ON), new PartModel(CHANNEL)};
    private final OreExportRules rules = new OreExportRules();
    private MEInventoryHandler<IAEItemStack> configuredHandler;
    private boolean postingReset;

    public PartOreStorageBus(final ItemStack stack) {
        super(stack);
        if (stack.hasTagCompound()) {
            final NBTTagCompound settings = stack.getTagCompound().getCompoundTag("oreStorageSettings");
            this.rules.read(settings.getCompoundTag("oreStorageRules"));
            this.priority = settings.getInteger("priority");
            this.getConfigManager().readFromNBT(settings);
        }
    }

    @Override
    public void updateSetting(final appeng.api.util.IConfigManager manager, final Enum setting, final Enum value) {
        if (this.getHost() != null) { super.updateSetting(manager, setting, value); }
    }

    public OreExportRules rules() { return this.rules; }

    @Override
    protected int getUpgradeSlots() { return 1; }

    public void changed() {
        this.resetCache(true);
        this.getHost().markForSave();
    }

    @Override
    public MEInventoryHandler<IAEItemStack> getInternalHandler() {
        final MEInventoryHandler<IAEItemStack> result = super.getInternalHandler();
        if (result != null && result != this.configuredHandler) {
            this.configuredHandler = result;
            result.setWhitelist(IncludeExclude.WHITELIST);
            result.setPartitionList(new OreStoragePartition(this.rules));
            result.setSticky(this.getInstalledUpgrades(Upgrades.STICKY) > 0);
        }
        return result;
    }

    @Override
    protected void resetCache() {
        // Inventory diffs include removals from the previous partition, not just new matches.
        this.postingReset = true;
        try { super.resetCache(); }
        finally { this.postingReset = false; }
    }

    @Override
    protected Iterable<IAEItemStack> filterChanges(final Iterable<IAEItemStack> change) {
        return this.postingReset ? change : super.filterChanges(change);
    }

    public List<String> partitionCandidates() {
        final TreeSet<String> names = new TreeSet<>();
        final MEInventoryHandler<IAEItemStack> inventory = this.getInternalHandler();
        if (inventory != null) {
            for (final IAEItemStack stack : inventory.getInternal().getAvailableItems(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList())) {
                for (final int id : OreDictionary.getOreIDs(stack.getDefinition())) {
                    final String name = OreDictionary.getOreName(id);
                    if (OreExportRules.valid(name)) { names.add(name); }
                }
            }
        }
        return new ArrayList<>(names);
    }

    @Override
    public void readFromNBT(final NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.rules.read(tag.getCompoundTag(RULES_KEY));
    }

    @Override
    public void writeToNBT(final NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setTag(RULES_KEY, this.rules.write());
    }

    // 记忆卡复制：AE2 基类只带 config 管理器（访问/过滤模式）、优先级和 "config" 槽，矿典规则要自己补进载荷。
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
    public ItemStack getItemStackRepresentation() { return new ItemStack(OreBusRegistration.STORAGE_BUS); }

    @Override
    public ItemStack getItemStack(final appeng.api.parts.PartItemStack type) {
        final ItemStack stack = super.getItemStack(type).copy();
        if (type == appeng.api.parts.PartItemStack.BREAK || type == appeng.api.parts.PartItemStack.WRENCH) {
            final NBTTagCompound settings = new NBTTagCompound();
            settings.setTag(RULES_KEY, this.rules.write());
            settings.setInteger("priority", this.priority);
            settings.setString("ACCESS", this.getConfigManager().getSetting(appeng.api.config.Settings.ACCESS).name());
            settings.setString("STORAGE_FILTER", this.getConfigManager().getSetting(appeng.api.config.Settings.STORAGE_FILTER).name());
            final NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound().copy() : new NBTTagCompound();
            tag.setTag("oreStorageSettings", settings);
            stack.setTagCompound(tag);
        }
        return stack;
    }

    @Override
    public void securityBreak() {
        if (this.getItemStack().getCount() > 0 && this.getGridNode() != null) {
            final List<ItemStack> drops = new ArrayList<>();
            drops.add(this.getItemStack(appeng.api.parts.PartItemStack.BREAK));
            this.getDrops(drops, false);
            this.getHost().removePart(this.getSide(), false);
            appeng.util.Platform.spawnDrops(this.getTile().getWorld(), this.getTile().getPos(), drops);
            this.getItemStack().setCount(0);
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
    public IPartModel getStaticModels() { return MODELS[this.isPowered() ? (this.isActive() ? 2 : 1) : 0]; }
}
