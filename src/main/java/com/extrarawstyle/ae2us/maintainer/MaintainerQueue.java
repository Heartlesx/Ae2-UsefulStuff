package com.extrarawstyle.ae2us.maintainer;

/**
 * 轮询队列：32 条规则共用，游标单向推进，每次只放行一条去计算。
 * 尝试后游标必然前进，所以首条缺料、需要大 CPU 或提交失败都不会堵住后面的条目。
 * 纯逻辑，不碰世界；行为自检直接驱动它验证公平性。
 */
public final class MaintainerQueue {
    /** 缺材料 / 无样板 / 缺流体离散器后的冷却。 */
    public static final long RETRY_NO_PATTERN = 200;
    /** 提交失败后的冷却，比缺料短一些。 */
    public static final long RETRY_SUBMIT_FAILED = 100;
    /** 已算好的计划最长保留时间；过期后轮到该条时重算。 */
    public static final long PLAN_LIFETIME = 600;

    private final long[] readyAt = new long[MaintainerBook.CAPACITY];
    private int cursor;

    public int cursor() {
        return this.cursor;
    }

    public void reset() {
        this.cursor = 0;
        for (int i = 0; i < this.readyAt.length; i++) { this.readyAt[i] = 0L; }
    }

    public boolean cooling(final int index, final long now) {
        return now < this.readyAt[index];
    }

    public void coolDown(final int index, final long now, final long ticks) {
        this.readyAt[index] = now + ticks;
    }

    public void clearCooldown(final int index) {
        this.readyAt[index] = 0L;
    }

    /**
     * 从当前游标起找下一条可以送去计算的规则，找到即把游标推到它后面。
     * 找不到返回 -1；此时游标不动，下次仍从原处开始扫。
     */
    public int pick(final MaintainerBook book, final long now) {
        for (int step = 0; step < MaintainerBook.CAPACITY; step++) {
            final int index = (this.cursor + step) % MaintainerBook.CAPACITY;
            if (!this.eligible(book.rule(index), index, now)) { continue; }
            this.cursor = (index + 1) % MaintainerBook.CAPACITY;
            return index;
        }
        return -1;
    }

    /** 本条能否开始计算：已配置、已启用、数值合法、无在管订单、不在冷却。 */
    public boolean eligible(final MaintainerRule rule, final int index, final long now) {
        if (rule == null || !rule.usable()) { return false; }
        if (rule.craftId != null) { return false; }
        return !this.cooling(index, now);
    }

    /** 纯判定：库存严格低于阈值。等于阈值不下单。 */
    public static boolean needsRefill(final long stored, final long threshold) {
        return stored < threshold;
    }
}
