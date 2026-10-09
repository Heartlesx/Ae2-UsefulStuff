package com.extrarawstyle.ae2us.client;

import com.extrarawstyle.ae2us.Ae2us;
import com.extrarawstyle.ae2us.FeatureConfig;
import com.extrarawstyle.ae2us.PatternRefillNetwork;
import com.extrarawstyle.ae2us.api.IPatternRefillContainer;

import appeng.api.AEApi;
import appeng.client.gui.implementations.GuiExpandedProcessingPatternTerm;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.ITooltip;
import appeng.container.implementations.ContainerPatternEncoder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 样板终端界面的空白样板自动补充按钮：物品栏是空白样板图标，按下去切换开关，暗色表示关闭。
 * 二合一（Fluid Craft for AE2）的流体终端是 GuiPatternTerm / GuiExpandedProcessingPatternTerm 的子类，
 * 自动跟着拿到这个按钮。
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Ae2us.MODID, value = Side.CLIENT)
public final class PatternRefillClient {

    private static final int BUTTON_ID = 60003;
    private static final ResourceLocation STATES = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    private static RefillButton button;

    private PatternRefillClient() {
    }

    @SubscribeEvent
    public static void onGuiOpen(final GuiOpenEvent event) {
        if (event.getGui() != null) {
            button = null;
        }
    }

    @SubscribeEvent
    public static void onInit(final GuiScreenEvent.InitGuiEvent.Post event) {
        if (!supports(event.getGui())) {
            return;
        }
        final GuiContainer gui = (GuiContainer) event.getGui();
        // 与样板上传/撤回按钮（PatternUploadClient）同列，排在撤回按钮下方一格
        button = new RefillButton(BUTTON_ID, gui.getGuiLeft() + 176, gui.getGuiTop() + gui.getYSize() - 102);
        event.getButtonList().add(button);
    }

    @SubscribeEvent
    public static void onAction(final GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getButton() != button) {
            return;
        }
        Minecraft.getMinecraft().getSoundHandler().playSound(
                PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        PatternRefillNetwork.sendToggle();
        event.setCanceled(true);
    }

    private static boolean supports(final GuiScreen gui) {
        return FeatureConfig.patternRefillEnabled()
                && (gui instanceof GuiPatternTerm || gui instanceof GuiExpandedProcessingPatternTerm)
                && ((GuiContainer) gui).inventorySlots instanceof ContainerPatternEncoder;
    }

    private static final class RefillButton extends GuiButton implements ITooltip {

        private boolean on = true;

        private RefillButton(final int id, final int x, final int y) {
            super(id, x, y, 16, 16, "");
        }

        @Override
        public void drawButton(final Minecraft mc, final int mouseX, final int mouseY, final float partial) {
            if (!this.visible) {
                return;
            }
            if (mc.currentScreen instanceof GuiContainer
                    && ((GuiContainer) mc.currentScreen).inventorySlots instanceof IPatternRefillContainer) {
                this.on = ((IPatternRefillContainer) ((GuiContainer) mc.currentScreen).inventorySlots)
                        .ae2us$isPatternRefillOn();
            }
            this.hovered = mouseX >= this.x && mouseY >= this.y
                    && mouseX < this.x + this.width && mouseY < this.y + this.height;
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.renderEngine.bindTexture(STATES);
            this.drawTexturedModalRect(this.x, this.y, 240, 240, 16, 16);
            final ItemStack blank = AEApi.instance().definitions().materials().blankPattern().maybeStack(1)
                    .orElse(ItemStack.EMPTY);
            if (!blank.isEmpty()) {
                mc.getRenderItem().renderItemAndEffectIntoGUI(blank, this.x, this.y);
            }
            if (!this.on) {
                Gui.drawRect(this.x, this.y, this.x + this.width, this.y + this.height, 0x99000000);
            } else if (this.hovered) {
                Gui.drawRect(this.x, this.y, this.x + this.width, this.y + this.height, 0x22FFFFFF);
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        @Override
        public String getMessage() {
            return I18n.format("ae2us.refill.button") + '\n'
                    + I18n.format(this.on ? "ae2us.refill.on" : "ae2us.refill.off");
        }

        @Override
        public int xPos() {
            return this.x;
        }

        @Override
        public int yPos() {
            return this.y;
        }

        @Override
        public int getWidth() {
            return this.width;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        @Override
        public boolean isVisible() {
            return this.visible;
        }
    }
}
