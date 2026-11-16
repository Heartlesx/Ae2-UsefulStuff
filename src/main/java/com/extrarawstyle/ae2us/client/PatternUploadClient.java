package com.extrarawstyle.ae2us.client;

import com.extrarawstyle.ae2us.Ae2us;
import com.extrarawstyle.ae2us.PatternCategory;
import com.extrarawstyle.ae2us.PatternUploadData.Mapping;
import com.extrarawstyle.ae2us.PatternUploadNetwork;
import com.extrarawstyle.ae2us.PatternUploadService.TargetInfo;
import com.extrarawstyle.ae2us.PatternUploadService.TargetInfo.MemberInfo;
import com.extrarawstyle.ae2us.mixin.PatternEncoderAccessor;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.client.gui.implementations.GuiExpandedProcessingPatternTerm;
import appeng.client.gui.implementations.GuiPatternTerm;
import appeng.client.gui.widgets.ITooltip;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.slot.SlotRestrictedInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Ae2us.MODID, value = Side.CLIENT)
public final class PatternUploadClient {

    private static GuiScreen owner;
    private static PatternButton uploadButton;
    private static PatternButton undoButton;
    private static GuiTextField search;
    private static GuiButton closeButton;
    private static GuiButton prevButton;
    private static GuiButton nextButton;
    private static GuiButton backButton;
    private static GuiButton locateButton;
    private static GuiButton bindButton;
    private static GuiButton mappingButton;
    private static GuiButton toggleButton;
    private static GuiButton saveButton;
    private static GuiButton deleteButton;
    private static GuiButton renameButton;
    private static GuiTextField nameField;
    private static TargetInfo renamingGroup;
    private static MemberInfo renamingMember;
    private static GuiTextField uidField;
    private static GuiTextField targetField;
    private static List<TargetInfo> all = Collections.emptyList();
    private static List<TargetInfo> filtered = Collections.emptyList();
    private static List<Mapping> mappings = Collections.emptyList();
    private static TargetInfo locatingGroup;
    private static int parentPage;
    private static int page;
    private static int cursorTick = -1;
    private static String lastSearchText = "";
    private static boolean open;
    private static boolean mappingOpen;
    private static boolean pending;
    private static boolean pendingAuto;
    private static boolean patternReady;
    private static boolean autoEnabled = true;
    private static boolean drawingOverlayTooltip;
    private static String categoryUid = "";
    private static String categoryTitle = "";
    private static String editingUid;
    private static String statusText;
    private static long statusUntil;

    /** 行高要放得下两行文字（名称 / 位置+空槽）和右侧 16px 图标。 */
    private static final int ROW_HEIGHT = 30;
    private static final int MAIN_WIDTH = 320;
    private static final int MAPPING_WIDTH = 360;
    /** 面板与屏幕边缘的最小距离，窄 GUI 下按此收缩。 */
    private static final int SCREEN_MARGIN = 10;

    // 原版容器界面配色：硬边、无渐变。
    private static final int COL_OUTLINE = 0xFF1B1B1B;
    private static final int COL_FACE = 0xFFC6C6C6;
    private static final int COL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COL_SHADOW = 0xFF555555;
    private static final int COL_FIELD = 0xFF8B8B8B;
    private static final int COL_FIELD_SHADOW = 0xFF373737;
    private static final int COL_ROW = 0xFFC6C6C6;
    private static final int COL_ROW_HOVER = 0xFFD9E4E6;
    private static final int COL_ROW_DIM = 0xFFAFAFAF;
    private static final int COL_TEXT = 0xFF202020;
    private static final int COL_TEXT_DIM = 0xFF4A4A4A;
    private static final int COL_INPUT = 0xFFFFFFFF;
    private static final int COL_INPUT_IDLE = 0xFFE4E4E4;

    private PatternUploadClient() {
    }

