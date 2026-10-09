package com.extrarawstyle.ae2us;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 空白样板自动补充的自检：不依赖 Minecraft，直接用 G:\java8 跑。
 * <pre>
 *   gradlew compileTestJava
 *   java -cp build/classes/java/main;build/classes/java/test com.extrarawstyle.ae2us.PatternRefillSelfCheck
 * </pre>
 */
public final class PatternRefillSelfCheck {

    private static int failed;

    private PatternRefillSelfCheck() {
    }

    public static void main(final String[] args) {
        check("空槽差 64 个", PatternRefill.missing(0), 64);
        check("63 个时差 1 个", PatternRefill.missing(63), 1);
        check("64 个时不补", PatternRefill.missing(64), 0);
        check("超量时不补", PatternRefill.missing(100), 0);

        check("没有标签视为开", PatternRefill.read(null), true);
        check("空标签视为开", PatternRefill.read(new NBTTagCompound()), true);

        final NBTTagCompound tag = new NBTTagCompound();
        PatternRefill.write(tag, false);
        check("写入关后读到关", PatternRefill.read(tag), false);
        PatternRefill.write(tag, true);
        check("写入开后读到开", PatternRefill.read(tag), true);

        final NBTTagCompound foreign = new NBTTagCompound();
        foreign.setBoolean("isCraftingMode", false);
        check("只有别的键时仍视为开", PatternRefill.read(foreign), true);

        if (failed > 0) {
            System.out.println(failed + " 项未通过");
            System.exit(1);
        }
        System.out.println("全部通过");
    }

    private static void check(final String what, final int actual, final int expected) {
        if (actual == expected) {
            System.out.println("OK   " + what);
        } else {
            failed++;
            System.out.println("FAIL " + what + "（期望 " + expected + "，实际 " + actual + "）");
        }
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
