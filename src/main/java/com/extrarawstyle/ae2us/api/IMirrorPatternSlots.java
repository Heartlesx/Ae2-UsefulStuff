package com.extrarawstyle.ae2us.api;

/**
 * 由 Mixin 织入 {@link appeng.helpers.DualityInterface}。
 * AE2 的样板槽固定 36 个（{@code NUMBER_OF_PATTERN_SLOTS} 是编译期常量），
 * 镜像样板供应器要装下多个源接口的全部样板，得在运行时把那个槽位数组换成更大的。
 */
public interface IMirrorPatternSlots {

    void ae2us$resizePatternSlots(int slots);
}
