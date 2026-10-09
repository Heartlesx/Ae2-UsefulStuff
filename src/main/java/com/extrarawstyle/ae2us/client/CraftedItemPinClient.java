package com.extrarawstyle.ae2us.client;

import com.extrarawstyle.ae2us.Ae2us;
import com.extrarawstyle.ae2us.FeatureConfig;
import com.extrarawstyle.ae2us.craftpin.CraftedItemPinNetwork;
import com.extrarawstyle.ae2us.craftpin.ItemKey;
import com.extrarawstyle.ae2us.craftpin.PinOrder;

import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.ITooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端保存"当前该置顶的物品"，由服务端在终端打开时下发（见 CraftedItemPinNetwork）。
 * 列表重排（ItemRepoPinMixin）和角标绘制（AEBaseGuiPinMixin）都从这里读。
 * <p>
 * dirty 标志是必需的：AE2 的 ItemRepo.updateView 在"清单没变、排序设置也没变"时会提前返回，
 * 光把数据换掉不会触发重排；标脏之后由下一次 updateView 消费，重排一次即可。
 * <p>
 * 数据带容器 windowId，读的时候核对当前打开的容器，避免切终端/关终端后用到过期集合。
 * <p>
 * 同时负责物品列表左边的"清除合成置顶"按钮：点一下发空包给服务端清记录，
 * 服务端下一次轮询回下新的置顶集合，这里靠重排逻辑自动把顺序还原。
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Ae2us.MODID, value = Side.CLIENT)
public final class CraftedItemPinClient {

    private static final int BUTTON_ID = 60004;
    private static final ResourceLocation STATES = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    private static Set<ItemKey> pinned = Collections.emptySet();
    private static int window = -1;
    private static boolean dirty;
    private static ClearButton button;

    private CraftedItemPinClient() {
    }

    public static void set(final int containerWindow, final List<ItemStack> items) {
        final Set<ItemKey> keys = new HashSet<>();
        for (final ItemStack stack : items) {
            if (stack != null && !stack.isEmpty()) {
                keys.add(new ItemKey(stack));
            }
        }
        pinned = keys;
        window = containerWindow;
        dirty = true;
    }

    /** 当前生效的置顶键集合；打开的容器对不上时返回空集合，避免用过期数据。 */
    public static Set<ItemKey> activeKeys() {
        return ae2us$matchesOpenContainer() ? pinned : Collections.emptySet();
    }

    public static boolean isPinned(final IAEItemStack stack) {
        final ItemKey key = PinOrder.keyOf(stack);
        return key != null && activeKeys().contains(key);
    }

    /** 当前终端有没有置顶物品：决定按钮是否可用（没有就压暗）。 */
    public static boolean hasPins() {
        return !activeKeys().isEmpty();
    }

    /** 有新数据还没应用到列表上。 */
    public static boolean isDirty() {
        return dirty && ae2us$matchesOpenContainer();
    }

    public static void clearDirty() {
        dirty = false;
    }

    /**
     * 是否需要把置顶顺序重新应用到列表上：列表被 AE2 重建过（对象变了），或者置顶集合刚变过。
     * <p>
     * 必须两个条件都看。只看"数据变过"就是 1.3.5-dev3 的 bug：收到数据的下一 tick 就把标志消费掉，
     * 可那会儿物品还在合成、不在列表里，什么也没提到前面；等物品到货、AE2 重建列表时标志早没了，
     * 于是只剩角标、没有置顶。只看"列表重建过"则会在置顶集合变化时漏掉更新。
     */
    public static <T> boolean needsReapply(final T currentView, final T lastOrderedView, final boolean dataChanged) {
        return currentView != lastOrderedView || dataChanged;
    }

    private static boolean ae2us$matchesOpenContainer() {
        final EntityPlayer player = Minecraft.getMinecraft().player;
        return player != null && player.openContainer != null && player.openContainer.windowId == window;
    }

    @SubscribeEvent
    public static void onGuiOpen(final GuiOpenEvent event) {
        if (event.getGui() != null) {
            button = null;
        }
    }

    @SubscribeEvent
    public static void onInit(final GuiScreenEvent.InitGuiEvent.Post event) {
        // 物品型 ME 终端（普通/合成/无线/样板终端，AE2FC 的流体形态也是 GuiPatternTerm 的子类）
        if (!FeatureConfig.craftedItemPinEnabled() || !(event.getGui() instanceof GuiMEMonitorable)) {
            return;
        }
        final GuiContainer gui = (GuiContainer) event.getGui();
        // 紧贴物品列表左侧、与第一行同高。x 与 AE2 自己的设置按钮列对齐（initGui 里是 guiLeft - 18）；
        // 面板内那个高度只有 9 像素边距，放不下 16x16。
        button = new ClearButton(BUTTON_ID, gui.getGuiLeft() - 18, gui.getGuiTop() + 14);
        event.getButtonList().add(button);
    }

    @SubscribeEvent
    public static void onAction(final GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getButton() != button) {
            return;
        }
        Minecraft.getMinecraft().getSoundHandler().playSound(
                PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        CraftedItemPinNetwork.sendClear();
        event.setCanceled(true);
    }

    /** 灰底 + 青色叉。叉和置顶角标同色，意思就是"把这些青色标记清掉"。 */
    private static final class ClearButton extends GuiButton implements ITooltip {

        private ClearButton(final int id, final int x, final int y) {
            super(id, x, y, 16, 16, "");
        }

        @Override
        public void drawButton(final Minecraft mc, final int mouseX, final int mouseY, final float partial) {
            if (!this.visible) {
                return;
            }
            this.hovered = mouseX >= this.x && mouseY >= this.y
                    && mouseX < this.x + this.width && mouseY < this.y + this.height;
            final boolean pins = CraftedItemPinClient.hasPins();

            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.renderEngine.bindTexture(STATES);
            this.drawTexturedModalRect(this.x, this.y, 240, 240, 16, 16);
            ae2us$drawCross(this.x + 4, this.y + 4, pins ? 0xFF33CCFF : 0xFF9AA0A6);

            if (!pins) {
                Gui.drawRect(this.x, this.y, this.x + this.width, this.y + this.height, 0x99000000);
            } else if (this.hovered) {
                Gui.drawRect(this.x, this.y, this.x + this.width, this.y + this.height, 0x22FFFFFF);
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        @Override
        public String getMessage() {
            return I18n.format("ae2us.pin.button") + '\n'
                    + I18n.format(CraftedItemPinClient.hasPins() ? "ae2us.pin.count" : "ae2us.pin.empty",
                            CraftedItemPinClient.pinned.size());
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

        private static void ae2us$drawCross(final int x, final int y, final int color) {
            for (int i = 0; i < 8; i++) {
                Gui.drawRect(x + i, y + i, x + i + 1, y + i + 1, color);
                Gui.drawRect(x + 7 - i, y + i, x + 8 - i, y + i + 1, color);
            }
        }
    }
}
