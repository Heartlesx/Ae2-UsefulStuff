package com.extrarawstyle.ae2us.orebus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public final class OreExportScheduler<T> {
    public static final class Stock<T> {
        public final T item;
        public long count;
        public final int[] rows;

        public Stock(final T item, final long count, final int[] rows) {
            this.item = item;
            this.count = count;
            this.rows = rows;
        }
    }

    public interface Transfer<T> { int send(T item, int limit); }

    private int nextRow;
    private final Map<Integer, Integer> nextItem = new HashMap<>();

    public static int budget(final int speedCards) {
        return new int[]{11, 86, 342, 683, 1024}[Math.max(0, Math.min(4, speedCards))];
    }

    public void reset() {
        this.nextRow = 0;
        this.nextItem.clear();
    }

    public int export(final List<Stock<T>> stock, final OreExportRules.Mode mode,
                      final int budget, final Random random, final Transfer<T> transfer) {
        final Map<Integer, List<Stock<T>>> groups = new TreeMap<>();
        for (final Stock<T> entry : stock) {
            if (entry.count <= 0) { continue; }
            for (final int row : entry.rows) {
                groups.computeIfAbsent(row, ignored -> new ArrayList<>()).add(entry);
            }
        }
        final List<Integer> order = new ArrayList<>(groups.keySet());
        if (mode == OreExportRules.Mode.RANDOM) {
            Collections.shuffle(order, random);
        } else if (mode == OreExportRules.Mode.ROUND_ROBIN && !order.isEmpty()) {
            int start = 0;
            while (start < order.size() && order.get(start) < this.nextRow) { start++; }
            Collections.rotate(order, -(start % order.size()));
        }

        int sent = 0;
        for (final int row : order) {
            final List<Stock<T>> entries = groups.get(row);
            final int start = this.nextItem.getOrDefault(row, 0) % entries.size();
            boolean hasStock = false;
            for (int offset = 0; offset < entries.size() && sent < budget; offset++) {
                final int index = (start + offset) % entries.size();
                final Stock<T> entry = entries.get(index);
                if (entry.count <= 0) { continue; }
                final int moved = transfer.send(entry.item, (int) Math.min(entry.count, budget - sent));
                entry.count -= moved;
                sent += moved;
                if (moved > 0) { this.nextItem.put(row, (index + 1) % entries.size()); }
            }
            for (final Stock<T> entry : entries) { hasStock |= entry.count > 0; }
            this.nextRow = row + 1;
            if (sent >= budget || (mode == OreExportRules.Mode.SEQUENTIAL && hasStock)) { break; }
        }
        return sent;
    }
}
