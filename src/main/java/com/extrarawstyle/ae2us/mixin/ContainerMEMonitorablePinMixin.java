package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.FeatureConfig;
import com.extrarawstyle.ae2us.craftpin.CraftedItemPinNetwork;
import com.extrarawstyle.ae2us.craftpin.CraftedItemPinTracker;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端：ME 终端打开期间，周期性看一眼"哪些合成任务在跑"，把产出连同最近完成的一起下发给客户端置顶。
 * <p>
 * 只读合成 CPU 的状态，不改 AE2 的任何合成流程；不发包给别的东西，也只发物品定义。
 * 首次必定下发一次（否则客户端手里可能还是上一个终端的集合），之后只在集合真的变了才发。
 * <p>
 * 注入 func_75142_b（Container.detectAndSendChanges，SRG 名——本工程不生成 refmap，注解里必须写运行时名字）。
 * 整个流程包在 try/catch 里：置顶只是显示优化，任何异常都不该把游戏带崩。
 */
@Mixin(value = ContainerMEMonitorable.class, remap = false)
public abstract class ContainerMEMonitorablePinMixin {

    /** 轮询间隔（tick）。合成 CPU 的状态变得不快，没必要每 tick 都算。 */
    private static final int POLL_INTERVAL = 10;

    @Shadow
    private IGridNode networkNode;

    /** 本容器是否已经下发过一次；首次必须发，之后靠 changed 判断。 */
    @Unique
    private boolean ae2us$pinSent;

    /** 出错一次就不再尝试，避免每 tick 刷屏。 */
    @Unique
    private boolean ae2us$pinFailed;

    @Inject(method = "func_75142_b", at = @At("TAIL"))
    private void ae2us$sendPinnedItems(final CallbackInfo ci) {
        if (!FeatureConfig.craftedItemPinEnabled() || this.ae2us$pinFailed) {
            return;
        }
        final EntityPlayer player = ((AEBaseContainer) (Object) this).getInventoryPlayer().player;
        if (!(player instanceof EntityPlayerMP) || player.world == null || player.world.isRemote) {
            return;
        }
        if (this.ae2us$pinSent && (player.ticksExisted % POLL_INTERVAL) != 0) {
            return;
        }
        try {
            final CraftedItemPinTracker.Snapshot snapshot = CraftedItemPinTracker.observe(
                    player.getUniqueID(), this.ae2us$busyOutputs(), System.currentTimeMillis());
            if (snapshot.changed || !this.ae2us$pinSent) {
                // windowId 在 vanilla 的 Container 上（本工程不生成 refmap，普通字段引用由 reobf 处理）。
                final int window = ((AEBaseContainer) (Object) this).windowId;
                CraftedItemPinNetwork.sendTo((EntityPlayerMP) player, window, snapshot.pinned);
                this.ae2us$pinSent = true;
            }
        } catch (final RuntimeException e) {
            this.ae2us$pinFailed = true;
            LogManager.getLogger("AE2US").warn("合成产出置顶：读取合成状态失败，本终端不再置顶", e);
        }
    }

    /** 当前忙碌的合成 CPU 的最终产出。读不到网格/合成缓存时返回空表，不报错。 */
    @Unique
    private List<ItemStack> ae2us$busyOutputs() {
        final List<ItemStack> busy = new ArrayList<>();
        final IGridNode node = this.networkNode;
        final IGrid grid = node == null ? null : node.getGrid();
        final ICraftingGrid crafting = grid == null ? null : grid.getCache(ICraftingGrid.class);
        if (crafting == null) {
            return busy;
        }
        for (final ICraftingCPU cpu : crafting.getCpus()) {
            if (cpu == null || !cpu.isBusy()) {
                continue;
            }
            final IAEItemStack output = cpu.getFinalOutput();
            if (output == null) {
                continue;
            }
            final ItemStack definition = output.getDefinition();
            if (definition != null && !definition.isEmpty() && ae2us$placedByPlayer(cpu)) {
                busy.add(definition);
            }
        }
        return busy;
    }

    /**
     * 这一单是不是玩家亲手下的。库存维持器之类的机器订单走 MachineSource，
     * CPU 里记录的发起者为 null，不该置顶——置顶是给"我刚下的东西"用的。
     */
    @Unique
    private static boolean ae2us$placedByPlayer(final ICraftingCPU cpu) {
        return cpu instanceof CraftingCPUCluster
                && ((CraftingCPUClusterRequesterAccessor) cpu).ae2us$requestingPlayer() != null;
    }
}
