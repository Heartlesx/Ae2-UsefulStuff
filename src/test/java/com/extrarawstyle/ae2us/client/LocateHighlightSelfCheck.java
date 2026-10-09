package com.extrarawstyle.ae2us.client;

import net.minecraft.util.math.BlockPos;

/**
 * 定位高亮的多个目标与计时：同时亮多个、重复点同一个只重新计时、各自 15 秒到期。
 * 时间直接按毫秒喂进去，不依赖真实时钟。
 */
public final class LocateHighlightSelfCheck {
    public static void main(final String[] args) {
        final BlockPos first = new BlockPos(1, 64, 1);
        final BlockPos second = new BlockPos(2, 64, 2);

        LocateHighlightClient.highlight(0, first, 0L);
        check(LocateHighlightClient.prune(0L) == 1, "single highlight stays");
        LocateHighlightClient.highlight(0, second, 1_000L);
        check(LocateHighlightClient.prune(1_000L) == 2, "two interfaces highlight at the same time");
        LocateHighlightClient.highlight(0, first, 2_000L);
        check(LocateHighlightClient.prune(2_000L) == 2, "clicking the same interface again does not add an entry");
        check(LocateHighlightClient.prune(15_000L) == 2, "re-clicking restarts only that one's timer");
        check(LocateHighlightClient.prune(16_000L) == 1, "each entry expires after its own 15 seconds");
        check(LocateHighlightClient.prune(17_000L) == 0, "all entries expire eventually");
        System.out.println("Locate highlight checks passed (multiple targets, refresh, independent expiry)");
    }

    private static void check(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
