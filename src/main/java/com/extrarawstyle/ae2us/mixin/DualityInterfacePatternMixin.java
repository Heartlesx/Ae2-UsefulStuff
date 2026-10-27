package com.extrarawstyle.ae2us.mixin;

import appeng.helpers.DualityInterface;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.inv.IAEAppEngInventory;
import com.extrarawstyle.ae2us.api.IMirrorPatternSlots;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 只给镜像样板供应器用：把 DualityInterface 的样板槽换成更大的数组。
 * 普通接口不会调它，槽数仍是 AE2 原来的 36。
 * <p>
 * AE2 自己的 {@code updateCraftingList()} 走的是 {@code patterns.getSlots()}，不是那个 36 的常量，
 * 所以换掉这个字段之后配方表、提供样板、推样板全部照常工作。
 */
@Mixin(value = DualityInterface.class, remap = false)
public abstract class DualityInterfacePatternMixin implements IMirrorPatternSlots {

    @Shadow
    @Final
    @Mutable
    private AppEngInternalInventory patterns;

    @Override
    public void ae2us$resizePatternSlots(final int slots) {
        if (this.patterns.getSlots() == slots) {
            return;
        }
        final AppEngInternalInventory resized = new AppEngInternalInventory(
                (IAEAppEngInventory) (Object) this, slots, 1);
        final int keep = Math.min(slots, this.patterns.getSlots());
        for (int i = 0; i < keep; i++) {
            final ItemStack stack = this.patterns.getStackInSlot(i);
            if (!stack.isEmpty()) {
                resized.setStackInSlot(i, stack);
            }
        }
        this.patterns = resized;
    }
}
