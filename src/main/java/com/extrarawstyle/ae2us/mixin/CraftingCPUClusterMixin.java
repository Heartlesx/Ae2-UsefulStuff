package com.extrarawstyle.ae2us.mixin;

import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingTile;
import com.extrarawstyle.ae2us.parallel.IParallelCraftingUnit;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让高级并行处理单元一块顶 N 块：原版 addTile 对它先记 1 个并行单元（accelerator++），
 * 这里在计数写入后补上剩下的 N-1。addTile 里对 accelerator 的写入点唯一，
 * 自检会直接核对 AE2 jar 的字节码，防止换版本后注入位置漂移。
 */
@Mixin(CraftingCPUCluster.class)
public abstract class CraftingCPUClusterMixin {

    @Shadow
    private int accelerator;

    @Inject(method = "addTile",
            at = @At(value = "FIELD",
                    target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;accelerator:I",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER))
    private void ae2us$countExtraParallels(final TileCraftingTile te, final CallbackInfo ci) {
        if (te instanceof IParallelCraftingUnit) {
            this.accelerator += ((IParallelCraftingUnit) te).extraParallels();
        }
    }
}
