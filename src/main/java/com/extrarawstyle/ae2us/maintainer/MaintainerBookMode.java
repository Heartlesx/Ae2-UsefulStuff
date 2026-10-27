package com.extrarawstyle.ae2us.maintainer;

import appeng.api.storage.data.IAEStack;

/** 目标与模式的合法性：流体模式只接受流体堆，物品模式只接受物品堆。 */
public final class MaintainerBookMode {
    private MaintainerBookMode() { }

    public static boolean accepts(final IAEStack<?> target, final int mode) {
        if (target == null) { return true; }
        if (mode == MaintainerRule.MODE_FLUID) { return target.isFluid(); }
        return target.isItem();
    }
}
