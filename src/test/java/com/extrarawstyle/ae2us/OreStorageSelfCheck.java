package com.extrarawstyle.ae2us;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.item.AEItemStack;
import appeng.util.item.ItemList;
import com.extrarawstyle.ae2us.orebus.OreBusNetwork;
import com.extrarawstyle.ae2us.orebus.OreExportRules;
import com.extrarawstyle.ae2us.orebus.OreStoragePartition;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

public final class OreStorageSelfCheck {
    public static void verify() {
        final Fixture inventory = new Fixture();
        final OreExportRules rules = new OreExportRules();
        final MEInventoryHandler<IAEItemStack> handler = new MEInventoryHandler<>(inventory, inventory.getChannel());
        handler.setWhitelist(IncludeExclude.WHITELIST);
        handler.setStorageFilter(StorageFilter.EXTRACTABLE_ONLY);
        handler.setPartitionList(new OreStoragePartition(rules));
        check(!handler.canAccept(inventory.iron) && handler.extractItems(inventory.iron, Actionable.SIMULATE, null) == null
                && handler.getAvailableItems(new ItemList()).isEmpty(), "empty whitelist denies insertion, extraction and filtered visibility");
        rules.add(true, "ingotGold");
        handler.setPartitionList(new OreStoragePartition(rules));
        check(!handler.canAccept(inventory.iron), "blacklist alone cannot enable access");
        rules.add(false, "nonexistentOreTagForAE2US");
        handler.setPartitionList(new OreStoragePartition(rules));
        check(!handler.canAccept(inventory.iron), "unknown ore rule cannot turn into allow all");
        rules.add(false, "ingot*");
        final OreStoragePartition before = new OreStoragePartition(rules);
        handler.setPartitionList(before);
        check(handler.canAccept(inventory.iron) && !handler.canAccept(inventory.gold), "wildcard whitelist and blacklist work together");
        check(handler.getAvailableItems(new ItemList()).size() == 1, "terminal exposes only allowed item");
        final long ironCount = inventory.iron.getStackSize();
        final IAEItemStack request = inventory.iron.copy();
        request.setStackSize(5);
        handler.extractItems(request, Actionable.SIMULATE, null);
        check(inventory.iron.getStackSize() == ironCount, "simulation does not modify inventory");
        final IAEItemStack extracted = handler.extractItems(request, Actionable.MODULATE, null);
        check(extracted.getStackSize() == 5 && inventory.iron.getStackSize() == ironCount - 5, "allowed extraction delegates once");
        check(handler.injectItems(extracted, Actionable.MODULATE, null) == null && inventory.iron.getStackSize() == ironCount,
                "allowed insertion restores original count");
        check(handler.injectItems(inventory.gold, Actionable.MODULATE, null) != null
                && inventory.gold.getStackSize() == 20, "rejected insertion conserves inventory");
        handler.setStorageFilter(StorageFilter.NONE);
        check(handler.getAvailableItems(new ItemList()).size() == 2
                && handler.extractItems(inventory.gold, Actionable.MODULATE, null) == null, "show all never bypasses extraction filter");
        handler.setBaseAccess(AccessRestriction.WRITE);
        check(handler.getAvailableItems(new ItemList()).isEmpty() && handler.canAccept(inventory.iron)
                && handler.extractItems(inventory.iron, Actionable.SIMULATE, null) == null, "write only respects access");
        handler.setBaseAccess(AccessRestriction.READ);
        check(!handler.canAccept(inventory.iron) && handler.extractItems(request, Actionable.SIMULATE, null) != null,
                "read only respects access");
        handler.setPriority(-1234);
        handler.setSticky(true);
        check(handler.getPriority() == -1234 && handler.isSticky(), "native priority and sticky properties retained");
        rules.add(true, "ingotIron");
        check(before.isListed(inventory.iron) && !new OreStoragePartition(rules).isListed(inventory.iron),
                "old partition is immutable for before/after inventory diffs");
        handler.setBaseAccess(AccessRestriction.READ_WRITE);
        handler.setStorageFilter(StorageFilter.EXTRACTABLE_ONLY);
        handler.setPartitionList(new OreStoragePartition(rules));
        check(handler.getAvailableItems(new ItemList()).isEmpty(), "updated blacklist hides previously available item");
        final OreExportRules restored = new OreExportRules();
        restored.read(rules.write());
        check(!new OreStoragePartition(restored).isListed(inventory.iron), "blacklist survives reload");
        final OreExportRules noTags = new OreExportRules();
        noTags.add(false, "*");
        check(!new OreStoragePartition(noTags).isListed(AEItemStack.fromItemStack(new ItemStack(Items.WRITTEN_BOOK))),
                "wildcard matches ore names, not untagged items");
        final OreExportRules multiple = new OreExportRules();
        multiple.add(false, "ingotIron");
        multiple.add(true, "ae2usStorageBlocked");
        OreDictionary.registerOre("ae2usStorageBlocked", new ItemStack(Items.IRON_INGOT));
        check(!new OreStoragePartition(multiple).isListed(inventory.iron), "blacklist wins across different real ore tags");
        final OreExportRules large = new OreExportRules();
        final StringBuilder names = new StringBuilder();
        for (int i = 0; i < 10000; i++) { names.append("storageOre").append(i).append('\n'); }
        large.add(false, names.toString());
        OreDictionary.registerOre("storageOre9999", new ItemStack(Items.GOLD_INGOT));
        final OreExportRules largeRestored = new OreExportRules();
        largeRestored.read(large.write());
        check(new OreStoragePartition(largeRestored).isListed(inventory.gold), "10000th storage rule survives reload and matches");
        final OreBusNetwork.State state = new OreBusNetwork.State();
        state.storage = true; state.sticky = true; state.priority = Integer.MIN_VALUE; state.access = 2; state.display = 1;
        state.total = 10000; state.offset = 9992;
        for (int i = 0; i < 8; i++) { state.names.add("storageOre" + (9992 + i)); }
        final io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        state.toBytes(buffer);
        check(buffer.readableBytes() < 256, "storage state remains paginated");
        final OreBusNetwork.State decoded = new OreBusNetwork.State();
        decoded.fromBytes(buffer);
        buffer.release();
        check(decoded.storage && decoded.sticky && decoded.priority == Integer.MIN_VALUE && decoded.access == 2
                && decoded.display == 1 && decoded.names.equals(state.names), "storage settings roundtrip");
        final OreBusNetwork.Partition partition = new OreBusNetwork.Partition();
        partition.window = 42; partition.first = true; partition.last = false;
        for (int i = 0; i < 24; i++) { partition.names.add("storageOre" + i); }
        final io.netty.buffer.ByteBuf chunk = io.netty.buffer.Unpooled.buffer();
        partition.toBytes(chunk);
        final OreBusNetwork.Partition read = new OreBusNetwork.Partition();
        read.fromBytes(chunk);
        chunk.release();
        check(read.window == 42 && read.first && !read.last && read.names.equals(partition.names), "partition candidates use bounded chunks");
        final ItemStack configured = new ItemStack(Items.STICK);
        final NBTTagCompound settings = new NBTTagCompound();
        settings.setTag("oreStorageRules", large.write());
        settings.setInteger("priority", -99);
        settings.setString("ACCESS", "WRITE");
        settings.setString("STORAGE_FILTER", "NONE");
        final NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setTag("oreStorageSettings", settings);
        configured.setTagCompound(itemTag);
        final com.extrarawstyle.ae2us.orebus.PartOreStorageBus part =
                new com.extrarawstyle.ae2us.orebus.PartOreStorageBus(configured);
        check(part.rules().list(false).size() == 10000 && part.getPriority() == -99
                && part.getConfigManager().getSetting(appeng.api.config.Settings.ACCESS) == AccessRestriction.WRITE,
                "part restores item settings before a host exists");
        check(part.getInventoryByName("upgrades").getSlots() == 1
                && part.getProxy().getFlags().contains(appeng.api.networking.GridFlags.REQUIRE_CHANNEL),
                "one upgrade slot and ordinary channel requirement");
        final ItemStack dropped = part.getItemStack(appeng.api.parts.PartItemStack.WRENCH);
        final NBTTagCompound droppedSettings = dropped.getTagCompound().getCompoundTag("oreStorageSettings");
        check(!droppedSettings.hasKey("upgrades") && !droppedSettings.hasKey("part") && !droppedSettings.hasKey("config"),
                "drop carries settings without duplicating cards or grid identity");
        final com.extrarawstyle.ae2us.orebus.PartOreStorageBus placed =
                new com.extrarawstyle.ae2us.orebus.PartOreStorageBus(dropped);
        check(placed.rules().list(false).equals(part.rules().list(false)) && placed.getPriority() == -99
                && placed.getConfigManager().getSetting(appeng.api.config.Settings.STORAGE_FILTER) == StorageFilter.NONE,
                "wrench drop and replacement preserve all configured settings");
        final NBTTagCompound worldSave = new NBTTagCompound();
        part.writeToNBT(worldSave);
        placed.readFromNBT(worldSave);
        check(placed.rules().list(false).size() == 10000 && placed.getPriority() == -99,
                "part world save roundtrip retains large rule lists");
        System.out.println("Ore storage checks passed (empty/unknown rules, access, display, conservation, snapshots, 10000 rules and packets)");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class Fixture implements IMEInventory<IAEItemStack> {
        final IAEItemStack iron = AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT, 30));
        final IAEItemStack gold = AEItemStack.fromItemStack(new ItemStack(Items.GOLD_INGOT, 20));
        @Override
        public IAEItemStack injectItems(final IAEItemStack input, final Actionable mode, final IActionSource source) {
            final IAEItemStack entry = input.getItem() == Items.IRON_INGOT ? this.iron : this.gold;
            if (mode == Actionable.MODULATE) { entry.incStackSize(input.getStackSize()); }
            return null;
        }
        @Override
        public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final IActionSource source) {
            final IAEItemStack entry = request.getItem() == Items.IRON_INGOT ? this.iron : this.gold;
            final IAEItemStack result = request.copy();
            result.setStackSize(Math.min(request.getStackSize(), entry.getStackSize()));
            if (mode == Actionable.MODULATE) { entry.decStackSize(result.getStackSize()); }
            return result.getStackSize() == 0 ? null : result;
        }
        @Override
        public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out) {
            out.add(this.iron.copy()); out.add(this.gold.copy()); return out;
        }
        @Override
        public IStorageChannel<IAEItemStack> getChannel() {
            return new IStorageChannel<IAEItemStack>() {
                @Override public IItemList<IAEItemStack> createList() { return new ItemList(); }
                @Override public IAEItemStack createStack(final Object item) { return AEItemStack.fromItemStack((ItemStack) item); }
                @Override public IAEItemStack readFromPacket(final io.netty.buffer.ByteBuf buffer) { throw new AssertionError("unused"); }
                @Override public IAEItemStack createFromNBT(final NBTTagCompound tag) { return AEItemStack.fromNBT(tag); }
            };
        }
    }
}
