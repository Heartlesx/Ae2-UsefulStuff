package com.extrarawstyle.ae2us.orebus;

import appeng.util.SettingsFrom;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * 矿典总线的内存卡载荷回环：真实部件实例走真实的 downloadSettings(SettingsFrom.MEMORY_CARD, ...)，
 * 再按 uploadSettings 的同一读法（压缩过一遍、同一键名）把规则读回来比对。
 * <p>
 * 放在 orebus 包里是因为 downloadSettings 在 AE2 里是 protected，同包才能直接调用。
 * 为什么只覆盖"存 + 读回"这两层：真实调用 uploadSettings 时，AE2 基类会复制名 "config" 的库存，
 * 途中触发 AEApi 单例初始化（AELog → AEConfig 在无游戏环境里是 null）抛 NPE，自检 JVM 里跑不了；
 * 载入侧"覆写存在且与基类签名一致"由 {@code OreExportSelfCheck} 的结构检查与编译期 @Override 保证。
 */
public final class OreBusCardSelfCheck {

    private OreBusCardSelfCheck() {
    }

    public static void verify() throws Exception {
        final PartOreExportBus exportSource = new PartOreExportBus(new ItemStack(Items.STICK));
        exportSource.rules().add(false, "oreIron\ndust*");
        exportSource.rules().add(true, "oreGold");
        exportSource.rules().setMode(OreExportRules.Mode.SEQUENTIAL);
        final NBTTagCompound exportCard = exportSource.downloadSettings(SettingsFrom.MEMORY_CARD, new NBTTagCompound());
        final NBTTagCompound exportPayload = exportCard.getCompoundTag(PartOreExportBus.RULES_KEY);
        check(exportPayload.hasKey("white") && "SEQUENTIAL".equals(exportPayload.getString("mode")),
                "export bus memory card payload carries the ore rules and mode");
        final OreExportRules exportRestored = readBack(exportPayload);
        check(exportRestored.list(false).equals(exportSource.rules().list(false))
                && exportRestored.list(true).equals(exportSource.rules().list(true))
                && exportRestored.mode() == OreExportRules.Mode.SEQUENTIAL,
                "export bus card payload reads back into the same rules and mode");

        final PartOreStorageBus storageSource = new PartOreStorageBus(new ItemStack(Items.STICK));
        storageSource.rules().add(false, "ingotCopper\nore*");
        storageSource.rules().add(true, "ingotTin");
        final NBTTagCompound storageCard = storageSource.downloadSettings(SettingsFrom.MEMORY_CARD, new NBTTagCompound());
        final NBTTagCompound storagePayload = storageCard.getCompoundTag(PartOreStorageBus.RULES_KEY);
        check(storagePayload.hasKey("white"), "storage bus memory card payload carries the ore rules");
        final OreExportRules storageRestored = readBack(storagePayload);
        check(storageRestored.list(false).equals(storageSource.rules().list(false))
                && storageRestored.list(true).equals(storageSource.rules().list(true)),
                "storage bus card payload reads back into the same rules");
    }

    /** 与 uploadSettings 里的读法一致：压缩过一遍再按同一个键读回。 */
    private static OreExportRules readBack(final NBTTagCompound payload) throws Exception {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(payload, output);
        final OreExportRules rules = new OreExportRules();
        rules.read(CompressedStreamTools.readCompressed(new ByteArrayInputStream(output.toByteArray())));
        return rules;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
