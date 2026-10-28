package com.extrarawstyle.ae2us.mirror;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.util.AEPartLocation;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import java.util.function.Function;

/**
 * 镜像样板供应器的 TheOneProbe 集成：仅在本类被 TOP 加载时才触碰 TOP 类。
 * <p>
 * AE2 自带的部件 TOP 已经给出"设备开启 / 频道"，这里只补它没有的：
 * 绑了几个源接口、镜像了多少样板、阻挡模式实际是开还是关。
 * 源接口坐标只在扩展模式显示，且只报第一个——绑多个源时列全是一长串。
 */
public class TopMirrorIntegration implements Function<ITheOneProbe, Void> {
    @Override
    public Void apply(final ITheOneProbe probe) {
        probe.registerProvider(new TopMirrorProvider());
        return null;
    }

    private static final class TopMirrorProvider implements IProbeInfoProvider {

        @Override
        public String getID() {
            return "ae2us:mirror_pattern_provider";
        }

        @Override
        public void addProbeInfo(final ProbeMode mode, final IProbeInfo info, final EntityPlayer player,
                                 final World world, final IBlockState blockState, final IProbeHitData data) {
            final PartMirrorProvider mirror = findMirror(world, data.getPos());
            if (mirror == null) { return; }

            final int bindings = mirror.bindingCount();
            if (bindings <= 0) {
                info.text(I18n.translateToLocal("ae2us.mirror.top.no_binding"));
                return;
            }
            info.text(I18n.translateToLocalFormatted("ae2us.mirror.top.bindings", bindings));
            info.text(I18n.translateToLocalFormatted("ae2us.mirror.top.patterns",
                    mirror.mirroredPatternCount(), PartMirrorProvider.PATTERN_SLOTS));
            // 阻挡跟随源接口合成出来的实际生效值，不是各源分别开没开。
            final boolean blocking = mirror.getConfigManager().getSetting(Settings.BLOCK) == YesNo.YES;
            info.text(I18n.translateToLocal(blocking ? "ae2us.mirror.top.blocking_on" : "ae2us.mirror.top.blocking_off"));
            if (mirror.truncated()) {
                info.text(I18n.translateToLocal("ae2us.mirror.top.truncated"));
            }

            if (mode != ProbeMode.EXTENDED) { return; }
            final BlockPos first = mirror.firstBindingPos();
            if (first != null) {
                info.text(I18n.translateToLocalFormatted("ae2us.mirror.top.source",
                        first.getX() + ", " + first.getY() + ", " + first.getZ()));
            }
        }
    }

    /** 只遍历六个侧面：AEPartLocation.INTERNAL 是线缆本体，传 null 还会 NPE。 */
    private static PartMirrorProvider findMirror(final World world, final BlockPos pos) {
        if (world == null || !world.isBlockLoaded(pos)) { return null; }
        final TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof IPartHost)) { return null; }
        final IPartHost host = (IPartHost) tile;
        for (final AEPartLocation side : AEPartLocation.SIDE_LOCATIONS) {
            final IPart part = host.getPart(side);
            if (part instanceof PartMirrorProvider) {
                return (PartMirrorProvider) part;
            }
        }
        return null;
    }
}
