package com.extrarawstyle.ae2us.maintainer;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import java.util.function.Function;

/**
 * 库存维持器的 TheOneProbe 集成：仅在本类被 TOP 加载时才触碰 TOP 类。
 * <p>
 * 摘要（状态 + 条数）谁都看得见；逐条明细只在扩展模式显示，且最多 {@link MaintainerProbe#DETAIL_MAX} 条、
 * 异常优先——异常是玩家要处理的事，比"正在跑"更该先占位置。
 */
public class TopMaintainerIntegration implements Function<ITheOneProbe, Void> {
    @Override
    public Void apply(final ITheOneProbe probe) {
        probe.registerProvider(new TopMaintainerProvider());
        return null;
    }

    private static final class TopMaintainerProvider implements IProbeInfoProvider {

        @Override
        public String getID() {
            return "ae2us:stock_maintainer";
        }

        @Override
        public void addProbeInfo(final ProbeMode mode, final IProbeInfo info, final EntityPlayer player,
                                 final World world, final IBlockState blockState, final IProbeHitData data) {
            final TileEntity tile = world.getTileEntity(data.getPos());
            if (!(tile instanceof TileStockMaintainer)) { return; }
            final TileStockMaintainer maintainer = (TileStockMaintainer) tile;

            if (!maintainer.isActive()) {
                info.text(I18n.translateToLocalFormatted(maintainer.statusOrdinal() == TileStockMaintainer.STATUS_NO_POWER
                        ? "ae2us.maintainer.top.no_power" : "ae2us.maintainer.top.no_channel"));
                return;
            }

            final MaintainerProbe.Summary summary = MaintainerProbe.summarize(maintainer.book().rules());
            if (!summary.hasAnything()) {
                info.text(I18n.translateToLocal("ae2us.maintainer.top.empty"));
                return;
            }
            info.text(I18n.translateToLocalFormatted("ae2us.maintainer.top.summary",
                    summary.enabled, summary.running, summary.problems));

            if (mode != ProbeMode.EXTENDED) { return; }
            for (final MaintainerProbe.Line line : summary.details) {
                final IProbeInfo row = info.horizontal();
                final ItemStack icon = iconOf(line.target);
                if (!icon.isEmpty()) { row.item(icon); }
                if (line.kind == MaintainerProbe.Line.KIND_RUNNING) {
                    row.text(I18n.translateToLocalFormatted("ae2us.maintainer.top.running",
                            line.observed, line.threshold));
                } else {
                    row.text(I18n.translateToLocal("ae2us.maintainer.state." + MaintainerStatus.key(line.status)));
                }
            }
            if (summary.hidden > 0) {
                info.text(I18n.translateToLocalFormatted("ae2us.maintainer.top.more", summary.hidden));
            }
        }

        /** 目标可能是物品也可能是流体；流体没有物品形态时就不画图标。 */
        private static ItemStack iconOf(final Object target) {
            if (target instanceof appeng.api.storage.data.IAEItemStack) {
                final ItemStack stack = ((appeng.api.storage.data.IAEItemStack) target).getDefinition();
                return stack == null ? ItemStack.EMPTY : stack;
            }
            return ItemStack.EMPTY;
        }
    }
}
