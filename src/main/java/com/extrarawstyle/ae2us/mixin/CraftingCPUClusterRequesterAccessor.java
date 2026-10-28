package com.extrarawstyle.ae2us.mixin;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

/**
 * 读合成 CPU 的"下单者"。AE2 只在发起者是玩家时填这个字段：
 * 玩家下单走 ContainerCraftConfirm.startJob → PlayerSource，这里拿到玩家 UUID；
 * 机器（例如本模组的库存维持器）走 MachineSource，这里恒为 null。
 *
 * 该字段没有公开 getter，只能开个 accessor。注意不能用 ICraftingLink.isStandalone() 区分：
 * 玩家下单传给 submitJob 的 requestingMachine 也是 null，两种订单同为 standalone。
 */
@Mixin(CraftingCPUCluster.class)
public interface CraftingCPUClusterRequesterAccessor {

    @Accessor("requestingPlayerUUID")
    UUID ae2us$requestingPlayer();
}
