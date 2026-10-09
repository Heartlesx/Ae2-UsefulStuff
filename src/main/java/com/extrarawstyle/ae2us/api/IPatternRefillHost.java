package com.extrarawstyle.ae2us.api;

/** 样板终端部件的"空白样板自动补充"开关；状态保存在部件 NBT 里。 */
public interface IPatternRefillHost {

    boolean ae2us$isPatternRefill();

    void ae2us$setPatternRefill(boolean value);
}
