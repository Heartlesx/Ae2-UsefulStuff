package com.extrarawstyle.ae2us.maintainer;

/** 每条规则在界面上显示的状态。数值稳定，直接进网络包。 */
public final class MaintainerStatus {
    /** 未配置目标。 */
    public static final int EMPTY = 0;
    /** 已停用。 */
    public static final int DISABLED = 1;
    /** 网络不可用（无频道、无电或未接入）。 */
    public static final int OFFLINE = 2;
    /** 未填合法的阈值或批量。 */
    public static final int INCOMPLETE = 3;
    /** 库存充足。 */
    public static final int STOCKED = 4;
    /** 已排入轮询，等待计算。 */
    public static final int QUEUED = 5;
    /** 正在异步计算固定批量的合成计划。 */
    public static final int CALCULATING = 6;
    /** 没有样板或缺材料，冷却后重试。 */
    public static final int NO_PATTERN = 7;
    /** 计划已算好，但没有空闲 CPU 或容量不足。 */
    public static final int WAITING_CPU = 8;
    /** 已提交独立订单，CPU 正在合成。 */
    public static final int CRAFTING = 9;
    /** 提交失败，冷却后重试。 */
    public static final int SUBMIT_FAILED = 10;
    /** 订单状态未知：CPU 不可见，保留关联等待。 */
    public static final int ORDER_UNKNOWN = 11;
    /** 流体条目缺少可用的 AE2FC 流体离散器。 */
    public static final int NO_DISCRETIZER = 12;

    public static final int IDLE = EMPTY;

    private MaintainerStatus() { }

    /** 该状态是否代表本条当前占着一笔已提交的订单。 */
    public static boolean holdsOrder(final int status) {
        return status == CRAFTING || status == ORDER_UNKNOWN;
    }

    /**
     * 这一条“本轮尝试的结论”是否应该在库存/配置没变时保留。
     * 缺料、提交失败、等待 CPU 都是实际发生过的事，面板要让玩家看到，
     * 不能每次常规核对都被刷回“排队中”。
     */
    public static boolean isAttemptResult(final int status) {
        return status == NO_PATTERN || status == SUBMIT_FAILED || status == WAITING_CPU;
    }

    /** 状态常量 → 语言键后缀（`ae2us.maintainer.state.*`）。界面与 TOP 共用一份。 */
    public static String key(final int status) {
        switch (status) {
            case DISABLED: return "disabled";
            case OFFLINE: return "offline";
            case INCOMPLETE: return "incomplete";
            case STOCKED: return "stocked";
            case QUEUED: return "queued";
            case CALCULATING: return "calculating";
            case NO_PATTERN: return "no_pattern";
            case WAITING_CPU: return "waiting_cpu";
            case CRAFTING: return "crafting";
            case SUBMIT_FAILED: return "submit_failed";
            case ORDER_UNKNOWN: return "order_unknown";
            case NO_DISCRETIZER: return "no_discretizer";
            default: return "";
        }
    }
}
