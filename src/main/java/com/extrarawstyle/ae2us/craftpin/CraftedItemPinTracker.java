package com.extrarawstyle.ae2us.craftpin;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 服务端记录"谁最近在合成什么"，用来决定 ME 终端里哪些物品置顶。
 * <p>
 * 口径：正在合成的产出 + 刚做完还留着一段时间的产出。这里**不监听"合成完成"事件**——
 * 每次轮询把当前忙碌 CPU 的产出刷新进记录并盖时间戳，任务做完后该 CPU 不再忙碌、
 * 记录停止刷新，靠 {@link #KEEP_MILLIS} 自然过期。好处是玩家关着终端等合成、
 * 过一会儿再打开仍然能看到产出置顶（这正是"下单制作完毕之后置顶"的场景）。
 * <p>
 * 纯数据逻辑，不碰网络也不碰 AE2 的类，方便自检直接构造。
 */
public final class CraftedItemPinTracker {

    /** 合成完成后继续置顶的时长。 */
    public static final long KEEP_MILLIS = 5L * 60L * 1000L;

    /** 同时最多置顶多少个物品；超出时最早被置顶的那个会被顶替掉。 */
    public static final int MAX_ENTRIES = 20;

    private static final Map<UUID, Record> RECORDS = new HashMap<>();

    private CraftedItemPinTracker() {
    }

    /** 服务端每次轮询调用：刷新记录，并给出该玩家当前应置顶的清单。 */
    public static Snapshot observe(final UUID player, final List<ItemStack> busyOutputs, final long now) {
        Record record = RECORDS.get(player);
        if (record == null) {
            record = new Record();
            RECORDS.put(player, record);
        }
        final Snapshot snapshot = record.observe(busyOutputs, now);
        if (record.isEmpty()) {
            RECORDS.remove(player);
        }
        return snapshot;
    }

    /** 玩家退出时清掉记录。 */
    public static void forget(final UUID player) {
        RECORDS.remove(player);
    }

    /**
     * 请求清空该玩家的置顶（终端里的清除按钮）。
     * 只清已完成的——下一次轮询会把仍在忙碌的产出立刻记回来，所以"正在合成的"不会消失。
     */
    public static void requestClear(final UUID player) {
        Record record = RECORDS.get(player);
        if (record == null) {
            record = new Record();
            RECORDS.put(player, record);
        }
        record.requestClear();
    }

    /** 一个玩家的记录。自检直接 new 出来测，不碰上面的静态表。 */
    public static final class Record {

        private final Map<ItemKey, Seen> seen = new LinkedHashMap<>();
        private final Set<ItemKey> lastSent = new HashSet<>();
        private boolean everSent;
        private boolean clearRequested;

        /** 下一次 observe 时把记录里"已完成"的清掉。 */
        public void requestClear() {
            this.clearRequested = true;
        }

        /**
         * 用本次观察到的忙碌产出刷新记录，返回该置顶的清单。
         * {@code changed} 表示与上次结果不同——一样就不必重复发包。
         */
        public Snapshot observe(final List<ItemStack> busyOutputs, final long now) {
            if (this.clearRequested) {
                // 只清已完成的：先把记录整个丢掉，紧接着把本次仍在忙碌的重新记回来。
                // 两步在同一次 observe 里完成，所以"正在合成的"不会先消失再出现（不会闪）。
                this.clearRequested = false;
                this.seen.clear();
            }
            for (final ItemStack stack : busyOutputs) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                this.seen.put(new ItemKey(stack), new Seen(stack.copy(), now));
            }
            evictExpired(now);
            trimToLimit();

            final List<ItemStack> pinned = new ArrayList<>(this.seen.size());
            final Set<ItemKey> keys = new HashSet<>();
            for (final Map.Entry<ItemKey, Seen> entry : this.seen.entrySet()) {
                pinned.add(entry.getValue().stack);
                keys.add(entry.getKey());
            }

            final boolean changed = !this.everSent || !keys.equals(this.lastSent);
            this.everSent = true;
            this.lastSent.clear();
            this.lastSent.addAll(keys);
            return new Snapshot(pinned, changed);
        }

        public boolean isEmpty() {
            return this.seen.isEmpty();
        }

        private void evictExpired(final long now) {
            final Iterator<Map.Entry<ItemKey, Seen>> iterator = this.seen.entrySet().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().getValue().at > KEEP_MILLIS) {
                    iterator.remove();
                }
            }
        }

        /**
         * 超出上限时顶替**最早被置顶**的那个。
         * seen 是 LinkedHashMap：迭代顺序就是插入顺序，而对已有键再 put 不会改变它原来的位置
         * （没开 accessOrder），所以队首正是"最早置顶、且一直没被顶替过"的那个。
         */
        private void trimToLimit() {
            final Iterator<ItemKey> iterator = this.seen.keySet().iterator();
            while (this.seen.size() > MAX_ENTRIES && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    /** 一次轮询的结果：要下发的清单 + 与上次相比有没有变化。 */
    public static final class Snapshot {

        public final List<ItemStack> pinned;
        public final boolean changed;

        Snapshot(final List<ItemStack> pinned, final boolean changed) {
            this.pinned = pinned;
            this.changed = changed;
        }
    }

    private static final class Seen {

        private final ItemStack stack;
        private final long at;

        private Seen(final ItemStack stack, final long at) {
            this.stack = stack;
            this.at = at;
        }
    }
}
