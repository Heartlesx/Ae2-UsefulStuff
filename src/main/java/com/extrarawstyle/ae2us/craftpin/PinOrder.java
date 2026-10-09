package com.extrarawstyle.ae2us.craftpin;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 把置顶条目提到 ME 终端列表最前的稳定分区。
 * <p>
 * 关键点：置顶条目以**完整物品清单**为准，而不是以 AE2 过滤后的 view 为准。
 * 否则搜索时会出问题——置顶的物品只要不匹配当前搜索词就不在 view 里，看上去就像"置顶丢了"
 * （其实没丢，只是被搜索过滤掉了）。这里把被过滤掉的置顶条目从完整清单里捞回来放到最前，
 * 于是不管搜什么，刚做出来的东西始终在第一眼能看到的位置。
 * <p>
 * 顺序：匹配当前搜索的置顶条目（保持 AE2 自己的排序）→ 被搜索过滤掉的置顶条目（按清单顺序）→ 其余。
 * 没有置顶条目时返回 null，调用方保持列表不动。
 */
public final class PinOrder {

    private PinOrder() {
    }

    /** 条目对应的物品键；取不到物品定义时返回 null。 */
    public static ItemKey keyOf(final IAEItemStack stack) {
        if (stack == null) {
            return null;
        }
        final ItemStack definition = stack.getDefinition();
        return definition == null || definition.isEmpty() ? null : new ItemKey(definition);
    }

    /**
     * @param view        AE2 排好序、且已按搜索过滤过的列表
     * @param all         完整物品清单（不受搜索过滤）
     * @param pinnedKeys  当前置顶的物品键
     * @return 新的完整顺序；一条置顶都找不到时返回 {@code null}
     */
    public static List<IAEItemStack> apply(final List<IAEItemStack> view, final Iterable<IAEItemStack> all,
            final Set<ItemKey> pinnedKeys) {
        if (view == null || pinnedKeys.isEmpty()) {
            return null;
        }

        final List<IAEItemStack> pinned = new ArrayList<>();
        final List<IAEItemStack> rest = new ArrayList<>(view.size());
        final Set<ItemKey> seen = new HashSet<>();
        for (final IAEItemStack stack : view) {
            final ItemKey key = keyOf(stack);
            if (key != null && pinnedKeys.contains(key)) {
                pinned.add(stack);
                seen.add(key);
            } else {
                rest.add(stack);
            }
        }

        // 从完整清单里补回被搜索过滤掉的置顶条目。seen 用来去重：已经在 view 里的不再加一遍。
        for (final IAEItemStack stack : all) {
            final ItemKey key = keyOf(stack);
            if (key != null && !seen.contains(key) && pinnedKeys.contains(key)) {
                pinned.add(stack);
                seen.add(key);
            }
        }

        if (pinned.isEmpty()) {
            return null;
        }
        pinned.addAll(rest);
        return pinned;
    }
}
