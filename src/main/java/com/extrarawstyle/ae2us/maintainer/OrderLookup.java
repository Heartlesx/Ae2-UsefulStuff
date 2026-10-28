package com.extrarawstyle.ae2us.maintainer;

/**
 * 一条规则的订单核对结果，以及「按记录坐标查到的 CPU」该怎么判。
 * <p>
 * 判据只有一条底线：**拿到确凿证据才判结束**。看不到（区块没加载、网络分裂）保持未知，
 * 绝不因为超时重下——那会重复下单。判据抽成纯函数是为了离线自检能直接驱动。
 */
public final class OrderLookup {

    public static final int RUNNING = 0;
    public static final int FINISHED = 1;
    public static final int UNKNOWN = 2;

    public final int state;

    private OrderLookup(final int state) {
        this.state = state;
    }

    public static final OrderLookup RUNNING_ORDER = new OrderLookup(RUNNING);
    public static final OrderLookup FINISHED_ORDER = new OrderLookup(FINISHED);
    public static final OrderLookup UNKNOWN_ORDER = new OrderLookup(UNKNOWN);

    /**
     * 按记录坐标查到的那台 CPU 该怎么判。
     *
     * @param craftingTile  那个位置上还是不是合成单元（被拆掉就不是）
     * @param linkMatches   当前 link 的 craftID 是否就是本条订单的
     * @param hasLink       CPU 上是否有任何 link
     * @param busy          CPU 是否在忙
     */
    public static OrderLookup forRememberedCpu(final boolean craftingTile, final boolean linkMatches,
            final boolean hasLink, final boolean busy) {
        if (!craftingTile) {
            // CPU 已被拆掉：它不可能还在跑这一单，属于确凿证据。
            return FINISHED_ORDER;
        }
        if (linkMatches) {
            return busy ? RUNNING_ORDER : FINISHED_ORDER;
        }
        if (hasLink) {
            // 这台 CPU 已经有别的 craftID：原单不再占用它，判定结束（不区分完成与取消）。
            return FINISHED_ORDER;
        }
        // 没有 link 且不忙：AE2 取消订单时会把 myLastLink 直接置 null，所以这就是「被取消了」。
        // 若仍然忙，说明只是看不到 link，保持未知。
        return busy ? UNKNOWN_ORDER : FINISHED_ORDER;
    }

    /**
     * 本网络与记录坐标都找不到这台 CPU 时该怎么判。
     * <p>
     * 有些 CPU 完成后会把自己从网络里摘掉——它本来就不是方块集群，例如虚拟合成处理器
     * （ECO 的 vCPU 是内存里 new 出来的 CraftingCPUCluster，tiles 为空、getCore() 返回 null，
     * 所以既攒不到坐标线索，也会在完成时 destroy）。这类 CPU 一旦消失就只能靠「之前见过它」判断：
     * 既然确实见过这一单在跑，现在它没了，就是跑完了。
     *
     * @param seenRunning 本次订单期间是否曾经在 CPU 列表里见过它
     */
    public static OrderLookup forMissingCpu(final boolean seenRunning) {
        return seenRunning ? FINISHED_ORDER : UNKNOWN_ORDER;
    }
}
