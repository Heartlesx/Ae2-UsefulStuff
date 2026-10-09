package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.client.CraftedItemPinClient;
import com.extrarawstyle.ae2us.craftpin.CraftedItemPinTracker;
import com.extrarawstyle.ae2us.craftpin.ItemKey;
import com.extrarawstyle.ae2us.craftpin.PinOrder;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 合成产出置顶的离线自检。
 * <p>
 * "记录 / 过期 / 按玩家隔离"这套纯逻辑没法在无游戏环境里实机跑，这里全验一遍；
 * 另外确认三个 mixin 确实注册进了 mixins.ae2us.json（漏注册 = 功能静默失效，
 * 而且 FeatureMixinPlugin 里那些 case 根本不会被调用）。
 */
public final class CraftedItemPinSelfCheck {

    private static final String CONTAINER_MIXIN = "ContainerMEMonitorablePinMixin";
    private static final String REPO_MIXIN = "client.ItemRepoPinMixin";
    private static final String GUI_MIXIN = "client.AEBaseGuiPinMixin";

    public static void main(final String[] args) throws Exception {
        Bootstrap.register();
        checkItemKey();
        checkRecordLifecycle();
        checkEvictionOrder();
        checkClearKeepsBusy();
        checkPinOrder();
        checkReapplyTrigger();
        checkPerPlayerIsolation();
        checkRegistered();

        System.out.println("Crafted item pin checks passed (key equality, ttl expiry, eviction order, clear keeps busy, "
                + "pin order under search filter, reapply trigger, per-player isolation, mixin registration)");
    }

    /** 超出上限时顶替的必须是"最早置顶"的那个，而不是随便一个。 */
    private static void checkEvictionOrder() {
        final CraftedItemPinTracker.Record record = new CraftedItemPinTracker.Record();
        final long start = 1_000_000L;
        final List<ItemStack> ordered = new ArrayList<>();

        // 一个一个地置顶，让插入顺序明确（同一次 observe 里的多个物品顺序不好区分）。
        for (int i = 0; i < CraftedItemPinTracker.MAX_ENTRIES; i++) {
            final ItemStack stack = new ItemStack(Items.STICK, 1, i);
            ordered.add(stack);
            record.observe(Collections.singletonList(stack), start + i);
        }

        final ItemStack newcomer = new ItemStack(Items.STICK, 1, CraftedItemPinTracker.MAX_ENTRIES);
        final CraftedItemPinTracker.Snapshot snapshot = record.observe(Collections.singletonList(newcomer), start + 100L);
        check(snapshot.pinned.size() == CraftedItemPinTracker.MAX_ENTRIES,
                "超出上限后仍然只置顶 " + CraftedItemPinTracker.MAX_ENTRIES + " 个（实际 " + snapshot.pinned.size() + "）");
        check(contains(snapshot.pinned, newcomer), "新来的物品被置顶");
        check(!contains(snapshot.pinned, ordered.get(0)), "最早置顶的那个被顶替掉");
        check(contains(snapshot.pinned, ordered.get(1)), "第二早的仍然在（只顶替一个）");
    }

    /** 清除按钮：只清已完成的，正在合成的保留（服务端在同一次轮询里把忙碌的记回来，所以不会闪）。 */
    private static void checkClearKeepsBusy() {
        final CraftedItemPinTracker.Record record = new CraftedItemPinTracker.Record();
        final long start = 1_000_000L;
        final ItemStack busy = new ItemStack(Items.STICK, 1, 0);
        final ItemStack done = new ItemStack(Items.STICK, 1, 1);

        record.observe(Collections.singletonList(busy), start);
        record.observe(Collections.singletonList(done), start + 1000L);
        check(record.observe(Collections.singletonList(busy), start + 2000L).pinned.size() == 2, "清除前两个都在置顶里");

        record.requestClear();
        final CraftedItemPinTracker.Snapshot after = record.observe(Collections.singletonList(busy), start + 3000L);
        check(after.pinned.size() == 1, "清除后只剩正在合成的那个（实际 " + after.pinned.size() + "）");
        check(contains(after.pinned, busy), "清除后正在合成的仍然置顶");
        check(!contains(after.pinned, done), "清除后已完成的被清掉");
    }

    /** 搜索过滤时置顶不能"消失"：被过滤掉的置顶条目要从完整清单里捞回最前。 */
    private static void checkPinOrder() {
        final IAEItemStack first = AEItemStack.fromItemStack(new ItemStack(Items.STICK, 1, 0));
        final IAEItemStack pinnedItem = AEItemStack.fromItemStack(new ItemStack(Items.STICK, 1, 1));
        final IAEItemStack last = AEItemStack.fromItemStack(new ItemStack(Items.STICK, 1, 2));
        final List<IAEItemStack> all = Arrays.asList(first, pinnedItem, last);

        final Set<ItemKey> keys = new HashSet<>();
        keys.add(PinOrder.keyOf(pinnedItem));

        // 搜索把置顶的那个过滤掉了：view 里只剩另外两个
        final List<IAEItemStack> filtered = new ArrayList<>(Arrays.asList(first, last));
        final List<IAEItemStack> ordered = PinOrder.apply(filtered, all, keys);
        check(ordered != null && ordered.size() == 3,
                "被搜索过滤掉的置顶条目会被补回列表（实际 " + (ordered == null ? "null" : ordered.size()) + "）");
        check(ordered.get(0) == pinnedItem, "补回来的置顶条目排在最前");
        check(ordered.get(1) == first && ordered.get(2) == last, "其余条目保持原顺序");

        // 置顶条目本来就在 view 里：只前移，不重复
        final List<IAEItemStack> unfiltered = new ArrayList<>(Arrays.asList(first, pinnedItem, last));
        final List<IAEItemStack> moved = PinOrder.apply(unfiltered, all, keys);
        check(moved != null && moved.size() == 3, "置顶条目已在列表里时不会重复添加");
        check(moved.get(0) == pinnedItem, "已在列表里的置顶条目被前移");

        check(PinOrder.apply(unfiltered, all, Collections.<ItemKey>emptySet()) == null, "没有置顶条目时不改列表");
    }

