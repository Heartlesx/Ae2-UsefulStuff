package com.extrarawstyle.ae2us.alarm;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import java.util.function.Function;

/** TheOneProbe 集成：仅在本类被 TOP 加载时才触碰 TOP 类。 */
public class TopAlarmIntegration implements Function<ITheOneProbe, Void> {
    @Override
    public Void apply(final ITheOneProbe probe) {
        probe.registerProvider(new TopAlarmProvider());
        return null;
    }

    private static final class TopAlarmProvider implements IProbeInfoProvider {
        private static final int SHOW_MAX = 5;

        @Override
        public String getID() {
            return "ae2us:storage_alarm";
        }

        @Override
        public void addProbeInfo(final ProbeMode mode, final IProbeInfo info, final net.minecraft.entity.player.EntityPlayer player,
                                 final World world, final net.minecraft.block.state.IBlockState blockState, final IProbeHitData data) {
            final TileEntity tile = world.getTileEntity(data.getPos());
            if (!(tile instanceof TileStorageAlarm)) { return; }
            final TileStorageAlarm alarm = (TileStorageAlarm) tile;
            if (!alarm.isActive()) {
                info.text(I18n.translateToLocalFormatted(alarm.statusOrdinal() == TileStorageAlarm.STATUS_NO_POWER
                        ? "ae2us.alarm.top.no_power" : "ae2us.alarm.top.no_channel"));
                return;
            }
            int total = 0;
            int shown = 0;
            for (int i = 0; i < TileStorageAlarm.CAPACITY; i++) {
                final TileStorageAlarm.Entry entry = alarm.entry(i);
                if (entry.item.isEmpty() || !entry.below) { continue; }
                total++;
                if (shown < SHOW_MAX) {
                    shown++;
                    final IProbeInfo row = info.horizontal();
                    row.item(entry.item);
                    row.text(I18n.translateToLocalFormatted("ae2us.alarm.top.entry", entry.count, entry.threshold));
                }
            }
            if (total == 0) {
                info.text(I18n.translateToLocalFormatted("ae2us.alarm.top.all_ok"));
            } else {
                info.text(I18n.translateToLocalFormatted("ae2us.alarm.top.summary", total, TileStorageAlarm.CAPACITY));
                if (total > shown) { info.text(I18n.translateToLocalFormatted("ae2us.alarm.top.more", total - shown)); }
            }
        }
    }
}
