package com.extrarawstyle.ae2us.maintainer;

import java.util.ArrayList;
import java.util.List;

/**
 * 库存维持器在 TheOneProbe 上显示什么。纯逻辑，不碰 TOP 也不碰世界，
 * 方便离线自检直接驱动（TOP 面板本身要游戏环境才画得出来）。
 * <p>
 * 口径：摘要行（状态 + 条数统计）谁都看得见；逐条明细只在扩展模式显示。
 * 明细共享 {@link #DETAIL_MAX} 条上限，<b>异常优先</b>——异常是玩家要处理的事，
 * 比"正在跑"更应该先占位置。
 */
public final class MaintainerProbe {

    /** 明细最多几条，避免 TOP 面板被 32 条规则刷屏。 */
    public static final int DETAIL_MAX = 3;

    private MaintainerProbe() {
    }

    /** 一条要点。kind 决定调用方怎么画（图标 + 文案）。 */
    public static final class Line {
        /** 正在跑：显示"合成中 现货 / 维持数量"。 */
        public static final int KIND_RUNNING = 0;
        /** 异常：显示状态名。 */
        public static final int KIND_PROBLEM = 1;

        public final int kind;
        public final Object target;
        public final long observed;
        public final long threshold;
        public final int status;

        Line(final int kind, final MaintainerRule rule) {
            this.kind = kind;
            this.target = rule.target;
            this.observed = rule.observed;
            this.threshold = rule.threshold;
            this.status = rule.status;
        }
    }

    /** 汇总：启用条数、在跑条数、异常条数、明细行（异常在前）。 */
    public static final class Summary {
        public final int enabled;
        public final int running;
        public final int problems;
        public final List<Line> details;
        /** 因超过 {@link #DETAIL_MAX} 而没有列出的条数。 */
        public final int hidden;

        Summary(final int enabled, final int running, final int problems,
                final List<Line> details, final int hidden) {
            this.enabled = enabled;
            this.running = running;
            this.problems = problems;
            this.details = details;
            this.hidden = hidden;
        }

        public boolean hasAnything() {
            return this.enabled > 0;
        }
    }

    public static Summary summarize(final MaintainerRule[] rules) {
        final List<Line> problems = new ArrayList<>();
        final List<Line> running = new ArrayList<>();
        int enabled = 0;

        for (final MaintainerRule rule : rules) {
            if (rule == null || rule.target == null) { continue; }
            if (rule.enabled) { enabled++; }
            // 异常优先分类：ORDER_UNKNOWN 虽然还占着订单，但它是玩家要处理的异常，
            // 不能混进"正在跑"里当成一切正常。
            if (isProblem(rule.status)) {
                problems.add(new Line(Line.KIND_PROBLEM, rule));
            } else if (MaintainerStatus.holdsOrder(rule.status)) {
                running.add(new Line(Line.KIND_RUNNING, rule));
            }
        }

        // 异常优先占位：异常先放，在跑的补剩下的位置。
        final List<Line> details = new ArrayList<>();
        for (final Line line : problems) {
            if (details.size() < DETAIL_MAX) { details.add(line); }
        }
        for (final Line line : running) {
            if (details.size() < DETAIL_MAX) { details.add(line); }
        }

        final int total = problems.size() + running.size();
        return new Summary(enabled, running.size(), problems.size(), details, total - details.size());
    }

    /** 玩家需要处理的状态。库存充足、排队中这类正常状态不算异常。 */
    public static boolean isProblem(final int status) {
        return status == MaintainerStatus.NO_PATTERN
                || status == MaintainerStatus.SUBMIT_FAILED
                || status == MaintainerStatus.ORDER_UNKNOWN
                || status == MaintainerStatus.NO_DISCRETIZER
                || status == MaintainerStatus.INCOMPLETE
                || status == MaintainerStatus.OFFLINE;
    }
}
