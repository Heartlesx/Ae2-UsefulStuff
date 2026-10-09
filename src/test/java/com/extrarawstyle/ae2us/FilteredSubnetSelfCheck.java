package com.extrarawstyle.ae2us;

import appeng.api.config.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.*;
import appeng.api.storage.data.*;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.NetworkInventoryHandler;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.inv.ItemListIgnoreCrafting;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.*;

public final class FilteredSubnetSelfCheck {
    private static final IStorageChannel<Stack> CHANNEL = new Channel();

    public static void main(final String[] args) {
        final NetworkInventoryHandler<Stack> legacyChild = new NetworkInventoryHandler<>(CHANNEL, null);
        legacyChild.addNewStorage(new Inventory(new Stack("dust", 40), new Stack("ore", 12)));
        final NetworkInventoryHandler<Stack> legacyParent = new NetworkInventoryHandler<>(CHANNEL, null);
        legacyParent.addNewStorage(filtered(legacyChild, "dust"));
        legacyParent.addNewStorage(filtered(legacyChild, "ore"));
        final IItemList<Stack> legacy = legacyParent.getAvailableItems(CHANNEL.createList());
        check(count(legacy, "dust") == 40 && count(legacy, "ore") == 0, "reproduce the original per-pass omission");

        final PatchedNetwork child = new PatchedNetwork();
        child.addNewStorage(new Inventory(new Stack("dust", 40), new Stack("ore", 12)));
        final PatchedNetwork parent = new PatchedNetwork();
        parent.addNewStorage(filtered(child, "dust"));
        parent.addNewStorage(filtered(child, "ore"));
        final IItemList<Stack> result = parent.getAvailableItems(CHANNEL.createList());
        check(count(result, "dust") == 40 && count(result, "ore") == 12,
                "two filtered paths must expose dust and ore from the same subnet");
        for (final boolean reverse : new boolean[]{false, true}) {
            for (final int priority : new int[]{-10, 0, 10}) {
                final PatchedNetwork ordered = new PatchedNetwork();
                final MEInventoryHandler<Stack> dust = filtered(child, "dust");
                dust.setPriority(priority);
                if (reverse) { ordered.addNewStorage(filtered(child, "ore")); }
                ordered.addNewStorage(dust);
                if (!reverse) { ordered.addNewStorage(filtered(child, "ore")); }
                final MEInventoryHandler<Stack> writer = filtered(child, "dust");
                writer.setBaseAccess(AccessRestriction.WRITE);
                writer.setPriority(100);
                writer.setSticky(true);
                ordered.addNewStorage(writer);
                final IItemList<Stack> listed = ordered.getAvailableItems(CHANNEL.createList());
                check(count(listed, "dust") == 40 && count(listed, "ore") == 12,
                        "order, priority, sticky grouping and write-only path cannot hide items");
            }
        }
        parent.addNewStorage(filtered(child, "dust"));
        final MEInventoryHandler<Stack> all = new ScopedFilter(child);
        all.setStorageFilter(StorageFilter.NONE);
        all.setBaseAccess(AccessRestriction.READ);
        parent.addNewStorage(all);
        check(count(parent.getAvailableItems(CHANNEL.createList()), "dust") == 40, "overlapping filters do not multiply stock");

        final PatchedNetwork second = new PatchedNetwork();
        second.addNewStorage(new Inventory(new Stack("dust", 7)));
        parent.addNewStorage(filtered(second, "dust"));
        check(count(parent.getAvailableItems(CHANNEL.createList()), "dust") == 47,
                "same item in independent inventories must sum instead of using a global maximum");
        second.addNewStorage(filtered(parent, "dust"));
        child.addNewStorage(filtered(second, "dust"));
        check(count(parent.getAvailableItems(CHANNEL.createList()), "dust") == 47,
                "longer cycles and diamond paths stop recursion without duplicating inventories");
        final Inventory local = new Inventory(new Stack("dust", 7)) {
            @Override public IItemList<Stack> getAvailableItems(final IItemList<Stack> out) {
                child.getAvailableItems(out);
                return super.getAvailableItems(out);
            }
        };
        final PatchedNetwork hybrid = new PatchedNetwork();
        hybrid.addNewStorage(filtered(local, "dust"));
        hybrid.addNewStorage(filtered(local, "dust"));
        check(count(hybrid.getAvailableItems(CHANNEL.createList()), "dust") == 54,
                "a forwarding handler with local content counts each source only once");

        final Inventory shared = new Inventory(new Stack("dust", 6));
        final PatchedNetwork sharedNetwork = new PatchedNetwork();
        sharedNetwork.addNewStorage(filtered(shared, "dust"));
        sharedNetwork.addNewStorage(filtered(shared, "dust"));
        check(count(sharedNetwork.getAvailableItems(CHANNEL.createList()), "dust") == 6,
                "different wrappers around one physical handler retain the same origin");

        final PatchedNetwork stickyNetwork = new PatchedNetwork();
        final MEInventoryHandler<Stack> sticky = filtered(child, "ore");
        sticky.setSticky(true);
        stickyNetwork.addNewStorage(sticky);
        check(count(stickyNetwork.getAvailableItems(CHANNEL.createList()), "ore") == 12, "sticky read filters are preserved");
        final MEInventoryHandler<Stack> blacklist = filtered(child, "dust");
        blacklist.setWhitelist(IncludeExclude.BLACKLIST);
        final PatchedNetwork blacklisted = new PatchedNetwork();
        blacklisted.addNewStorage(blacklist);
        check(count(blacklisted.getAvailableItems(CHANNEL.createList()), "dust") == 0
                && count(blacklisted.getAvailableItems(CHANNEL.createList()), "ore") == 12, "blacklist remains effective");

        final Inventory craftInventory = new Inventory(new Stack("craft", 3).setCraftable(true).setCountRequestable(5));
        final PatchedNetwork craftNetwork = new PatchedNetwork();
        craftNetwork.addNewStorage(craftInventory);
        final IItemList<Stack> ignored = CHANNEL.createList();
        craftNetwork.getAvailableItems(new ItemListIgnoreCrafting<>(ignored));
        check(!ignored.getFirstItem().craftable && ignored.getFirstItem().requestable == 5,
                "pass-through suppresses crafting but keeps requestable quantities");
        check(craftNetwork.getAvailableItems(CHANNEL.createList()).getFirstItem().craftable,
                "crafting state does not leak from one scan into the next");

        final IActionSource source = new IActionSource() {
            @Override public Optional<net.minecraft.entity.player.EntityPlayer> player() { return Optional.empty(); }
            @Override public Optional<appeng.api.networking.security.IActionHost> machine() { return Optional.empty(); }
            @Override public <T> Optional<T> context(final Class<T> type) { return Optional.empty(); }
        };
        check(filtered(shared, "ore").extractItems(new Stack("dust", 1), Actionable.MODULATE, source) == null,
                "read filter still denies extracting excluded stock");
        final MEInventoryHandler<Stack> readonly = filtered(shared, "dust");
        check(readonly.injectItems(new Stack("dust", 1), Actionable.MODULATE, source) != null,
                "read-only path cannot write");
        readonly.setBaseAccess(AccessRestriction.WRITE);
        check(readonly.injectItems(new Stack("dust", 2), Actionable.MODULATE, source) == null,
                "write-only path still writes using the original implementation");
        check(count(sharedNetwork.getAvailableItems(CHANNEL.createList()), "dust") == 8,
                "a new scan sees changed stock without retaining a stale snapshot");
        check(sharedNetwork.extractItems(new Stack("dust", 3), Actionable.MODULATE, source).size == 3,
                "original network extraction path still works");
        check(count(sharedNetwork.getAvailableItems(CHANNEL.createList()), "dust") == 5,
                "actual extraction is reflected by the next scan");

        final PatchedNetwork failing = new PatchedNetwork();
        failing.addNewStorage(new Inventory() {
            @Override public IItemList<Stack> getAvailableItems(final IItemList<Stack> out) { throw new IllegalStateException("fixture"); }
        });
        final PatchedNetwork filteredFailure = new PatchedNetwork();
        filteredFailure.addNewStorage(filtered(failing, "ore"));
        try {
            filteredFailure.getAvailableItems(CHANNEL.createList());
            throw new AssertionError("fixture must throw");
        } catch (final IllegalStateException expected) {
            check("fixture".equals(expected.getMessage()), "expected fixture failure");
        }
        check(DEPTH.get().isEmpty() && count(sharedNetwork.getAvailableItems(CHANNEL.createList()), "dust") == 5,
                "exception cleanup preserves following scans and traversal depth");
        System.out.println("Filtered subnet checks passed (legacy repro, overlap, order, cycles, sources and access)");
    }

