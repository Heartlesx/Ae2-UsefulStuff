package com.extrarawstyle.ae2us.craftstorage;

/**
 * 六档高级合成存储器的档位表：每档多少 k 容量（k = 1024 字节，沿用原版 1k/4k/16k/64k 的口径）、
 * 注册名与名字前缀颜色。只用 JDK 类型，方便离线自检直接跑。
 */
public enum StorageTier {
    STORAGE_256(256, "crafting_storage_256k", "\u00a73"),
    STORAGE_512(512, "crafting_storage_512k", "\u00a7b"),
    STORAGE_1024(1024, "crafting_storage_1024k", "\u00a79"),
    STORAGE_4096(4096, "crafting_storage_4096k", "\u00a71"),
    STORAGE_8192(8192, "crafting_storage_8192k", "\u00a75"),
    STORAGE_16384(16384, "crafting_storage_16384k", "\u00a7d");

    private final int kilobytes;
    private final String registryName;
    private final String colorCode;

    StorageTier(final int kilobytes, final String registryName, final String colorCode) {
        this.kilobytes = kilobytes;
        this.registryName = registryName;
        this.colorCode = colorCode;
    }

    public int kilobytes() {
        return this.kilobytes;
    }

    /** 容量（字节）。 */
    public int bytes() {
        return this.kilobytes * 1024;
    }

    public String registryName() {
        return this.registryName;
    }

    /** 名字前缀的颜色码，如 §3。 */
    public String colorCode() {
        return this.colorCode;
    }

    /** 名字前缀，如 256k。 */
    public String label() {
        return this.kilobytes + "k";
    }
}
