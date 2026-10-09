package com.extrarawstyle.ae2us.parallel;

/**
 * 六档高级并行处理单元的档位表：每档等效多少块原版并行处理单元，以及它的名字前缀与前缀颜色。
 * 只用 JDK 类型，方便离线自检直接跑。
 */
public enum ParallelTier {
    PARALLEL_16(16, "parallel_unit_16", "§a"),
    PARALLEL_64(64, "parallel_unit_64", "§b"),
    PARALLEL_256(256, "parallel_unit_256", "§9"),
    PARALLEL_1024(1024, "parallel_unit_1024", "§d"),
    PARALLEL_2048(2048, "parallel_unit_2048", "§6"),
    PARALLEL_4096(4096, "parallel_unit_4096", "§c");

    private final int parallels;
    private final String registryName;
    private final String colorCode;

    ParallelTier(final int parallels, final String registryName, final String colorCode) {
        this.parallels = parallels;
        this.registryName = registryName;
        this.colorCode = colorCode;
    }

    /** 等效的原版并行处理单元数量。 */
    public int parallels() {
        return this.parallels;
    }

    /** 原版计的是 1 个，集群侧要补的差额。 */
    public int extraParallels() {
        return this.parallels - 1;
    }

    public String registryName() {
        return this.registryName;
    }

    /** 名字前缀的颜色码，如 §a。 */
    public String colorCode() {
        return this.colorCode;
    }

    /** 名字前缀，如 16x。 */
    public String label() {
        return this.parallels + "x";
    }
}
