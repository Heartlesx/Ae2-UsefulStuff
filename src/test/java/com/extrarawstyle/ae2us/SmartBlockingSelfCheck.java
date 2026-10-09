package com.extrarawstyle.ae2us;

import java.util.Objects;

/**
 * SmartBlocking 的自检：不依赖 Minecraft，直接用 G:\java8 跑。
 * <pre>
 *   gradlew compileTestJava
 *   java -cp build/classes/java/main;build/classes/java/test com.extrarawstyle.ae2us.SmartBlockingSelfCheck
 * </pre>
 */
public final class SmartBlockingSelfCheck {

    private static int failed;

    private SmartBlockingSelfCheck() {
    }

    public static void main(final String[] args) {
        final SmartBlocking memory = new SmartBlocking((a, b) -> Objects.equals(a, b));

        check("没记录过 → 不放行（原版阻挡照旧）", memory.isRepeating(0, "P"), false);

        memory.record(0, "P");
        check("同一个面、同一样板 → 放行", memory.isRepeating(0, "P"), true);
        check("同一个面、别的样板 → 不放行", memory.isRepeating(0, "Q"), false);

        memory.record(1, "Q");
        check("另一个面各记各的", memory.isRepeating(1, "Q"), true);
        check("另一个面不影响第一个面", memory.isRepeating(0, "P"), true);
        check("第一个面仍然拦住别的样板", memory.isRepeating(0, "Q"), false);

        memory.record(0, null);
        check("样板为空时不覆盖已有记录", memory.isRepeating(0, "P"), true);
        check("样板为空时不放行", memory.isRepeating(0, null), false);

        if (failed > 0) {
            System.out.println(failed + " 项未通过");
            System.exit(1);
        }
        System.out.println("全部通过");
    }

    private static void check(final String what, final boolean actual, final boolean expected) {
        if (actual == expected) {
            System.out.println("OK   " + what);
        } else {
            failed++;
            System.out.println("FAIL " + what + "（期望 " + expected + "，实际 " + actual + "）");
        }
    }
}