    private static MEInventoryHandler<Stack> filtered(final IMEInventory<Stack> inventory, final String key) {
        final MEInventoryHandler<Stack> handler = new ScopedFilter(inventory);
        handler.setBaseAccess(AccessRestriction.READ);
        handler.setStorageFilter(StorageFilter.EXTRACTABLE_ONLY);
        handler.setPartitionList(new IPartitionList<Stack>() {
            @Override public boolean isListed(final Stack stack) { return stack.key.equals(key); }
            @Override public boolean isEmpty() { return false; }
            @Override public Iterable<Stack> getItems() { return Collections.singletonList(new Stack(key, 1)); }
        });
        return handler;
    }

    private static final ThreadLocal<Deque<Object>> DEPTH = ThreadLocal.withInitial(ArrayDeque::new);

    private static final class PatchedNetwork extends NetworkInventoryHandler<Stack> {
        private final NavigableMap<Integer, List<IMEInventoryHandler<Stack>>> normal = new TreeMap<>(Comparator.reverseOrder());
        private final NavigableMap<Integer, List<IMEInventoryHandler<Stack>>> sticky = new TreeMap<>(Comparator.reverseOrder());
        private PatchedNetwork() { super(CHANNEL, null); }
        @Override public void addNewStorage(final IMEInventoryHandler<Stack> inventory) {
            super.addNewStorage(inventory);
            (inventory.isSticky() ? sticky : normal).computeIfAbsent(inventory.getPriority(), key -> new ArrayList<>()).add(inventory);
        }
        @Override public IItemList<Stack> getAvailableItems(final IItemList<Stack> out) {
            return FilteredSubnetScan.scan(this, CHANNEL, DEPTH.get(), out, normal.values(), sticky.values());
        }
    }

