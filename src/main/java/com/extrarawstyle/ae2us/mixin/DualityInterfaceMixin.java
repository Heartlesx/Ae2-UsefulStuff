package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.SmartBlocking;
import com.extrarawstyle.ae2us.api.ISmartBlocking;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.helpers.DualityInterface;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 智能阻挡的判定全在这里：
 * <ul>
 *     <li>每个面记下"上一次成功推出去的样板"；</li>
 *     <li>原版阻挡本来要拦下这次推送时，如果推的还是同一样板，就放行——同一配方可以持续补料；</li>
 *     <li>换别的样板时仍按原版阻挡走，所以第一台机器里的东西没消化完之前，别的配方不会挤进来；</li>
 *     <li>开着智能阻挡的接口不再因为"目标满了"被判成忙，否则合成 CPU 压根不会调 pushPattern。</li>
 * </ul>
 * 二合一接口（Fluid Craft for AE2）的物品侧就是一个 DualityInterface 实例，它的阻挡检查也在同一段代码里，
 * 所以物品和流体一起被这套规则管住。
 */
@Mixin(value = DualityInterface.class, remap = false)
public abstract class DualityInterfaceMixin {

    @Unique
    private SmartBlocking ae2us$memory;

    @Unique
    private ICraftingPatternDetails ae2us$pushing;

    @Unique
    private EnumFacing ae2us$face;

    // 记下这次推的是哪个样板；退出时清掉，免得其它调用（例如 isBusy 里的阻挡查询）读到上一轮的值。
    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void ae2us$beginPush(final ICraftingPatternDetails patternDetails, final InventoryCrafting table,
            final CallbackInfoReturnable<Boolean> cir) {
        this.ae2us$pushing = patternDetails;
        this.ae2us$face = null;
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void ae2us$endPush(final ICraftingPatternDetails patternDetails, final InventoryCrafting table,
            final CallbackInfoReturnable<Boolean> cir) {
        this.ae2us$pushing = null;
        this.ae2us$face = null;
    }

    // pushPattern 每轮循环开头都会用当前朝向取一次相邻方块位置，从这里拿到本轮处理的是哪一面。
    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/BlockPos;func_177972_a(Lnet/minecraft/util/EnumFacing;)Lnet/minecraft/util/math/BlockPos;"))
    private BlockPos ae2us$trackFace(final BlockPos pos, final EnumFacing face, final Operation<BlockPos> original) {
        this.ae2us$face = face;
        return original.call(pos, face);
    }

    // 放行：这一面上一轮成功推出去的就是这次要推的样板，那就不算被阻挡。
    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/helpers/DualityInterface;isBlocking()Z"))
    private boolean ae2us$allowSamePattern(final DualityInterface self, final Operation<Boolean> original) {
        final boolean blocked = original.call(self);
        if (!blocked || !((ISmartBlocking) this).ae2us$isSmartBlocking()
                || this.ae2us$pushing == null || this.ae2us$face == null) {
            return blocked;
        }
        return !this.ae2us$memory().isRepeating(this.ae2us$face.getIndex(), this.ae2us$pushing.getPattern());
    }

    // 推送成功时两个分支都会走到 addToSendListFacing，在这里记下"这一面推出去的是哪个样板"。
    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/helpers/DualityInterface;addToSendListFacing(Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/EnumFacing;)V"))
    private void ae2us$recordPushedPattern(final DualityInterface self, final ItemStack stack, final EnumFacing face,
            final Operation<Void> original) {
        if (this.ae2us$pushing != null) {
            this.ae2us$memory().record(face.getIndex(), this.ae2us$pushing.getPattern());
        }
        original.call(self, stack, face);
    }

    @Inject(method = "isBusy", at = @At("HEAD"), cancellable = true)
    private void ae2us$busyOnlyForPendingItems(final CallbackInfoReturnable<Boolean> cir) {
        if (((ISmartBlocking) this).ae2us$isSmartBlocking()) {
            final DualityInterfaceInvoker invoker = (DualityInterfaceInvoker) (Object) this;
            cir.setReturnValue(invoker.ae2us$hasItemsToSend() || invoker.ae2us$hasItemsToSendFacing());
        }
    }

    @Unique
    private SmartBlocking ae2us$memory() {
        if (this.ae2us$memory == null) {
            this.ae2us$memory = new SmartBlocking((a, b) -> ItemStack.areItemStacksEqual((ItemStack) a, (ItemStack) b));
        }
        return this.ae2us$memory;
    }
}
