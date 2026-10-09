package com.extrarawstyle.ae2us;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * 记录"每个方向上一次成功推送的样板"，用来判断这次要推的是不是同一样板。
 * <p>
 * 只用 JDK 类型，方便脱离 Minecraft 直接跑自检（见 src/test/java 下的 SmartBlockingSelfCheck）。
 */
public final class SmartBlocking {

    private final Map<Integer, Object> lastByFace = new HashMap<>();
    private final BiPredicate<Object, Object> same;

    public SmartBlocking(final BiPredicate<Object, Object> same) {
        this.same = same;
    }

    /** 记录某个方向上刚刚成功推送出去的样板。 */
    public void record(final int face, final Object pattern) {
        if (pattern != null) {
            this.lastByFace.put(face, pattern);
        }
    }

    /** 该方向上这次要推的样板，是否就是它上次成功推出去的那个。 */
    public boolean isRepeating(final int face, final Object pattern) {
        final Object last = this.lastByFace.get(face);
        return last != null && pattern != null && this.same.test(last, pattern);
    }
}