    private static final class ScopedFilter extends MEInventoryHandler<Stack> {
        private ScopedFilter(final IMEInventory<Stack> inventory) { super(inventory, CHANNEL); }
        @Override public IItemList<Stack> getAvailableItems(final IItemList<Stack> out) {
            if (getStorageFilter() != StorageFilter.EXTRACTABLE_ONLY) { return super.getAvailableItems(out); }
            final IMEInventoryHandler<Stack> original = new appeng.me.storage.MEPassThrough<Stack>(this, CHANNEL) {
                @Override @SuppressWarnings("unchecked")
                public IItemList<Stack> getAvailableItems(final IItemList result) {
                    return ScopedFilter.super.getAvailableItems(result);
                }
            };
            return FilteredSubnetScan.filtered(original, out, this::shouldItemBeAvailable);
        }
    }

    private static long count(final IItemList<Stack> list, final String key) {
        final Stack stack = list.findPrecise(new Stack(key, 1));
        return stack == null ? 0 : stack.getStackSize();
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static class Inventory implements IMEInventoryHandler<Stack> {
        private final List<Stack> contents;
        private Inventory(final Stack... stacks) { contents = Arrays.asList(stacks); }
        @Override public IItemList<Stack> getAvailableItems(final IItemList<Stack> out) {
            contents.forEach(out::add);
            return out;
        }
        @Override public Stack injectItems(final Stack input, final Actionable mode, final IActionSource source) {
            final Stack existing = contents.stream().filter(stack -> stack.equals(input)).findFirst().orElse(null);
            if (existing == null) { return input; }
            if (mode == Actionable.MODULATE) { existing.incStackSize(input.size); }
            return null;
        }
        @Override public Stack extractItems(final Stack request, final Actionable mode, final IActionSource source) {
            for (final Stack stack : contents) {
                if (stack.equals(request)) {
                    final Stack result = request.copy().setStackSize(Math.min(request.size, stack.size));
                    if (mode == Actionable.MODULATE) { stack.decStackSize(result.size); }
                    return result;
                }
            }
            return null;
        }
        @Override public IStorageChannel<Stack> getChannel() { return CHANNEL; }
        @Override public AccessRestriction getAccess() { return AccessRestriction.READ_WRITE; }
        @Override public boolean isPrioritized(final Stack input) { return false; }
        @Override public boolean canAccept(final Stack input) { return true; }
        @Override public int getPriority() { return 0; }
        @Override public int getSlot() { return 0; }
        @Override public boolean validForPass(final int pass) { return true; }
    }

    private static final class Channel implements IStorageChannel<Stack> {
        @Override public IItemList<Stack> createList() { return new StackList(); }
        @Override public Stack createStack(final Object input) { return ((Stack) input).copy(); }
        @Override public Stack readFromPacket(final ByteBuf input) { throw new UnsupportedOperationException(); }
        @Override public Stack createFromNBT(final NBTTagCompound input) { throw new UnsupportedOperationException(); }
    }

    private static final class StackList implements IItemList<Stack> {
        private final Map<String, Stack> entries = new LinkedHashMap<>();
        @Override public void add(final Stack stack) {
            if (stack != null) { entries.computeIfAbsent(stack.key, key -> stack.empty()).add(stack); }
        }
        @Override public void addStorage(final Stack stack) { add(stack.copy().setCraftable(false).setCountRequestable(0)); }
        @Override public void addCrafting(final Stack stack) { add(stack.empty().setCraftable(true)); }
        @Override public void addRequestable(final Stack stack) { add(stack.empty().setCountRequestable(stack.requestable)); }
        @Override public Stack findPrecise(final Stack stack) { return entries.get(stack.key); }
        @Override public Collection<Stack> findFuzzy(final Stack stack, final FuzzyMode mode) {
            final Stack found = findPrecise(stack);
            return found == null ? Collections.emptyList() : Collections.singletonList(found);
        }
        @Override public boolean isEmpty() { return !iterator().hasNext(); }
        @Override public Stack getFirstItem() { return iterator().hasNext() ? iterator().next() : null; }
        @Override public int size() { return entries.size(); }
        @Override public Iterator<Stack> iterator() {
            return entries.values().stream().filter(Stack::isMeaningful).iterator();
        }
        @Override public void resetStatus() { entries.values().forEach(Stack::reset); }
    }

    private static final class Stack implements IAEStack<Stack> {
        private final String key;
        private long size;
        private long requestable;
        private boolean craftable;
        private Stack(final String key, final long size) { this.key = key; this.size = size; }
        @Override public void add(final Stack stack) {
            if (stack != null) { size += stack.size; requestable += stack.requestable; craftable |= stack.craftable; }
        }
        @Override public long getStackSize() { return size; }
        @Override public Stack setStackSize(final long count) { size = count; return this; }
        @Override public long getCountRequestable() { return requestable; }
        @Override public Stack setCountRequestable(final long count) { requestable = count; return this; }
        @Override public boolean isCraftable() { return craftable; }
        @Override public Stack setCraftable(final boolean value) { craftable = value; return this; }
        @Override public Stack reset() { size = 0; requestable = 0; craftable = false; return this; }
        @Override public boolean isMeaningful() { return size > 0 || requestable > 0 || craftable; }
        @Override public void incStackSize(final long amount) { size += amount; }
        @Override public void decStackSize(final long amount) { size -= amount; }
        @Override public void incCountRequestable(final long amount) { requestable += amount; }
        @Override public void decCountRequestable(final long amount) { requestable -= amount; }
        @Override public void writeToNBT(final NBTTagCompound tag) { throw new UnsupportedOperationException(); }
        @Override public void writeToPacket(final ByteBuf buffer) { throw new UnsupportedOperationException(); }
        @Override public Stack copy() { return new Stack(key, size).setCountRequestable(requestable).setCraftable(craftable); }
        @Override public Stack empty() { return new Stack(key, 0); }
        @Override public boolean isItem() { return true; }
        @Override public boolean isFluid() { return false; }
        @Override public IStorageChannel<Stack> getChannel() { return CHANNEL; }
        @Override public ItemStack asItemStackRepresentation() { return ItemStack.EMPTY; }
        @Override public boolean equals(final Object other) { return other instanceof Stack && key.equals(((Stack) other).key); }
        @Override public int hashCode() { return key.hashCode(); }
        @Override public boolean fuzzyComparison(final Stack other, final FuzzyMode mode) { return equals(other); }
    }
}
