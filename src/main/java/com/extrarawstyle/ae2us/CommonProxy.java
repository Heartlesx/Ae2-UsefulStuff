package com.extrarawstyle.ae2us;

import java.util.List;

public class CommonProxy {

    public Object oreBusGui(final net.minecraft.entity.player.InventoryPlayer inventory,
                            final appeng.parts.automation.PartUpgradeable bus) {
        return null;
    }

    public void oreBusState(final com.extrarawstyle.ae2us.orebus.OreBusNetwork.State state) {
    }

    public void oreBusPartition(final com.extrarawstyle.ae2us.orebus.OreBusNetwork.Partition state) {
    }

    public Object storageAlarmGui(final net.minecraft.entity.player.InventoryPlayer inventory,
                                  final com.extrarawstyle.ae2us.alarm.TileStorageAlarm tile) {
        return null;
    }

    public void alarmState(final com.extrarawstyle.ae2us.alarm.AlarmNetwork.State state) {
    }

    public Object stockMaintainerGui(final net.minecraft.entity.player.InventoryPlayer inventory,
                                     final com.extrarawstyle.ae2us.maintainer.TileStockMaintainer tile) {
        return null;
    }

    public void stockMaintainerState(final com.extrarawstyle.ae2us.maintainer.StockMaintainerNetwork.State state) {
    }

    public void pinnedItems(final int window, final List<net.minecraft.item.ItemStack> items) {
    }

    public void showUploadTargets(final List<PatternUploadService.TargetInfo> targets) {
    }

    public void setUndoAvailable(final boolean available) {
    }

    public void updateUploadSettings(final boolean enabled, final List<PatternUploadData.Mapping> mappings) {
    }

    public void finishAutoUpload() {
    }
}
