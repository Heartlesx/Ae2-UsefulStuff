package com.extrarawstyle.ae2us.mixin.client;

import com.extrarawstyle.ae2us.api.ICraftableItemSource;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.me.SlotME;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotFake;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 给"有样板（可合成）"的物品加一个黄色小"+"。两条来源分开处理：
 *
 * 1. ME 终端物品列表（SlotME）：在 func_146977_a（GuiContainer.drawSlot）里收集被真正画出来的槽位，
 *    用物品自己的 craftable 标志判定——列表滚动时 AE2 会自己搬槽位坐标，交给它的绘制流程最稳。
 * 2. 样板终端编码网格的输入槽：**不能**靠 drawSlot 收集。AE2FC 的流体/扩展样板终端里这些格子不走
 *    AEBaseGui.drawSlot（格子里显示的物品是 AE2FC 自己画的），所以这里在绘制时直接遍历容器的槽位判定。
 *    输入槽 = SlotFake 且不是 OptionalSlotFake（AE2 用 SlotFakeCraftingMatrix，AE2FC 用 SlotFluidConvertingFake）。
 *
 * 绘制点都在 func_146979_b（GuiContainer.drawGuiContainerForegroundLayer）的末尾：
 * AE2/AE2FC 自己的前景绘制都结束后再画，不会被盖住，坐标也仍是 GUI 内坐标。
 * 本工程不生成 refmap，所以注解里写的是运行时方法名。
 */
@Mixin(value = AEBaseGui.class, remap = false)
public abstract class AEBaseGuiMixin {

    @Unique
    private final List<Slot> ae2us$markedListSlots = new ArrayList<>();

    @Unique
    private int ae2us$lastMarkedList;

    @Unique
    private int ae2us$lastMarkedCode;

    @Inject(method = "func_146977_a", at = @At("HEAD"))
    private void ae2us$collectListSlot(final Slot slot, final CallbackInfo ci) {
        if (!(slot instanceof SlotME)) {
            return;
        }
        // 隐藏槽位（切换合成/加工模式时会挪到负坐标）不画。
        if (slot.xPos < 0 || slot.yPos < 0) {
            return;
        }
        final IAEItemStack stack = ((SlotME) slot).getAEStack();
        if (stack != null && stack.isCraftable()) {
            this.ae2us$markedListSlots.add(slot);
        }
    }

    @Inject(method = "func_146979_b", at = @At("TAIL"))
    private void ae2us$drawMarks(final int mouseX, final int mouseY, final CallbackInfo ci) {
        final Container container = ((GuiContainer) (Object) this).inventorySlots;
        final ICraftableItemSource source = container instanceof ICraftableItemSource
                ? (ICraftableItemSource) container
                : null;

        GlStateManager.disableDepth();

        final int listMarks = this.ae2us$markedListSlots.size();
        for (final Slot slot : this.ae2us$markedListSlots) {
            ae2us$drawMark(slot.xPos, slot.yPos);
        }
        this.ae2us$markedListSlots.clear();

        int codeMarks = 0;
        if (source != null) {
            for (final Slot slot : container.inventorySlots) {
                if (!ae2us$isCodeSlot(slot) || slot.xPos < 0 || slot.yPos < 0) {
                    continue;
                }
                final ItemStack display = slot.getStack();
                if (display.isEmpty() || !source.ae2us$isCraftable(display)) {
                    continue;
                }
                ae2us$drawMark(slot.xPos, slot.yPos);
                codeMarks++;
            }
        }

        GlStateManager.enableDepth();
        // Gui.drawRect 会改掉当前颜色，必须还原，否则后面的内容会被染色。
        GlStateManager.color(1F, 1F, 1F, 1F);

        if (listMarks != this.ae2us$lastMarkedList || codeMarks != this.ae2us$lastMarkedCode) {
            this.ae2us$lastMarkedList = listMarks;
            this.ae2us$lastMarkedCode = codeMarks;
            LogManager.getLogger("AE2US")
                    .info("标记列表槽 {} 个、编码槽 {} 个（GUI={}）", listMarks, codeMarks, this.getClass().getSimpleName());
        }
    }

    @Unique
    private static void ae2us$drawMark(final int slotX, final int slotY) {
        final int x = slotX + 1;
        final int y = slotY + 1;
        Gui.drawRect(x, y + 2, x + 5, y + 3, 0xFFFFDD00);
        Gui.drawRect(x + 2, y, x + 3, y + 5, 0xFFFFDD00);
    }

    /** 编码网格的输入假槽（排除样板终端右侧的输出槽）。 */
    @Unique
    private static boolean ae2us$isCodeSlot(final Slot slot) {
        return slot instanceof SlotFake && !(slot instanceof OptionalSlotFake);
    }
}
