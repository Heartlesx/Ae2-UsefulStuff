package com.extrarawstyle.ae2us;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.MEInventoryHandler;
import appeng.me.storage.MEPassThrough;
import appeng.util.inv.ItemListIgnoreCrafting;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class FilteredSubnetScan {
    private static final ThreadLocal<Map<IStorageChannel<?>, Scan<?>>> SCANS = new ThreadLocal<>();
    private static final ThreadLocal<Deque<Filter<?>>> FILTERS = new ThreadLocal<>();

    private FilteredSubnetScan() {
    }

    public static <T extends IAEStack<T>> IItemList<T> filtered(final IMEInventoryHandler<T> internal,
            final IItemList<T> out, final Predicate<T> filter) {
        Deque<Filter<?>> filters = FILTERS.get();
        if (filters == null) {
            filters = new ArrayDeque<>();
            FILTERS.set(filters);
        }
        filters.push(new Filter<>(internal.getChannel(), filter));
        try {
            return internal.getAvailableItems(out);
        } finally {
            filters.pop();
            if (filters.isEmpty()) { FILTERS.remove(); }
        }
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    public static <T extends IAEStack<T>> IItemList<T> scan(final Object network,
            final IStorageChannel<T> channel, final Deque<Object> depth, final IItemList<T> out,
            final Iterable<List<IMEInventoryHandler<T>>>... groups) {
        if (depth.contains(network)) {
            return out;
        }
        Map<IStorageChannel<?>, Scan<?>> scans = SCANS.get();
        if (scans == null) {
            scans = new IdentityHashMap<>();
            SCANS.set(scans);
        }
        Scan<T> scan = (Scan<T>) scans.get(channel);
        final boolean root = scan == null;
        if (root) {
            scan = new Scan<>(channel);
            scans.put(channel, scan);
        }
        final Frame<T> caller = scan.frame;
        final boolean oldIgnoreCrafting = scan.ignoreCrafting;
        scan.ignoreCrafting |= out instanceof ItemListIgnoreCrafting;
        depth.push(network);
        try {
            final IItemList<T> result = channel.createList();
            for (final Iterable<List<IMEInventoryHandler<T>>> group : groups) {
                for (final List<IMEInventoryHandler<T>> handlers : group) {
                    for (final IMEInventoryHandler<T> handler : handlers) {
                        final Frame<T> frame = new Frame<>(channel.createList());
                        scan.frame = frame;
                        final IItemList<T> visible = handler.getAvailableItems(channel.createList());
                        for (final T stack : visible) {
                            if (!allowed(channel, stack)) { continue; }
                            final T value = stack.copy();
                            if (scan.ignoreCrafting) { value.setCraftable(false); }
                            final T nested = frame.nested.findPrecise(value);
                            final T local = value.copy();
                            if (nested != null) {
                                local.setStackSize(Math.max(0, value.getStackSize() - nested.getStackSize()));
                                local.setCountRequestable(Math.max(0, value.getCountRequestable() - nested.getCountRequestable()));
                                local.setCraftable(value.isCraftable() && !nested.isCraftable());
                            }
                            final T delta = scan.take(source(handler), local);
                            // Nested scans already deduplicated their sources; only local content needs accounting.
                            value.setStackSize(value.getStackSize() - local.getStackSize() + delta.getStackSize());
                            value.setCountRequestable(value.getCountRequestable() - local.getCountRequestable()
                                    + delta.getCountRequestable());
                            value.setCraftable((nested != null && nested.isCraftable() && value.isCraftable())
                                    || delta.isCraftable());
                            if (value.isMeaningful()) { result.add(value); }
                        }
                    }
                }
            }
            for (final T stack : result) {
                out.add(stack);
                if (caller != null) { caller.nested.add(stack); }
            }
            return out;
        } finally {
            scan.frame = caller;
            scan.ignoreCrafting = oldIgnoreCrafting;
            depth.pop();
            if (root) {
                scans.remove(channel);
                if (scans.isEmpty()) { SCANS.remove(); }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> boolean allowed(final IStorageChannel<T> channel, final T stack) {
        final Deque<Filter<?>> filters = FILTERS.get();
        if (filters != null) {
            for (final Filter<?> filter : filters) {
                if (filter.channel == channel && !((Predicate<T>) filter.predicate).test(stack)) { return false; }
            }
        }
        return true;
    }

    private static Object source(final IMEInventory<?> inventory) {
        IMEInventory<?> current = inventory;
        while (current instanceof MEInventoryHandler || current instanceof MEPassThrough) {
            final IMEInventory<?> internal = current instanceof MEInventoryHandler
                    ? ((MEInventoryHandler<?>) current).getInternal() : ((MEPassThrough<?>) current).getInternal();
            if (internal == null || internal == current) { break; }
            current = internal;
        }
        return current;
    }

    private static final class Scan<T extends IAEStack<T>> {
        private final IStorageChannel<T> channel;
        private final Map<Object, IItemList<T>> sources = new IdentityHashMap<>();
        private Frame<T> frame;
        private boolean ignoreCrafting;
        private Scan(final IStorageChannel<T> channel) { this.channel = channel; }

        private T take(final Object source, final T stack) {
            final T delta = stack.copy();
            if (!stack.isMeaningful()) { return delta; }
            final IItemList<T> seen = sources.computeIfAbsent(source, key -> channel.createList());
            final T previous = seen.findPrecise(stack);
            if (previous == null) {
                seen.add(stack);
            } else {
                delta.setStackSize(Math.max(0, stack.getStackSize() - previous.getStackSize()));
                delta.setCountRequestable(Math.max(0, stack.getCountRequestable() - previous.getCountRequestable()));
                delta.setCraftable(stack.isCraftable() && !previous.isCraftable());
                previous.setStackSize(Math.max(previous.getStackSize(), stack.getStackSize()));
                previous.setCountRequestable(Math.max(previous.getCountRequestable(), stack.getCountRequestable()));
                previous.setCraftable(previous.isCraftable() || stack.isCraftable());
            }
            return delta;
        }
    }

    private static final class Frame<T extends IAEStack<T>> {
        private final IItemList<T> nested;
        private Frame(final IItemList<T> nested) { this.nested = nested; }
    }

    private static final class Filter<T extends IAEStack<T>> {
        private final IStorageChannel<T> channel;
        private final Predicate<T> predicate;
        private Filter(final IStorageChannel<T> channel, final Predicate<T> predicate) {
            this.channel = channel;
            this.predicate = predicate;
        }
    }
}
