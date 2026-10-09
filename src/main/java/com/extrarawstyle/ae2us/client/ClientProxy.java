package com.extrarawstyle.ae2us.client;

import com.extrarawstyle.ae2us.CommonProxy;
import com.extrarawstyle.ae2us.PatternUploadService;
import com.extrarawstyle.ae2us.PatternUploadData;
import net.minecraft.client.Minecraft;

import java.util.List;

public class ClientProxy extends CommonProxy {

    @Override
    public Object oreBusGui(final net.minecraft.entity.player.InventoryPlayer inventory,
                            final appeng.parts.automation.PartUpgradeable bus) {
        return new GuiOreExportBus(bus instanceof com.extrarawstyle.ae2us.orebus.PartOreStorageBus
                ? new com.extrarawstyle.ae2us.orebus.ContainerOreExportBus(inventory, (com.extrarawstyle.ae2us.orebus.PartOreStorageBus) bus)
                : new com.extrarawstyle.ae2us.orebus.ContainerOreExportBus(inventory, (com.extrarawstyle.ae2us.orebus.PartOreExportBus) bus));
    }

    @Override
    public void oreBusPartition(final com.extrarawstyle.ae2us.orebus.OreBusNetwork.Partition state) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            final net.minecraft.entity.player.EntityPlayer player = Minecraft.getMinecraft().player;
            if (player != null && player.openContainer instanceof com.extrarawstyle.ae2us.orebus.ContainerOreExportBus
                    && player.openContainer.windowId == state.window) {
                final com.extrarawstyle.ae2us.orebus.ContainerOreExportBus container =
                        (com.extrarawstyle.ae2us.orebus.ContainerOreExportBus) player.openContainer;
                if (container.storage == null) { return; }
                if (state.first) { container.partitionNames.clear(); container.partitionReady = false; }
                container.partitionNames.addAll(state.names);
                container.partitionReady = state.last;
            }
        });
    }

    @Override
    public Object storageAlarmGui(final net.minecraft.entity.player.InventoryPlayer inventory,
                                  final com.extrarawstyle.ae2us.alarm.TileStorageAlarm tile) {
        return new GuiStorageAlarm(new com.extrarawstyle.ae2us.alarm.ContainerStorageAlarm(inventory, tile));
    }

    @Override
    public void alarmState(final com.extrarawstyle.ae2us.alarm.AlarmNetwork.State state) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            final net.minecraft.entity.player.EntityPlayer player = Minecraft.getMinecraft().player;
            if (player != null && player.openContainer instanceof com.extrarawstyle.ae2us.alarm.ContainerStorageAlarm
                    && player.openContainer.windowId == state.window) {
                final com.extrarawstyle.ae2us.alarm.ContainerStorageAlarm container =
                        (com.extrarawstyle.ae2us.alarm.ContainerStorageAlarm) player.openContainer;
                container.state = state;
                container.page = state.page;
            }
        });
    }

    @Override
    public void oreBusState(final com.extrarawstyle.ae2us.orebus.OreBusNetwork.State state) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            final net.minecraft.entity.player.EntityPlayer player = Minecraft.getMinecraft().player;
            if (player != null && player.openContainer instanceof com.extrarawstyle.ae2us.orebus.ContainerOreExportBus
                    && player.openContainer.windowId == state.window) {
                ((com.extrarawstyle.ae2us.orebus.ContainerOreExportBus) player.openContainer).state = state;
            }
        });
    }

    @Override
    public void pinnedItems(final int window, final List<net.minecraft.item.ItemStack> items) {
        Minecraft.getMinecraft().addScheduledTask(() -> CraftedItemPinClient.set(window, items));
    }

    @Override
    public void showUploadTargets(final List<PatternUploadService.TargetInfo> targets) {
        Minecraft.getMinecraft().addScheduledTask(() -> PatternUploadClient.show(targets));
    }

    @Override
    public void setUndoAvailable(final boolean available) {
        Minecraft.getMinecraft().addScheduledTask(() -> PatternUploadClient.setUndoAvailable(available));
    }

    @Override
    public void updateUploadSettings(final boolean enabled, final List<PatternUploadData.Mapping> mappings) {
        Minecraft.getMinecraft().addScheduledTask(() -> PatternUploadClient.updateSettings(enabled, mappings));
    }

    @Override
    public void finishAutoUpload() {
        Minecraft.getMinecraft().addScheduledTask(PatternUploadClient::finishAutoUpload);
    }
}
