package com.extrarawstyle.ae2us.mixin.client;

import com.extrarawstyle.ae2us.FeatureConfig;
import com.extrarawstyle.ae2us.client.CraftedItemPinClient;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.me.SlotME;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 给置顶物品画一个青色角标，和"可合成"的黄色小"+"区分开。
 * <p>
 * 路线与 AEBaseGuiMixin 的黄色标记完全一致（也是同一套已被实机验证的坐标）：
 * 在 func_146977_a（GuiContainer.drawSlot）里收集被真正画出来的 SlotME，滚动由 AE2 自己搬槽位坐标；
 * 在 func_146979_b（drawGuiContainerForegroundLayer）末尾统一画，坐标仍是 GUI 内坐标。
 * 两个 mixin 注入同一个方法互不干扰，但受各自的开关控制。
 */
@Mixin(value = AEBaseGui.class, remap = false)
public abstract class AEBaseGuiPinMixin {

    @Unique
    private final List<Slot> ae2us$pinnedSlots = new ArrayList<>();

    @Inject(method = "func_146977_a", at = @At("HEAD"))
    private void ae2us$collectPinnedSlot(final Slot slot, final CallbackInfo ci) {
        if (!(slot instanceof SlotME) || slot.xPos < 0 || slot.yPos < 0) {
            return;
        }
        final IAEItemStack stack = ((SlotME) slot).getAEStack();
        if (CraftedItemPinClient.isPinned(stack)) {
            this.ae2us$pinnedSlots.add(slot);
        }
    }

    @Inject(method = "func_146979_b", at = @At("TAIL"))
    private void ae2us$drawPins(final int mouseX, final int mouseY, final CallbackInfo ci) {
        if (!FeatureConfig.craftedItemPinEnabled() || this.ae2us$pinnedSlots.isEmpty()) {
            this.ae2us$pinnedSlots.clear();
            return;
        }
        GlStateManager.disableDepth();
        for (final Slot slot : this.ae2us$pinnedSlots) {
            ae2us$drawPin(slot.xPos, slot.yPos);
        }
        this.ae2us$pinnedSlots.clear();
        GlStateManager.enableDepth();
        // Gui.drawRect 会改掉当前颜色，必须还原，否则后面的内容会被染色。
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /** 左上角的青色角标（横 5 像素 + 竖 5 像素的 "⌐"），与黄色 "+" 形状、颜色都不同。 */
    @Unique
    private static void ae2us$drawPin(final int slotX, final int slotY) {
        Gui.drawRect(slotX, slotY, slotX + 5, slotY + 1, 0xFF33CCFF);
        Gui.drawRect(slotX, slotY, slotX + 1, slotY + 5, 0xFF33CCFF);
    }
}
