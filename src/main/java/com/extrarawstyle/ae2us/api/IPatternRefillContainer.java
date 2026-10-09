package com.extrarawstyle.ae2us.api;

/** 样板终端容器对外的自动补充开关：客户端读状态显示按钮，服务端由网络包触发切换。 */
public interface IPatternRefillContainer {

    boolean ae2us$isPatternRefillOn();

    void ae2us$togglePatternRefill();
}
