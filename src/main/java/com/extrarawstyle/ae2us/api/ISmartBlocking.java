package com.extrarawstyle.ae2us.api;

/**
 * 由 DualityInterface 的 Mixin 织入到 AE2 的接口逻辑上。
 * AE2 的接口方块与接口面板共用同一个 DualityInterface 实例，二合一接口（Fluid Craft for AE2）的物品侧也是它。
 */
public interface ISmartBlocking {

    boolean ae2us$isSmartBlocking();

    void ae2us$setSmartBlocking(boolean value);
}