    /** 重排触发条件：列表被重建过就得重新应用，哪怕没有新数据（1.3.5-dev3 的 bug 就在这里）。 */
    private static void checkReapplyTrigger() {
        final Object beforeArrival = new Object();
        final Object afterArrival = new Object();

        check(CraftedItemPinClient.needsReapply(beforeArrival, null, true), "刚拿到置顶数据要重排一次");
        check(!CraftedItemPinClient.needsReapply(beforeArrival, beforeArrival, false), "同一份列表、数据没变就不重复做");
        check(CraftedItemPinClient.needsReapply(afterArrival, beforeArrival, false),
                "物品到货后 AE2 重建了列表：没有新数据也必须重新重排");
    }

    private static void checkItemKey() {
        final ItemStack stick = new ItemStack(Items.STICK);
        check(new ItemKey(stick).equals(new ItemKey(new ItemStack(Items.STICK))), "同物品同元数据的键相等");
        check(new ItemKey(stick).hashCode() == new ItemKey(new ItemStack(Items.STICK)).hashCode(), "同键哈希一致");

        check(!new ItemKey(stick).equals(new ItemKey(new ItemStack(Items.STICK, 1, 1))), "元数据不同不算同一个物品");
        check(!new ItemKey(stick).equals("not a key"), "不同类型不相等");

        final ItemStack tagged = new ItemStack(Items.STICK);
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("ae2us", 1);
        tagged.setTagCompound(tag);
        check(!new ItemKey(stick).equals(new ItemKey(tagged)), "NBT 不同不算同一个物品");
        check(new ItemKey(tagged).equals(new ItemKey(tagged.copy())), "NBT 相同的副本相等");
    }

    private static void checkRecordLifecycle() {
        final CraftedItemPinTracker.Record record = new CraftedItemPinTracker.Record();
        final ItemStack output = new ItemStack(Items.STICK);
        final long start = 1_000_000L;

        CraftedItemPinTracker.Snapshot snapshot = record.observe(Collections.singletonList(output), start);
        check(snapshot.changed, "首次观察到忙碌产出要下发");
        check(contains(snapshot.pinned, output), "忙碌产出被置顶");

        snapshot = record.observe(Collections.singletonList(output), start + 1000L);
        check(!snapshot.changed, "产出没变就不重复下发");
        check(contains(snapshot.pinned, output), "产出没变仍然置顶");

        snapshot = record.observe(Collections.<ItemStack>emptyList(), start + 2000L);
        check(!snapshot.changed, "合成结束后集合没变，不必再发");
        check(contains(snapshot.pinned, output), "合成结束后仍在保留期内，继续置顶");

        // 注意：上一次刷新发生在 start + 1000，过期点以那次为准，所以要往后多留一点。
        snapshot = record.observe(Collections.<ItemStack>emptyList(),
                start + 10_000L + CraftedItemPinTracker.KEEP_MILLIS);
        check(snapshot.changed, "超过保留期要下发一次空集合");
        check(snapshot.pinned.isEmpty(), "超过保留期不再置顶");
        check(record.isEmpty(), "记录空了就不再占着玩家条目");

        final List<ItemStack> many = new ArrayList<>();
        for (int i = 0; i < CraftedItemPinTracker.MAX_ENTRIES + 10; i++) {
            many.add(new ItemStack(Items.STICK, 1, i));
        }
        snapshot = record.observe(many, start);
        check(snapshot.pinned.size() == CraftedItemPinTracker.MAX_ENTRIES,
                "同时置顶的数量被限制在上限内（实际 " + snapshot.pinned.size() + "）");

        snapshot = record.observe(Collections.<ItemStack>emptyList(), start + CraftedItemPinTracker.KEEP_MILLIS + 1L);
        check(snapshot.pinned.isEmpty(), "没有产出时不会置顶任何东西");
    }

    private static void checkPerPlayerIsolation() {
        final UUID alice = UUID.randomUUID();
        final UUID bob = UUID.randomUUID();
        final ItemStack output = new ItemStack(Items.STICK);
        final long now = 5_000_000L;

        CraftedItemPinTracker.observe(alice, Collections.singletonList(output), now);
        check(CraftedItemPinTracker.observe(bob, Collections.<ItemStack>emptyList(), now).pinned.isEmpty(),
                "记录按玩家分开：别人下的单不会置顶到我这边");

        CraftedItemPinTracker.forget(alice);
        check(CraftedItemPinTracker.observe(alice, Collections.<ItemStack>emptyList(), now + 1000L).pinned.isEmpty(),
                "玩家退出后记录被清掉");
    }

    private static void checkRegistered() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                CraftedItemPinSelfCheck.class.getClassLoader().getResourceAsStream("mixins.ae2us.json"),
                StandardCharsets.UTF_8)) {
            final JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            check(listed(json, "mixins", CONTAINER_MIXIN), CONTAINER_MIXIN + " 已注册进 mixins.ae2us.json");
            check(listed(json, "client", REPO_MIXIN), REPO_MIXIN + " 已注册进 mixins.ae2us.json");
            check(listed(json, "client", GUI_MIXIN), GUI_MIXIN + " 已注册进 mixins.ae2us.json");
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

    private static boolean contains(final List<ItemStack> list, final ItemStack expected) {
        final ItemKey key = new ItemKey(expected);
        for (final ItemStack stack : list) {
            if (key.equals(new ItemKey(stack))) {
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
