package com.extrarawstyle.ae2us.parallel;

/**
 * 高级并行处理单元的瓦片实现它，供 CraftingCPUClusterMixin 在组块时补上差额并行数。
 * 单独抽出接口是为了让 mixin 只依赖这一个窄契约，不直接引用瓦片类。
 */
public interface IParallelCraftingUnit {
    int extraParallels();
}