    /** 原版容器面板：黑描边、灰底面、左上高光 / 右下阴影，全部一像素硬边。 */
    private static void panel(final int x, final int y, final int width, final int height) {
        Gui.drawRect(x, y, x + width, y + height, COL_OUTLINE);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, COL_FACE);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + 2, COL_HIGHLIGHT);
        Gui.drawRect(x + 1, y + 1, x + 2, y + height - 1, COL_HIGHLIGHT);
        Gui.drawRect(x + 1, y + height - 2, x + width - 1, y + height - 1, COL_SHADOW);
        Gui.drawRect(x + width - 2, y + 1, x + width - 1, y + height - 1, COL_SHADOW);
    }

    /** 凹槽：左上压暗、右下提亮，用来做列表区和输入框底。 */
    private static void inset(final int x, final int y, final int width, final int height, final int fill) {
        Gui.drawRect(x, y, x + width, y + height, fill);
        Gui.drawRect(x, y, x + width, y + 1, COL_FIELD_SHADOW);
        Gui.drawRect(x, y, x + 1, y + height, COL_FIELD_SHADOW);
        Gui.drawRect(x + width - 1, y, x + width, y + height, COL_HIGHLIGHT);
        Gui.drawRect(x, y + height - 1, x + width, y + height, COL_HIGHLIGHT);
    }

    /** 刻线：上暗下亮的一像素分隔线（原版标题栏下那条）。 */
    private static void groove(final int x, final int y, final int width) {
        Gui.drawRect(x, y, x + width, y + 1, COL_SHADOW);
        Gui.drawRect(x, y + 1, x + width, y + 2, COL_HIGHLIGHT);
    }

    /** 列表行：凸起在凹槽之上的浅色块，左上提亮、右下压暗。 */
    private static void row(final int x, final int y, final int width, final int fill) {
        Gui.drawRect(x, y, x + width, y + ROW_HEIGHT, fill);
        Gui.drawRect(x, y, x + width, y + 1, COL_HIGHLIGHT);
        Gui.drawRect(x, y, x + 1, y + ROW_HEIGHT, COL_HIGHLIGHT);
        Gui.drawRect(x + width - 1, y, x + width, y + ROW_HEIGHT, COL_SHADOW);
        Gui.drawRect(x, y + ROW_HEIGHT - 1, x + width, y + ROW_HEIGHT, COL_SHADOW);
    }

    /** 文字在输入框内的左内缩量；与 GuiTextField 开启自带背景时的 x+4 一致。 */
    static int inputTextPadX() {
        return 4;
    }

    /** 文字在输入框内的上内缩量；与 GuiTextField 开启自带背景时的 (height-8)/2 一致。 */
    static int inputTextPadY(final int height) {
        return (height - 8) / 2;
    }

    /** 输入框：凹底加一圈边框，聚焦时边框变白、底色变亮。 */
    private static void input(final GuiTextField field, final int x, final int y, final int width,
            final int height, final boolean enabled) {
        field.x = x;
        field.y = y;
        field.width = width;
        final int border = !enabled ? COL_SHADOW : field.isFocused() ? COL_HIGHLIGHT : COL_FACE;
        final int fill = !enabled ? COL_FIELD : field.isFocused() ? COL_INPUT : COL_INPUT_IDLE;
        Gui.drawRect(x - 2, y - 2, x + width + 2, y + height + 2, COL_OUTLINE);
        Gui.drawRect(x - 1, y - 1, x + width + 1, y + height + 1, border);
        inset(x, y, width, height, fill);
        // GuiTextField 只在开启自带背景时才算居中（x+4 / y+(height-8)/2），关掉背景就退化成贴顶贴边。
        // 背景由上面自绘，所以绘制期间临时把坐标挪到它本该在的位置，画完还原——mouseClicked 用的是原始坐标。
        field.x = x + inputTextPadX();
        field.y = y + inputTextPadY(height);
        field.drawTextBox();
        field.x = x;
        field.y = y;
    }

    @SubscribeEvent
    public static void onGuiOpen(final GuiOpenEvent event) {
        if (event.getGui() != owner) {
            if (owner != null) {
                ((UploadSearchHolder) owner).ae2us$setUploadSearch(null);
            }
            if (search != null) {
                search.setFocused(false);
            }
            owner = null;
            uploadButton = null;
            undoButton = null;
            search = null;
            closeButton = null;
            prevButton = null;
            nextButton = null;
            backButton = null;
            locateButton = null;
            bindButton = null;
            mappingButton = null;
            toggleButton = null;
            saveButton = null;
            deleteButton = null;
            renameButton = null;
            nameField = null;
            renamingGroup = null;
            renamingMember = null;
            uidField = null;
            targetField = null;
            all = Collections.emptyList();
            filtered = Collections.emptyList();
            mappings = Collections.emptyList();
            locatingGroup = null;
            open = false;
            mappingOpen = false;
            pending = false;
            pendingAuto = false;
            patternReady = false;
            categoryUid = "";
            categoryTitle = "";
            editingUid = null;
            statusText = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onStatus(final ClientChatReceivedEvent event) {
        if (event.getType() != ChatType.GAME_INFO || Minecraft.getMinecraft().currentScreen != owner
                || owner == null) {
            return;
        }
        if (!(event.getMessage() instanceof TextComponentTranslation)) {
            statusText = null;
            return;
        }
        final String key = ((TextComponentTranslation) event.getMessage()).getKey();
        if (!key.startsWith("ae2us.upload.") && !key.startsWith("ae2us.undo.")
                && !key.startsWith("ae2us.locate.") && !key.startsWith("ae2us.mapping.")
                && !key.startsWith("ae2us.rename.")) {
            statusText = null;
            return;
        }
        statusText = I18n.format(key);
        statusUntil = System.currentTimeMillis() + 3000L;
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTooltip(final RenderTooltipEvent.Pre event) {
        if (open && Minecraft.getMinecraft().currentScreen == owner && !drawingOverlayTooltip) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInit(final GuiScreenEvent.InitGuiEvent.Post event) {
        final GuiScreen gui = event.getGui();
        if (!supports(gui)) {
            return;
        }
        if (owner != gui) {
            owner = gui;
            open = false;
            pending = false;
        }
        final GuiContainer container = (GuiContainer) gui;
        final int x = container.getGuiLeft() + 176;
        final int y = container.getGuiTop() + container.getYSize() - 142;
        uploadButton = new PatternButton(60001, x, y, "\u2191", "ae2us.upload.button");
        undoButton = new PatternButton(60002, x, y + 22, "\u2190", "ae2us.undo.button");
        undoButton.enabled = false;
        event.getButtonList().add(uploadButton);
        event.getButtonList().add(undoButton);
        PatternUploadNetwork.requestUndoState();
    }

    @SubscribeEvent
    public static void onButton(final GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getGui() != owner) {
            return;
        }
        if (event.getButton() == uploadButton) {
            playClickSound();
            pending = true;
            pendingAuto = !sneakDown();
            if (pendingAuto) {
                PatternUploadNetwork.autoUpload();
            } else {
                PatternUploadNetwork.requestTargets();
            }
            event.setCanceled(true);
        } else if (event.getButton() == undoButton) {
            playClickSound();
            undoButton.enabled = false;
            PatternUploadNetwork.undo();
            event.setCanceled(true);
        }
    }

    public static void setUndoAvailable(final boolean available) {
        if (undoButton != null && Minecraft.getMinecraft().currentScreen == owner) {
            undoButton.enabled = available;
        }
    }

    public static void updateSettings(final boolean enabled, final List<Mapping> saved) {
        autoEnabled = enabled;
        mappings = new ArrayList<>(saved);
        if (toggleButton != null) {
            toggleButton.displayString = autoEnabled ? "\u25A0" : "\u25A1";
        }
    }

    public static void finishAutoUpload() {
        pending = false;
        pendingAuto = false;
    }

    private static boolean sneakDown() {
        final int key = Minecraft.getMinecraft().gameSettings.keyBindSneak.getKeyCode();
        return key >= 0 ? Keyboard.isKeyDown(key) : Mouse.isButtonDown(key + 100);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyboard(final GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!open || event.getGui() != owner) {
            return;
        }
        final char character = Keyboard.getEventCharacter();
        final int key = Keyboard.getEventKey();
        if (Keyboard.getEventKeyState() || key == 0 && character >= ' ') {
            handleKey(event.getGui(), character, key);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyboardAfter(final GuiScreenEvent.KeyboardInputEvent.Post event) {
        if (open && event.getGui() == owner) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrawBefore(final GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (event.getGui() == owner && uploadButton != null) {
            final ContainerPatternEncoder container = (ContainerPatternEncoder) ((GuiContainer) owner).inventorySlots;
            final SlotRestrictedInput output = ((PatternEncoderAccessor) container).ae2us$getPatternOutputSlot();
            patternReady = output != null && !output.getStack().isEmpty()
                    && output.getStack().getItem() instanceof ICraftingPatternItem;
            categoryUid = patternReady ? PatternCategory.routingUid(output.getStack()) : "";
            categoryTitle = patternReady ? PatternCategory.routingTitle(output.getStack()) : "";
        }
    }

    public static void show(final List<TargetInfo> targets) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (!pending && open && mc.currentScreen == owner && supports(owner)) {
            refresh(targets);
            return;
        }
        if (!pending || mc.currentScreen != owner || !supports(owner)) {
            return;
        }
        pending = false;
        open = true;
        mappingOpen = false;
        all = new ArrayList<>(targets);
        filtered = all;
        locatingGroup = null;
        page = 0;
        lastSearchText = "";
        cursorTick = -1;
        final ContainerPatternEncoder container = (ContainerPatternEncoder) ((GuiContainer) owner).inventorySlots;
        final SlotRestrictedInput output = ((PatternEncoderAccessor) container).ae2us$getPatternOutputSlot();
        final boolean encoded = output != null && !output.getStack().isEmpty()
                && output.getStack().getItem() instanceof ICraftingPatternItem;
        categoryUid = encoded ? PatternCategory.routingUid(output.getStack()) : "";
        categoryTitle = encoded ? PatternCategory.routingTitle(output.getStack()) : "";
        final Mapping saved = mapping(categoryUid);
        final String suggestedName = saved == null && encoded ? PatternCategory.title(output.getStack())
                : saved == null ? categoryTitle : saved.name;
        final boolean tryAuto = pendingAuto && autoEnabled && !categoryUid.isEmpty()
                && (!PatternCategory.requiresMoldMapping(output.getStack()) || saved != null);
        pendingAuto = false;
        search = new GuiTextField(0, mc.fontRenderer, 0, 0, 80, 16);
        search.setMaxStringLength(256);
        search.setEnableBackgroundDrawing(false);
        search.setTextColor(0xFF18292D);
        search.setText(TextFormatting.getTextWithoutFormattingCodes(localizedName(suggestedName)));
        lastSearchText = search.getText();
        filter();
        if (tryAuto) {
            // 未保存映射时只能自动上传"过滤后恰好一行且完整同名"的组；显式映射不受行数限制。
            final TargetInfo target = saved == null
                    ? TargetInfo.findByName(filtered, suggestedName, true, PatternUploadClient::localizedName)
                    : TargetInfo.findByName(targets, suggestedName, false, PatternUploadClient::localizedName);
            if (target != null && target.freeSlots > 0) {
                closeOverlay();
                PatternUploadNetwork.upload(target.key);
                return;
            }
        }
        closeButton = new GuiButton(0, 0, 0, 20, 20, "x");
        prevButton = new GuiButton(1, 0, 0, 20, 20, "<");
        nextButton = new GuiButton(2, 0, 0, 20, 20, ">");
        backButton = new GuiButton(3, 0, 0, 20, 20, "<");
        locateButton = new GuiButton(4, 0, 0, 20, 20, "\u25CE");
        bindButton = new GuiButton(5, 0, 0, 20, 20, "+");
        mappingButton = new GuiButton(6, 0, 0, 20, 20, "\u2261");
        toggleButton = new GuiButton(7, 0, 0, 20, 20, autoEnabled ? "\u25A0" : "\u25A1");
        saveButton = new GuiButton(8, 0, 0, 20, 20, "\u221A");
        deleteButton = new GuiButton(9, 0, 0, 20, 20, "x");
        renameButton = new GuiButton(10, 0, 0, 20, 20, "\u270E");
        nameField = new GuiTextField(3, mc.fontRenderer, 0, 0, 80, 16);
        nameField.setMaxStringLength(32);
        nameField.setEnableBackgroundDrawing(false);
        renamingGroup = null;
        renamingMember = null;
        uidField = new GuiTextField(1, mc.fontRenderer, 0, 0, 80, 16);
        uidField.setMaxStringLength(128);
        uidField.setEnableBackgroundDrawing(false);
        targetField = new GuiTextField(2, mc.fontRenderer, 0, 0, 80, 16);
        targetField.setMaxStringLength(256);
        targetField.setEnableBackgroundDrawing(false);
        uidField.setText(categoryUid);
        targetField.setText("");
        editingUid = null;
        new Layout(owner).placeControls();
        ((UploadSearchHolder) owner).ae2us$setUploadSearch(search);
        search.setFocused(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDrawAfter(final GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!open || event.getGui() != owner) {
            return;
        }
        if (locatingGroup == null && !mappingOpen) {
            syncFilter();
        }
        final GuiScreen gui = event.getGui();
        final Layout layout = new Layout(gui);
        layout.placeControls();
        final int total = mappingOpen ? mappings.size()
                : locatingGroup == null ? filtered.size() : locatingGroup.members.size();
        page = Math.min(page, Math.max(0, (total - 1) / layout.rows));
        final int mouseX = event.getMouseX();
        final int mouseY = event.getMouseY();
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && cursorTick != mc.player.ticksExisted) {
            if (mappingOpen) {
                uidField.updateCursorCounter();
                targetField.updateCursorCounter();
            } else {
                search.updateCursorCounter();
                if (nameField != null) {
                    nameField.updateCursorCounter();
                }
            }
            cursorTick = mc.player.ticksExisted;
        }

        if (mappingOpen) {
            drawMappings(event, layout, total);
            return;
        }

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        panel(layout.x, layout.y, layout.width, layout.height);
        mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(
                I18n.format(locatingGroup == null ? "ae2us.upload.title" : "ae2us.locate.select"),
                layout.width - 112), layout.x + 10, layout.y + 8, COL_TEXT);
        groove(layout.x + 8, layout.y + 24, layout.width - 16);
        if (locatingGroup == null) {
            input(search, search.x, search.y, search.width, search.height, true);
        } else {
            mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(localizedName(locatingGroup.name),
                    layout.width - 20), layout.x + 10, layout.y + 33, COL_TEXT_DIM);
        }

        // 列表区是一整块凹槽，行是压在上面的凸起小块。
        final int rowX = layout.rowX();
        final int rowW = layout.rowW();
        inset(rowX, layout.listY - 2, rowW, layout.rows * ROW_HEIGHT + 4, COL_FIELD);

        final int start = page * layout.rows;
        if (total == 0) {
            mc.fontRenderer.drawString(I18n.format("ae2us.upload.empty"), rowX + 8,
                    layout.listY + 7, COL_TEXT_DIM);
        }
        final int iconY = (ROW_HEIGHT - Layout.BUTTON) / 2;
        boolean locateHovered = false;
        boolean bindHovered = false;
        boolean renameHovered = false;
        for (int i = 0; i < layout.rows && start + i < total; i++) {
            final int rowY = layout.rowY(i);
            final boolean hovered = mouseX >= rowX && mouseX < rowX + rowW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            final boolean uploadable = locatingGroup == null && patternReady
                    && filtered.get(start + i).freeSlots > 0;
            row(rowX, rowY, rowW,
                    hovered ? COL_ROW_HOVER : uploadable || locatingGroup != null ? COL_ROW : COL_ROW_DIM);
            renameButton.x = layout.renameX();
            renameButton.y = rowY + iconY;
            bindButton.x = layout.bindX();
            bindButton.y = rowY + iconY;
            locateButton.x = layout.locateX();
            locateButton.y = rowY + iconY;
            final int textX = rowX + 6;
            final int textW = layout.renameX() - 6 - textX;
            if (locatingGroup == null) {
                final TargetInfo target = filtered.get(start + i);
                if (renamingGroup == target && renamingMember == null) {
                    input(nameField, textX, rowY + 7, textW, 16, true);
                } else {
                    final String count = " \u00D7" + target.count;
                    final String name = mc.fontRenderer.trimStringToWidth(
                            TextFormatting.getTextWithoutFormattingCodes(localizedName(target.name)),
                            textW - mc.fontRenderer.getStringWidth(count)) + count;
                    final String detail = target.location.isEmpty()
                            ? I18n.format("ae2us.upload.free", target.freeSlots)
                            : target.location + "  " + I18n.format("ae2us.upload.free", target.freeSlots);
                    mc.fontRenderer.drawString(name, textX, rowY + 6, COL_TEXT);
                    mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(detail, textW),
                            textX, rowY + 16, COL_TEXT_DIM);
                }
                bindButton.enabled = !categoryUid.isEmpty();
                bindButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
                bindHovered |= bindButton.isMouseOver();
            } else {
                final MemberInfo member = locatingGroup.members.get(start + i);
                if (renamingMember == member) {
                    input(nameField, textX, rowY + 7, textW, 16, true);
                } else {
                    mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(member.location, textW),
                            textX, rowY + 6, COL_TEXT);
                    mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(
                                    I18n.format("ae2us.upload.free", member.freeSlots), textW),
                            textX, rowY + 16, COL_TEXT_DIM);
                }
            }
            renameButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
            renameHovered |= renameButton.isMouseOver();
            locateButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
            locateHovered |= locateButton.isMouseOver();
        }

        closeButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
        if (locatingGroup != null) {
            backButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
        } else {
            mappingButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
            toggleButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
        }
        prevButton.enabled = page > 0;
        nextButton.enabled = (page + 1) * layout.rows < total;
        prevButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
        nextButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
        final String pages = (page + 1) + "/" + Math.max(1, (total + layout.rows - 1) / layout.rows);
        mc.fontRenderer.drawString(pages, layout.x + (layout.width - mc.fontRenderer.getStringWidth(pages)) / 2,
                layout.footerY + 6, COL_TEXT_DIM);
        if (locateHovered) {
            drawTooltip("ae2us.locate.button", gui, mouseX, mouseY);
        } else if (renameHovered && renamingGroup == null) {
            drawTooltip("ae2us.rename.button", gui, mouseX, mouseY);
        } else if (bindHovered) {
            final String key = categoryUid.isEmpty() ? "ae2us.mapping.no_category"
                    : "ae2us.mapping.bind";
            drawTooltip(key,
                    gui, mouseX, mouseY);
        } else if (locatingGroup == null && mappingButton.isMouseOver()) {
            drawTooltip("ae2us.mapping.button", gui, mouseX, mouseY);
        } else if (locatingGroup == null && toggleButton.isMouseOver()) {
            drawTooltip(autoEnabled ? "ae2us.auto.on" : "ae2us.auto.off", gui, mouseX, mouseY);
        }
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    private static void drawMappings(final GuiScreenEvent.DrawScreenEvent.Post event, final Layout layout,
            final int total) {
        final Minecraft mc = Minecraft.getMinecraft();
        final int mouseX = event.getMouseX();
        final int mouseY = event.getMouseY();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        panel(layout.x, layout.y, layout.width, layout.height);
        mc.fontRenderer.drawString(I18n.format("ae2us.mapping.title"), layout.x + 10, layout.y + 8, COL_TEXT);
        groove(layout.x + 8, layout.y + 24, layout.width - 16);
        mc.fontRenderer.drawString(I18n.format("ae2us.mapping.category"), layout.x + 10, layout.y + 28, COL_TEXT_DIM);
        mc.fontRenderer.drawString(I18n.format("ae2us.mapping.target"), layout.x + 10, layout.y + 60, COL_TEXT_DIM);
        input(uidField, uidField.x, uidField.y, uidField.width, uidField.height, true);
        input(targetField, targetField.x, targetField.y, targetField.width, targetField.height, true);
        saveButton.enabled = !uidField.getText().trim().isEmpty() && !targetField.getText().trim().isEmpty();
        deleteButton.enabled = mapping(uidField.getText().trim()) != null;
        saveButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
        deleteButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());

        final int rowX = layout.rowX();
        final int rowW = layout.rowW();
        inset(rowX, layout.listY - 2, rowW, layout.rows * ROW_HEIGHT + 4, COL_FIELD);

        final int start = page * layout.rows;
        if (total == 0) {
            mc.fontRenderer.drawString(I18n.format("ae2us.mapping.empty"), rowX + 8,
                    layout.listY + 7, COL_TEXT_DIM);
        }
        for (int i = 0; i < layout.rows && start + i < total; i++) {
            final Mapping mapping = mappings.get(start + i);
            final int rowY = layout.rowY(i);
            final boolean hovered = mouseX >= rowX && mouseX < rowX + rowW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            row(rowX, rowY, rowW, hovered ? COL_ROW_HOVER : COL_ROW);
            final int textW = rowW - 12;
            mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(mapping.title + "  >  " + localizedName(mapping.name),
                    textW), rowX + 6, rowY + 6, COL_TEXT);
            mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(mapping.uid, textW),
                    rowX + 6, rowY + 16, COL_TEXT_DIM);
        }
        closeButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
        backButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
        prevButton.enabled = page > 0;
        nextButton.enabled = (page + 1) * layout.rows < total;
        prevButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
        nextButton.drawButton(mc, mouseX, mouseY, event.getRenderPartialTicks());
        final String pages = (page + 1) + "/" + Math.max(1, (total + layout.rows - 1) / layout.rows);
        mc.fontRenderer.drawString(pages, layout.x + (layout.width - mc.fontRenderer.getStringWidth(pages)) / 2,
                layout.footerY + 6, COL_TEXT_DIM);
        if (saveButton.isMouseOver()) {
            drawTooltip("ae2us.mapping.save", event.getGui(), mouseX, mouseY);
        } else if (deleteButton.isMouseOver()) {
            drawTooltip("ae2us.mapping.delete", event.getGui(), mouseX, mouseY);
        }
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    private static Mapping mapping(final String uid) {
        for (final Mapping entry : mappings) {
            if (entry.uid.equals(uid)) {
                return entry;
            }
        }
        return null;
    }

    private static void drawTooltip(final String key, final GuiScreen gui, final int mouseX, final int mouseY) {
        drawingOverlayTooltip = true;
        try {
            GuiUtils.drawHoveringText(Collections.singletonList(I18n.format(key)), mouseX, mouseY,
                    gui.width, gui.height, -1, Minecraft.getMinecraft().fontRenderer);
        } finally {
            drawingOverlayTooltip = false;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrawStatus(final GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui() != owner || statusText == null || System.currentTimeMillis() >= statusUntil) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        final List<String> lines = mc.fontRenderer.listFormattedStringToWidth(statusText, event.getGui().width - 16);
        final int firstY = event.getGui().height - 24 - (lines.size() - 1) * 10;
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        // 状态文字保持原位与生命周期不变，只在背后垫一块半透明凹槽，压住底下的界面提高可读性。
        int blockW = 0;
        for (final String line : lines) {
            blockW = Math.max(blockW, mc.fontRenderer.getStringWidth(line));
        }
        final int blockX = (event.getGui().width - blockW) / 2;
        final int blockY = firstY - 3;
        Gui.drawRect(blockX - 4, blockY, blockX + blockW + 4, blockY + lines.size() * 10 + 4, 0xC0000000);
        Gui.drawRect(blockX - 4, blockY, blockX + blockW + 4, blockY + 1, 0xFF555555);
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            mc.fontRenderer.drawStringWithShadow(line,
                    (event.getGui().width - mc.fontRenderer.getStringWidth(line)) / 2,
                    firstY + i * 10, 0xFFFFFF);
        }
        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(final GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!open || event.getGui() != owner) {
            return;
        }
        event.setCanceled(true);
        final int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            changePage(wheel > 0 ? -1 : 1, new Layout(owner));
            return;
        }
        if (Mouse.getEventButton() != 0 || !Mouse.getEventButtonState()) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        final int mouseX = Mouse.getEventX() * owner.width / mc.displayWidth;
        final int mouseY = owner.height - Mouse.getEventY() * owner.height / mc.displayHeight - 1;
        final Layout layout = new Layout(owner);
        layout.placeControls();
        if (mappingOpen) {
            handleMappingMouse(mc, layout, mouseX, mouseY);
            return;
        }
        final boolean inRows = layout.inRows(mouseX, mouseY);
        if (renamingGroup != null) {
            if (nameField != null && mouseX >= nameField.x && mouseX < nameField.x + nameField.width
                    && mouseY >= nameField.y && mouseY < nameField.y + nameField.height) {
                nameField.mouseClicked(mouseX, mouseY, 0);
                return;
            }
            if (inRows && layout.hit(mouseX, mouseY, layout.renameX())) {
                beginRename(page * layout.rows + layout.rowAt(mouseY));
                return;
            }
            cancelRename();
            if (inRows) {
                return;
            }
        }
        final boolean clickedSearch = locatingGroup == null && mouseX >= search.x && mouseX < search.x + search.width
                && mouseY >= search.y && mouseY < search.y + search.height;
        if (!clickedSearch) {
            search.setFocused(false);
        }
        if (closeButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            closeOverlay();
        } else if (locatingGroup == null && mappingButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            mappingOpen = true;
            page = 0;
            search.setFocused(false);
            uidField.setText(categoryUid);
            final Mapping current = mapping(categoryUid);
            targetField.setText(current == null ? "" : localizedName(current.name));
            editingUid = current == null ? null : current.uid;
            uidField.setFocused(!categoryUid.isEmpty());
        } else if (locatingGroup == null && toggleButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            autoEnabled = !autoEnabled;
            toggleButton.displayString = autoEnabled ? "\u25A0" : "\u25A1";
            PatternUploadNetwork.setAutoEnabled(autoEnabled);
        } else if (locatingGroup != null && backButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            locatingGroup = null;
            page = parentPage;
        } else if (prevButton.enabled && prevButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            changePage(-1, layout);
        } else if (nextButton.enabled && nextButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            changePage(1, layout);
        } else if (layout.inRows(mouseX, mouseY)) {
            final int index = page * layout.rows + layout.rowAt(mouseY);
            if (locatingGroup != null && index < locatingGroup.members.size()) {
                if (layout.hit(mouseX, mouseY, layout.renameX())) {
                    beginRename(locatingGroup, locatingGroup.members.get(index));
                } else {
                    playClickSound();
                    final MemberInfo member = locatingGroup.members.get(index);
                    LocateHighlightClient.highlight(member.dimension, member.pos);
                    PatternUploadNetwork.locate(locatingGroup.key, member);
                }
            } else if (locatingGroup == null && index < filtered.size()) {
                final TargetInfo target = filtered.get(index);
                if (layout.hit(mouseX, mouseY, layout.renameX())) {
                    beginRename(target, null);
                } else if (layout.hit(mouseX, mouseY, layout.bindX())) {
                    if (!categoryUid.isEmpty()) {
                        playClickSound();
                        PatternUploadNetwork.saveMapping("", categoryUid, categoryTitle, target.name);
                    }
                } else if (layout.hit(mouseX, mouseY, layout.locateX())) {
                    playClickSound();
                    if (target.members.size() == 1) {
                        final MemberInfo member = target.members.get(0);
                        LocateHighlightClient.highlight(member.dimension, member.pos);
                        PatternUploadNetwork.locate(target.key, member);
                    } else {
                        parentPage = page;
                        locatingGroup = target;
                        page = 0;
                    }
                } else if (patternReady && target.freeSlots > 0) {
                    playClickSound();
                    PatternUploadNetwork.upload(target.key);
                    closeOverlay();
                }
            }
        } else if (mouseX >= layout.x && mouseX < layout.x + layout.width
                && mouseY >= layout.y && mouseY < layout.y + layout.height) {
            if (clickedSearch) {
                playClickSound();
                search.mouseClicked(mouseX, mouseY, 0);
            }
        }
    }

    private static void handleMappingMouse(final Minecraft mc, final Layout layout,
            final int mouseX, final int mouseY) {
        if (closeButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            closeOverlay();
        } else if (backButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            mappingOpen = false;
            page = 0;
            uidField.setFocused(false);
            targetField.setFocused(false);
        } else if (saveButton.enabled && saveButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            final String uid = uidField.getText().trim();
            final Mapping current = mapping(editingUid);
            final String title = uid.equals(categoryUid) && !categoryTitle.isEmpty() ? categoryTitle
                    : current != null && current.uid.equals(uid) ? current.title : uid;
            PatternUploadNetwork.saveMapping(editingUid == null ? "" : editingUid, uid, title,
                    targetField.getText().trim());
            editingUid = uid;
        } else if (deleteButton.enabled && deleteButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            PatternUploadNetwork.removeMapping(uidField.getText().trim());
            editingUid = null;
            targetField.setText("");
        } else if (prevButton.enabled && prevButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            changePage(-1, layout);
        } else if (nextButton.enabled && nextButton.mousePressed(mc, mouseX, mouseY)) {
            playClickSound();
            changePage(1, layout);
        } else if (layout.inRows(mouseX, mouseY)) {
            final int index = page * layout.rows + layout.rowAt(mouseY);
            if (index < mappings.size()) {
                playClickSound();
                final Mapping selected = mappings.get(index);
                editingUid = selected.uid;
                uidField.setText(selected.uid);
                targetField.setText(localizedName(selected.name));
                uidField.setFocused(false);
                targetField.setFocused(true);
            }
        } else {
            uidField.mouseClicked(mouseX, mouseY, 0);
            targetField.mouseClicked(mouseX, mouseY, 0);
        }
    }

    public static boolean handleKey(final GuiScreen gui, final char character, final int key) {
        if (!open || gui != owner) {
            return false;
        }
        if (renamingGroup != null) {
            if (key == Keyboard.KEY_ESCAPE) {
                cancelRename();
            } else if (key == Keyboard.KEY_RETURN) {
                submitRename();
            } else {
                nameField.textboxKeyTyped(character, key);
            }
            return true;
        }
        if (mappingOpen) {
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_E
                    && !uidField.isFocused() && !targetField.isFocused()) {
                mappingOpen = false;
                page = 0;
                uidField.setFocused(false);
                targetField.setFocused(false);
            } else if (key == Keyboard.KEY_TAB) {
                final boolean focusTarget = uidField.isFocused();
                uidField.setFocused(!focusTarget);
                targetField.setFocused(focusTarget);
            } else {
                uidField.textboxKeyTyped(character, key);
                targetField.textboxKeyTyped(character, key);
            }
        } else if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_E && !search.isFocused()) {
            closeOverlay();
        } else if (locatingGroup == null) {
            search.textboxKeyTyped(character, key);
            syncFilter();
        }
        return true;
    }

    private static void closeOverlay() {
        open = false;
        mappingOpen = false;
        locatingGroup = null;
        cancelRename();
        if (owner != null) {
            ((UploadSearchHolder) owner).ae2us$setUploadSearch(null);
        }
        if (search != null) {
            search.setFocused(false);
        }
        if (uidField != null) {
            uidField.setFocused(false);
            targetField.setFocused(false);
        }
    }

    /** 列表已经打开时收到新数据（改名成功后的刷新）：就地更新，保留搜索词与页码。 */
    private static void refresh(final List<TargetInfo> targets) {
        final int keep = page;
        all = new ArrayList<>(targets);
        locatingGroup = null;
        cancelRename();
        filter();
        page = keep;
    }

    private static void beginRename(final int rowIndex) {
        if (locatingGroup != null) {
            if (rowIndex < locatingGroup.members.size()) {
                beginRename(locatingGroup, locatingGroup.members.get(rowIndex));
            }
        } else if (rowIndex < filtered.size()) {
            beginRename(filtered.get(rowIndex), null);
        }
    }

    private static void beginRename(final TargetInfo group, final MemberInfo member) {
        renamingGroup = group;
        renamingMember = member;
        final boolean custom = member == null ? anyCustom(group) : member.custom;
        nameField.setText(custom ? group.name : "");
        nameField.setFocused(true);
        search.setFocused(false);
        playClickSound();
    }

    private static boolean anyCustom(final TargetInfo group) {
        for (final MemberInfo entry : group.members) {
            if (entry.custom) {
                return true;
            }
        }
        return false;
    }

    private static void cancelRename() {
        renamingGroup = null;
        renamingMember = null;
        if (nameField != null) {
            nameField.setFocused(false);
        }
    }

    private static void submitRename() {
        final TargetInfo group = renamingGroup;
        final MemberInfo member = renamingMember;
        final String value = nameField.getText().trim();
        cancelRename();
        if (group == null) {
            return;
        }
        final boolean custom = member == null ? anyCustom(group) : member.custom;
        if (value.equals(custom ? group.name : "")) {
            return;
        }
        if (member != null) {
            PatternUploadNetwork.renameMember(member.dimension, member.pos, member.side, value);
        } else {
            PatternUploadNetwork.renameGroup(group.key, value);
        }
    }

    private static void playClickSound() {
        Minecraft.getMinecraft().getSoundHandler().playSound(
                PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static void syncFilter() {
        final String current = search.getText();
        if (!current.equals(lastSearchText)) {
            lastSearchText = current;
            filter();
        }
    }

    private static void filter() {
        final String query = search.getText().trim();
        final List<TargetInfo> matches = new ArrayList<>();
        for (final TargetInfo target : all) {
            if (PinyinSearch.matches(localizedName(target.name), query)) {
                matches.add(target);
            }
        }
        filtered = matches;
        page = 0;
    }

    private static void changePage(final int direction, final Layout layout) {
        final int total = mappingOpen ? mappings.size()
                : locatingGroup == null ? filtered.size() : locatingGroup.members.size();
        page = Math.max(0, Math.min(page + direction,
                Math.max(0, (total - 1) / layout.rows)));
    }

    private static boolean supports(final GuiScreen gui) {
        return (gui instanceof GuiPatternTerm || gui instanceof GuiExpandedProcessingPatternTerm)
                && ((GuiContainer) gui).inventorySlots instanceof ContainerPatternEncoder;
    }

    /** 覆盖层打开期间该屏幕的鼠标输入全部归覆盖层；HEI 的 handleMouseEvent 守卫读这里（见 InputHandlerMixin）。 */
    public static boolean blocksHeiMouseInput(final GuiScreen gui) {
        return open && gui == owner;
    }

    private static String localizedName(final String name) {
        if (I18n.hasKey(name)) {
            return I18n.format(name);
        }
        return I18n.hasKey(name + ".name") ? I18n.format(name + ".name") : name;
    }

    /**
     * 布局几何快照 {x, y, width, height, rows, listY, footerY, renameX, bindX, locateX, textWidth}。
     * 供自检在无 GUI 环境下验算边界与重叠，不在游戏逻辑里使用。
     */
    static int[] geometry(final int screenWidth, final int screenHeight, final boolean mapping) {
        final boolean wasMapping = mappingOpen;
        mappingOpen = mapping;
        try {
            final Layout layout = new Layout(screenWidth, screenHeight);
            return new int[]{layout.x, layout.y, layout.width, layout.height, layout.rows,
                    layout.listY, layout.footerY, layout.renameX(), layout.bindX(), layout.locateX(),
                    layout.renameX() - 6 - (layout.rowX() + 6)};
        } finally {
            mappingOpen = wasMapping;
        }
    }

    private static final class Layout {
        /** 标题区（含搜索框或组名）；列表从它下面开始。 */
        private static final int MAIN_HEAD = 54;
        private static final int MAPPING_HEAD = 96;
        /** 列表下方：分页按钮 + 页码。 */
        private static final int FOOTER = 26;
        /** 原版按钮固定 20x20，纹理才不会被截断。 */
        private static final int BUTTON = 20;
        private static final int ICON_PITCH = 22;

        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int rows;
        private final int listY;
        private final int footerY;

        private Layout(final GuiScreen gui) {
            this(gui.width, gui.height);
        }

        /** 只依赖屏幕宽高，自检可以脱离 GUI 环境直接验算几何。 */
        Layout(final int screenWidth, final int screenHeight) {
            final int head = mappingOpen ? MAPPING_HEAD : MAIN_HEAD;
            width = Math.min(mappingOpen ? MAPPING_WIDTH : MAIN_WIDTH, screenWidth - SCREEN_MARGIN * 2);
            final int usable = Math.max(ROW_HEIGHT, screenHeight - SCREEN_MARGIN * 2 - head - FOOTER);
            rows = Math.max(1, Math.min(6, usable / ROW_HEIGHT));
            height = Math.min(head + rows * ROW_HEIGHT + FOOTER, screenHeight - SCREEN_MARGIN * 2);
            x = (screenWidth - width) / 2;
            y = (screenHeight - height) / 2;
            listY = y + head;
            // 面板被屏幕高度压扁时，页脚跟着上移，始终贴着面板底边内侧。
            footerY = Math.min(listY + rows * ROW_HEIGHT + 4, y + height - FOOTER + 3);
        }

        /** 列表行左边缘（凹槽内缩 6px）。 */
        private int rowX() {
            return x + 6;
        }

        private int rowW() {
            return width - 12;
        }

        /** 第 index 行的上边缘。 */
        private int rowY(final int index) {
            return listY + index * ROW_HEIGHT;
        }

        /** 鼠标所在行号；调用前须先确认落在列表范围内。 */
        private int rowAt(final int mouseY) {
            return (mouseY - listY) / ROW_HEIGHT;
        }

        private boolean inRows(final int mouseX, final int mouseY) {
            return mouseX >= rowX() && mouseX < rowX() + rowW()
                    && mouseY >= listY && mouseY < listY + rows * ROW_HEIGHT;
        }

        /** 行内三个操作图标：从右往左依次 定位 / 绑定 / 改名，定位贴齐行右缘内缩 4px。 */
        private int renameX() {
            return rowX() + rowW() - 4 - BUTTON - ICON_PITCH * 2;
        }

        private int bindX() {
            return renameX() + ICON_PITCH;
        }

        private int locateX() {
            return bindX() + ICON_PITCH;
        }

        private boolean hit(final int mouseX, final int mouseY, final int buttonX) {
            return mouseX >= buttonX && mouseX < buttonX + BUTTON
                    && mouseY >= listY && mouseY < listY + rows * ROW_HEIGHT;
        }

        private void placeControls() {
            search.x = x + 11;
            search.y = y + 32;
            search.width = width - 22;
            closeButton.x = x + width - 24;
            closeButton.y = y + 4;
            backButton.x = x + width - 46;
            backButton.y = y + 4;
            mappingButton.x = x + width - 46;
            mappingButton.y = y + 4;
            toggleButton.x = x + width - 68;
            toggleButton.y = y + 4;
            prevButton.x = x + width / 2 - 55;
            prevButton.y = footerY;
            nextButton.x = x + width / 2 + 35;
            nextButton.y = footerY;
            uidField.x = x + 11;
            uidField.y = y + 40;
            uidField.width = width - 22;
            targetField.x = x + 11;
            targetField.y = y + 72;
            targetField.width = width - 74;
            saveButton.x = x + width - 58;
            saveButton.y = y + 70;
            deleteButton.x = x + width - 34;
            deleteButton.y = y + 70;
        }
    }

    private static final class PatternButton extends GuiButton implements ITooltip {
        private final String tooltip;

        private PatternButton(final int id, final int x, final int y, final String symbol, final String tooltip) {
            super(id, x, y, 16, 16, symbol);
            this.tooltip = tooltip;
        }

        @Override
        public String getMessage() {
            return I18n.format(tooltip);
        }

        @Override
        public int xPos() {
            return x;
        }

        @Override
        public int yPos() {
            return y;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }
    }
}
