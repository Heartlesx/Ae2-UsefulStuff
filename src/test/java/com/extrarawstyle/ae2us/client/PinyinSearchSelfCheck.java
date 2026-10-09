package com.extrarawstyle.ae2us.client;

public final class PinyinSearchSelfCheck {

    private PinyinSearchSelfCheck() {
    }

    public static void main(final String[] args) {
        final boolean withJecharacters = args.length > 0 && args[0].equals("with-jecharacters");
        check("direct Chinese", PinyinSearch.matches("MV 输出仓", "输出"), true);
        check("Latin name", PinyinSearch.matches("MV 输出仓", "mv"), true);
        check("pinyin", PinyinSearch.matches("组装机", "zuzhuangji"), withJecharacters);
        check("pinyin initials", PinyinSearch.matches("组装机", "zzj"), withJecharacters);
        check("mixed name pinyin", PinyinSearch.matches("MV 输出仓", "shuchu"), withJecharacters);
        check("unrelated", PinyinSearch.matches("组装机", "shuchucang"), false);
    }

    private static void check(final String name, final boolean actual, final boolean expected) {
        if (actual != expected) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
        System.out.println("OK " + name);
    }
}
