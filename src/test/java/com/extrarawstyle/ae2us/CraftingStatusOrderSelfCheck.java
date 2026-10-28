package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.client.ActiveFirstOrder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * 合成情况界面「正在合成排最前」的离线自检。
 * <p>
 * 排序本身是纯逻辑（{@link ActiveFirstOrder}），直接驱动验证；
 * 另外确认 mixin 注册在配置里——漏注册等于功能静默失效。
 */
public final class CraftingStatusOrderSelfCheck {

    private static final String MIXIN = "client.GuiCraftingCPUActiveFirstMixin";

    public static void main(final String[] args) throws Exception {
        checkStablePartition();
        checkPartialRanks();
        checkAllActiveOrNone();
        checkEmptyList();
        checkRegistered();

        System.out.println("Crafting status order checks passed (green then yellow partition, partial ranks, "
                + "no-op cases, mixin registration)");
    }

    /** 绿 → 黄 → 其余三档，各档内部相对顺序都不动。 */
    private static void checkStablePartition() {
        final List<String> list = new ArrayList<>(Arrays.asList("a", "B", "c", "d", "E", "f", "G"));
        // B/E/G = 绿（正在合成），d = 黄（计划合成），其余无档
        final ToIntFunction<String> rank = s -> {
            if ("BEG".contains(s)) { return ActiveFirstOrder.RANK_ACTIVE; }
            if ("d".equals(s)) { return ActiveFirstOrder.RANK_PENDING; }
            return ActiveFirstOrder.RANK_OTHER;
        };

        ActiveFirstOrder.apply(list, rank);

        check(list.equals(Arrays.asList("B", "E", "G", "d", "a", "c", "f")),
                "绿最前、黄第二、各档内部顺序不变（实际 " + list + "）");
    }

    /** 三档里缺档也不能出错。 */
    private static void checkPartialRanks() {
        final List<String> noPending = new ArrayList<>(Arrays.asList("a", "B", "c", "D"));
        ActiveFirstOrder.apply(noPending, s -> "BD".contains(s)
                ? ActiveFirstOrder.RANK_ACTIVE : ActiveFirstOrder.RANK_OTHER);
        check(noPending.equals(Arrays.asList("B", "D", "a", "c")),
                "没有黄色档时绿仍然排最前（实际 " + noPending + "）");

        final List<String> onlyPending = new ArrayList<>(Arrays.asList("a", "b", "c"));
        ActiveFirstOrder.apply(onlyPending, s -> "b".equals(s)
                ? ActiveFirstOrder.RANK_PENDING : ActiveFirstOrder.RANK_OTHER);
        check(onlyPending.equals(Arrays.asList("b", "a", "c")),
                "只有黄色档时黄排最前（实际 " + onlyPending + "）");
    }

    /** 全命中、全不命中都不能改动顺序。 */
    private static void checkAllActiveOrNone() {
        final List<String> all = new ArrayList<>(Arrays.asList("A", "B", "C"));
        ActiveFirstOrder.apply(all, s -> ActiveFirstOrder.RANK_ACTIVE);
        check(all.equals(Arrays.asList("A", "B", "C")), "全部同档时顺序不变（实际 " + all + "）");

        final List<String> none = new ArrayList<>(Arrays.asList("a", "b", "c"));
        ActiveFirstOrder.apply(none, s -> ActiveFirstOrder.RANK_OTHER);
        check(none.equals(Arrays.asList("a", "b", "c")), "全部无档时顺序不变（实际 " + none + "）");
    }

    private static void checkEmptyList() {
        final List<String> list = new ArrayList<>();
        ActiveFirstOrder.apply(list, s -> ActiveFirstOrder.RANK_ACTIVE);
        check(list.isEmpty(), "空列表不报错");

        final List<String> single = new ArrayList<>(Collections.singletonList("x"));
        ActiveFirstOrder.apply(single, s -> ActiveFirstOrder.RANK_ACTIVE);
        check(single.equals(Collections.singletonList("x")), "单元素列表不报错");
    }

    private static void checkRegistered() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                CraftingStatusOrderSelfCheck.class.getClassLoader().getResourceAsStream("mixins.ae2us.json"),
                StandardCharsets.UTF_8)) {
            final JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            check(listed(json, "client", MIXIN), MIXIN + " 已注册进 mixins.ae2us.json");
        }
    }

    private static boolean listed(final JsonObject json, final String section, final String name) {
        final JsonArray array = json.getAsJsonArray(section);
        if (array == null) {
            return false;
        }
        for (final JsonElement element : array) {
            if (name.equals(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static void check(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
