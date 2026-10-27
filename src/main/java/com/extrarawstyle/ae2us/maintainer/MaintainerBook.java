package com.extrarawstyle.ae2us.maintainer;

import appeng.api.storage.data.IAEStack;

/**
 * 32 条规则与配置操作。纯数据逻辑，不直接碰世界：主线程与行为自检共用。
 * 目标身份按 AE 的堆比较语义判定（物品为 ID+元数据+NBT，流体为注册名+NBT），不含数量。
 *
 * 两个修订号分工：{@link #revision()} 任何变化（含界面库存刷新）都会涨，用于 GUI 同步；
 * {@link #configRevision()} 只在配置本身变化时涨，用于判定已算好的合成计划是否过期。
 */
public final class MaintainerBook {
    public static final int CAPACITY = 32;
    public static final int ROWS_PER_PAGE = 8;
    public static final int PAGES = CAPACITY / ROWS_PER_PAGE;

    private final MaintainerRule[] rules = new MaintainerRule[CAPACITY];
    private long revision = 1;
    private long configRevision = 1;

    public MaintainerBook() {
        for (int i = 0; i < CAPACITY; i++) { this.rules[i] = new MaintainerRule(); }
    }

    public MaintainerRule rule(final int index) {
        return index >= 0 && index < CAPACITY ? this.rules[index] : null;
    }

    public MaintainerRule[] rules() {
        return this.rules;
    }

    public long revision() {
        return this.revision;
    }

    public long configRevision() {
        return this.configRevision;
    }

    /** 界面数据（库存、状态）变化：只涨显示修订号。 */
    public void bump() {
        this.revision++;
    }

    /** 配置变化：两个修订号一起涨，已算好的计划随之作废。 */
    public void bumpConfig() {
        this.revision++;
        this.configRevision++;
    }

    /** 已有的同类型同目标条目下标；没有返回 -1。ignoring 为要排除的自身下标。 */
    public int duplicateOf(final IAEStack<?> target, final int mode, final int ignoring) {
        if (target == null) { return -1; }
        for (int i = 0; i < CAPACITY; i++) {
            if (i == ignoring) { continue; }
            final MaintainerRule rule = this.rules[i];
            if (rule.target == null || rule.mode != mode) { continue; }
            if (rule.target.equals(target)) { return i; }
        }
        return -1;
    }

    /**
     * 设置目标；target 为 null 表示清空。
     * 更换目标会解除当前订单关联（旧订单在 CPU 上继续），保留启用状态与已填数值；
     * 清空后重建按新行处理：默认停用、数值留空，避免旧数字套到新目标上。
     *
     * @return false 表示与另一条规则重复，本次未改动。
     */
    public boolean setTarget(final int index, final IAEStack<?> target, final int mode) {
        final MaintainerRule rule = this.rules[index];
        if (target == null) {
            final boolean changed = rule.target != null || rule.craftId != null;
            rule.target = null;
            rule.mode = MaintainerRule.MODE_ITEM;
            rule.enabled = false;
            rule.threshold = 0;
            rule.batch = 0;
            rule.clearOrder();
            rule.observed = 0;
            rule.observedValid = false;
            rule.status = MaintainerStatus.EMPTY;
            if (changed) { this.bumpConfig(); }
            return true;
        }
        if (this.duplicateOf(target, mode, index) >= 0) { return false; }
        final boolean same = rule.target != null && rule.mode == mode && rule.target.equals(target);
        if (same) { return true; }
        rule.target = target.copy().setStackSize(1).setCraftable(false).setCountRequestable(0);
        rule.mode = mode;
        // 换目标：旧订单继续在 CPU 上跑，本条目只解除关联，不调用 cancel。
        rule.clearOrder();
        rule.observed = 0;
        rule.observedValid = false;
        rule.status = rule.enabled ? MaintainerStatus.QUEUED : MaintainerStatus.DISABLED;
        this.bumpConfig();
        return true;
    }

    /** 改数字不改订单：新阈值用于后续判断，新批量用于下一笔订单。 */
    public void setThreshold(final int index, final long value) {
        final MaintainerRule rule = this.rules[index];
        if (value <= 0 || rule.threshold == value) { return; }
        rule.threshold = value;
        this.bumpConfig();
    }

    public void setBatch(final int index, final long value) {
        final MaintainerRule rule = this.rules[index];
        if (value <= 0 || rule.batch == value) { return; }
        rule.batch = value;
        this.bumpConfig();
    }

    /** 停用只阻止后续提交，已提交的订单继续。 */
    public void setEnabled(final int index, final boolean value) {
        final MaintainerRule rule = this.rules[index];
        if (rule.enabled == value) { return; }
        rule.enabled = value;
        this.bumpConfig();
    }

    /** 记录界面展示用的库存与状态；这类变化不影响配置修订号。 */
    public void observe(final int index, final int status, final long observed, final boolean observedValid) {
        final MaintainerRule rule = this.rules[index];
        if (rule.status == status && rule.observed == observed && rule.observedValid == observedValid) { return; }
        rule.status = status;
        rule.observed = observed;
        rule.observedValid = observedValid;
        this.bump();
    }

    /** 装载存档/掉落物期间不逐条递增修订号，装完统一调一次。 */
    public void markChanged() {
        this.bumpConfig();
    }

    public int configuredCount() {
        int total = 0;
        for (final MaintainerRule rule : this.rules) {
            if (rule.target != null) { total++; }
        }
        return total;
    }
}
