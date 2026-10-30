package com.extrarawstyle.ae2us.maintainer;

/**
 * 缺料时的批量降级：算出「按现有材料最多还能做多少个」。
 *
 * 前提事实（对着 AE2UEL 源码核过，别推翻）：
 * - AE 没有「最多能做几个」的接口，只有「够」和「不够」两种结局，只能换个数重算。
 * - 算失败的那份任务里没有可用数据：{@code CraftingJob} 只在请求来自玩家时才重算一遍并填计划，
 *   机器发起的请求拿不到原料清单。所以估算用的成本必须来自一次**算成功**的任务。
 * - AE 在算单前会把目标自身的库存清零（{@code craftingInventory.ignore(output)}），
 *   也就是每次都要从头做满请求量，不能按「先把现货吃掉、只补差额」去推算成本。
 *
 * 于是做法是：先按整批算；缺料就探 1 个——这一次算成功的计划里，
 * 每样原料的用量正好是「做一个的成本」，据此反推上限，再按上限验证一次。
 *
 * 纯算术，不碰世界，行为自检直接驱动。
 */
public final class MaintainerEstimate {
    /** 一次降级最多算几回：整批、探 1 个、按上限试、超估后减半。 */
    public static final int MAX_ATTEMPTS = 4;

    private MaintainerEstimate() { }

    /**
     * 按每单位的原料成本，算出现有库存能支持多少个。
     *
     * @param unitCost 做 1 个所需的每样原料数量，与 have 一一对应
     * @param have     同一下标的网络库存
     * @param cap      上限（不会算出比它更大的数）
     * @return 能做的个数；0 表示一个也做不出来
     */
    public static long maxCraftable(final long[] unitCost, final long[] have, final long cap) {
        // 一项可判定的成本都没有：无从估算，宁可报缺料也不乱下小单。
        if (cap <= 0L || unitCost == null || unitCost.length == 0) { return 0L; }
        long max = cap;
        for (int i = 0; i < unitCost.length; i++) {
            // 不消耗的原料（成本为 0）不构成限制。
            if (unitCost[i] <= 0L) { continue; }
            if (i >= have.length) { return 0L; }
            final long allowed = have[i] / unitCost[i];
            if (allowed < max) { max = allowed; }
            if (max <= 0L) { return 0L; }
        }
        return max;
    }

    /** 超估（按上限试还算不出来）之后的退路：减半再试，至少减 1，保证能收敛。 */
    public static long halve(final long amount) {
        return amount <= 1L ? 0L : Math.max(1L, amount / 2L);
    }
}
