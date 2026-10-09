package com.extrarawstyle.ae2us.api;

import appeng.helpers.DualityInterface;

/** 由 ContainerInterface 的 Mixin 织入：服务端用它拿到接口逻辑，客户端用它拿同步过来的开关状态。 */
public interface ISmartBlockingContainer {

    DualityInterface ae2us$getInterfaceDuality();

    /**
     * 同步过来的开关状态。
     * 客户端不能直接读本地接口逻辑上的开关：切换只发生在服务端，本地那份不会跟着变。
     */
    boolean ae2us$isSmartBlockingEnabled();
}
