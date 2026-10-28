package com.extrarawstyle.ae2us.client;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * 稳定分区：按档位把条目分组搬到列表最前，档位小的在前。
 * 同一档内部、以及未分档条目之间的相对顺序都不动。
 * <p>
 * 用 {@link List#sort} 是因为 JDK 保证它是稳定排序——比较结果为 0 的条目保持原有先后。
 * 抽成独立方法是为了让离线自检能直接驱动（界面类本身要游戏环境才能跑）。
 */
public final class ActiveFirstOrder {

    /** 正在合成（绿色底）：排最前。 */
    public static final int RANK_ACTIVE = 0;
    /** 计划合成（只排上队、还没开工，黄色底）：排第二。 */
    public static final int RANK_PENDING = 1;
    /** 其余：保持原顺序排在后面。 */
    public static final int RANK_OTHER = 2;

    private ActiveFirstOrder() {
    }

    public static <T> void apply(final List<T> list, final ToIntFunction<T> rank) {
        list.sort((a, b) -> Integer.compare(rank.applyAsInt(a), rank.applyAsInt(b)));
    }
}
