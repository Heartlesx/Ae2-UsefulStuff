package com.extrarawstyle.ae2us.client;

import appeng.client.gui.AEBaseGui;
import appeng.container.interfaces.IJEIGhostIngredients;
import com.extrarawstyle.ae2us.maintainer.ContainerStockMaintainer;
import com.extrarawstyle.ae2us.maintainer.MaintainerAmount;
import com.extrarawstyle.ae2us.maintainer.MaintainerBook;
import com.extrarawstyle.ae2us.maintainer.MaintainerRule;
import com.extrarawstyle.ae2us.maintainer.MaintainerStatus;
import com.extrarawstyle.ae2us.maintainer.StockMaintainerNetwork;
import com.extrarawstyle.ae2us.maintainer.TileStockMaintainer;
import com.extrarawstyle.ae2us.maintainer.fluid.FluidMaintainerBridge;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Optional;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ME库存维持器界面：每页 8 条、共 4 页。
 * 每行显示模式、标记、当前库存、阈值、单次批量、状态与启用开关。
 *
 * 模式格子是纯客户端的“待定模式”：点它只改本行的解释方式，不提交任何目标；
 * 真正的目标更新只在拖入/点击标记时带着当前模式一起发给服务端。
 * 数值输入沿用报警器界面的交互：回车提交，翻页/关闭前提交合法编辑，非法编辑保留原值。
 */
@Optional.Interface(iface = "appeng.container.interfaces.IJEIGhostIngredients", modid = "jei")
public final class GuiStockMaintainer extends AEBaseGui implements IJEIGhostIngredients {
    private static final int ROWS = MaintainerBook.ROWS_PER_PAGE;
    private static final int PAGES = MaintainerBook.PAGES;
    private static final int TOP = 40, ROW_H = 17;
    private static final int SLOT_X = 32, MODE_X = 10, MODE_W = 20;
    private static final int NAME_X = 54, NAME_W = 62;
    private static final int COUNT_X = 122;
    private static final int THRESHOLD_X = 168, FIELD_W = 46;
    private static final int BATCH_X = 218;
    private static final int STATE_X = 268;
    private static final int TOGGLE_X = 330;
    /** 该行有未提交的数值改动时，状态格临时变成「确定」按钮。 */
    private static final int CONFIRM_W = 58;
    /** 每行第二个开关：本条的材料不够整批时减量下单。窗口因此加宽到 376。 */
    private static final int SMART_TOGGLE_X = 352;

    private final ContainerStockMaintainer container;
    private final GuiTextField[] thresholds = new GuiTextField[ROWS];
    private final GuiTextField[] batches = new GuiTextField[ROWS];
    private final String[] committedThresholds = new String[ROWS];
    private final String[] committedBatches = new String[ROWS];
    /** 每行的待定模式：仅客户端，不随目标一起同步。 */
    private final int[] pendingMode = new int[ROWS];
    private String status = "";

    public GuiStockMaintainer(final ContainerStockMaintainer container) {
        super(container);
        this.container = container;
        this.xSize = 376;
        this.ySize = 272;
    }

    private static String tr(final String key, final Object... args) {
        return I18n.format("ae2us.maintainer." + key, args);
    }

    @Override
    public void initGui() {
        super.initGui();
        for (int i = 0; i < ROWS; i++) {
            final int y = TOP + i * ROW_H + 4;
            this.thresholds[i] = this.field(this.thresholds[i], i, THRESHOLD_X, y);
            this.batches[i] = this.field(this.batches[i], 100 + i, BATCH_X, y);
        }
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(0, this.guiLeft + 272, this.guiTop + 178, 16, 14, "<"));
        this.buttonList.add(new GuiButton(1, this.guiLeft + 318, this.guiTop + 178, 16, 14, ">"));
        Keyboard.enableRepeatEvents(true);
        this.syncFromState();
    }

    private GuiTextField field(final GuiTextField existing, final int id, final int x, final int y) {
        final GuiTextField field = existing == null
                ? new GuiTextField(id, this.fontRenderer, this.guiLeft + x, this.guiTop + y, FIELD_W, 14)
                : existing;
        field.x = this.guiLeft + x;
        field.y = this.guiTop + y;
        field.setMaxStringLength(24);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(0x304449);
        return field;
    }

    private void send(final int operation, final int index, final String text, final ItemStack stack) {
        StockMaintainerNetwork.action(this.container.windowId, operation, index, text, stack);
    }

    private void sendPage(final int page) {
        this.send(StockMaintainerNetwork.PAGE, MathHelper.clamp(page, 0, PAGES - 1), "", ItemStack.EMPTY);
    }

    // ---------------------------------------------------------------- 数值提交

    private void commitThreshold(final int row) {
        final GuiTextField field = this.thresholds[row];
        if (field.getText().equals(this.committedThresholds[row])) { return; }
        final Long value = MaintainerAmount.parse(field.getText());
        if (value == null) {
            this.status = tr("invalid");
            field.setText(this.committedThresholds[row] == null ? "" : this.committedThresholds[row]);
            return;
        }
        this.committedThresholds[row] = MaintainerAmount.format(value);
        this.send(StockMaintainerNetwork.SET_THRESHOLD, this.container.page * ROWS + row,
                this.committedThresholds[row], ItemStack.EMPTY);
        this.status = "";
    }

    private void commitBatch(final int row) {
        final GuiTextField field = this.batches[row];
        if (field.getText().equals(this.committedBatches[row])) { return; }
        final Long value = MaintainerAmount.parse(field.getText());
        if (value == null) {
            this.status = tr("invalid");
            field.setText(this.committedBatches[row] == null ? "" : this.committedBatches[row]);
            return;
        }
        this.committedBatches[row] = MaintainerAmount.format(value);
        this.send(StockMaintainerNetwork.SET_BATCH, this.container.page * ROWS + row,
                this.committedBatches[row], ItemStack.EMPTY);
        this.status = "";
    }

    private void commitAll() {
        for (int i = 0; i < ROWS; i++) {
            this.commitThreshold(i);
            this.commitBatch(i);
        }
    }

    /**
     * 该行两个输入框里是否有还没提交的改动。
     * 有的话状态格会临时让位给「确定」按钮——玩家输完数字直接点它就行，
     * 不必记得按回车，也不会因为点了别处而看到数值被悄悄改回去。
     */
    private boolean dirty(final int row) {
        final GuiTextField threshold = this.thresholds[row];
        final GuiTextField batch = this.batches[row];
        if (threshold == null || batch == null) { return false; }
        return !threshold.getText().equals(this.committedThresholds[row])
                || !batch.getText().equals(this.committedBatches[row]);
    }

    /** 点「确定」：提交本行两个数值，然后收起焦点。非法值仍按原逻辑回滚并提示。 */
    private void confirmRow(final int row) {
        this.commitThreshold(row);
        this.commitBatch(row);
        this.thresholds[row].setFocused(false);
        this.batches[row].setFocused(false);
    }

    // ---------------------------------------------------------------- 标记

    /** 物品模式：把物品本身当作目标。 */
    private void markItem(final int index, final ItemStack stack) {
        if (stack.isEmpty()) { return; }
        this.commitAll();
        this.send(StockMaintainerNetwork.SET_TARGET_ITEM, index, "", stack);
        this.status = "";
    }

    /**
     * 流体模式：从容器里只读取流体类型，不排空物品。
     * 容器里有多种流体、或读取不到时，提示改从 HEI 拖入指定流体，不擅自取第一种。
     */
    private void markFluidFromContainer(final int index, final ItemStack container) {
        final FluidStack fluid = singleFluidOf(container);
        if (fluid == null) {
            this.status = tr("fluid_ambiguous");
            return;
        }
        this.commitAll();
        this.markFluid(index, fluid);
    }

    private void markFluid(final int index, final Object raw) {
        final Object parsed = FluidMaintainerBridge.fluidOf(raw);
        if (parsed == null) {
            this.status = tr("fluid_unavailable");
            return;
        }
        final NBTTagCompound tag = new NBTTagCompound();
        if (!FluidMaintainerBridge.writeTarget(parsed, tag)) {
            this.status = tr("fluid_unavailable");
            return;
        }
        this.send(StockMaintainerNetwork.SET_TARGET_FLUID, index, tag.toString(), ItemStack.EMPTY);
        this.status = "";
    }

    private static FluidStack singleFluidOf(final ItemStack stack) {
        if (stack.isEmpty() || !stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            return null;
        }
        final IFluidHandlerItem handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (handler == null) { return null; }
        FluidStack found = null;
        for (final IFluidTankProperties tank : handler.getTankProperties()) {
            final FluidStack contents = tank.getContents();
            if (contents == null || contents.getFluid() == null || contents.amount <= 0) { continue; }
            if (found != null) { return null; }
            found = contents;
        }
        return found;
    }

    // ---------------------------------------------------------------- 交互

    @Override
    protected void actionPerformed(final GuiButton button) {
        this.commitAll();
        this.sendPage(this.container.page + (button.id == 0 ? -1 : 1));
    }

    @Override
    protected void mouseClicked(final int x, final int y, final int button) throws IOException {
        final StockMaintainerNetwork.State state = this.container.state;
        for (int i = 0; i < ROWS; i++) {
            final int rowY = TOP + i * ROW_H;
            final int index = this.container.page * ROWS + i;
            if (this.isPointInRegion(MODE_X, rowY + 4, MODE_W, 9, x, y)) {
                // 只切换本行解释方式，不提交目标。
                if (state == null || !state.configured[i]) {
                    this.pendingMode[i] = this.pendingMode[i] == MaintainerRule.MODE_FLUID
                            ? MaintainerRule.MODE_ITEM : MaintainerRule.MODE_FLUID;
                    this.status = "";
                } else {
                    this.status = tr("mode_locked");
                }
                return;
            }
            if (this.isPointInRegion(SLOT_X, rowY, 16, 16, x, y)) {
                if (button == 1) {
                    this.commitAll();
                    this.send(StockMaintainerNetwork.CLEAR, index, "", ItemStack.EMPTY);
                    this.status = "";
                } else if (button == 0) {
                    final ItemStack held = this.mc.player.inventory.getItemStack();
                    if (held.isEmpty()) { return; }
                    if (this.modeOf(state, i) == MaintainerRule.MODE_FLUID) {
                        this.markFluidFromContainer(index, held);
                    } else {
                        this.markItem(index, held);
                    }
                }
                return;
            }
            if (state != null && state.configured[i] && this.isPointInRegion(TOGGLE_X, rowY + 4, 16, 9, x, y)) {
                this.commitAll();
                this.send(StockMaintainerNetwork.SET_ENABLED, index, state.enabled[i] ? "0" : "1", ItemStack.EMPTY);
                return;
            }
            if (state != null && state.configured[i]
                    && this.isPointInRegion(SMART_TOGGLE_X, rowY + 4, 16, 9, x, y)) {
                this.commitAll();
                this.send(StockMaintainerNetwork.SET_SMART_PART_BATCH, index,
                        state.smartPartBatches[i] ? "0" : "1", ItemStack.EMPTY);
                return;
            }            // 状态格被换成「确定」按钮时点它：只提交本行，不牵动别的行。
            if (this.dirty(i) && this.isPointInRegion(STATE_X, rowY + 4, CONFIRM_W, 9, x, y)) {
                this.confirmRow(i);
                return;
            }
        }
        for (final GuiTextField field : this.thresholds) { field.mouseClicked(x, y, button); }
        for (final GuiTextField field : this.batches) { field.mouseClicked(x, y, button); }
        super.mouseClicked(x, y, button);
    }

    /** 已配置的行用服务端模式，未配置的行用客户端待定模式。 */
    private int modeOf(final StockMaintainerNetwork.State state, final int row) {
        if (state != null && state.configured[row]) { return state.modes[row]; }
        return this.pendingMode[row];
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        final int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && this.container.state != null) {
            this.commitAll();
            this.sendPage(this.container.page + (wheel > 0 ? -1 : 1));
        }
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        for (int i = 0; i < ROWS; i++) {
            if (this.keyFor(this.thresholds[i], this.committedThresholds[i], i, true, character, key)) { return; }
            if (this.keyFor(this.batches[i], this.committedBatches[i], i, false, character, key)) { return; }
        }
        super.keyTyped(character, key);
    }

    private boolean keyFor(final GuiTextField field, final String committed, final int row,
                           final boolean threshold, final char character, final int key) {
        if (!field.isFocused()) { return false; }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (threshold) { this.commitThreshold(row); } else { this.commitBatch(row); }
            field.setFocused(false);
            return true;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            field.setText(committed == null ? "" : committed);
            field.setFocused(false);
            return true;
        }
        return field.textboxKeyTyped(character, key);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (final GuiTextField field : this.thresholds) { field.updateCursorCounter(); }
        for (final GuiTextField field : this.batches) { field.updateCursorCounter(); }
        this.syncFromState();
    }

    /**
     * 服务器状态到达时校准基准值与待定模式。
     * 只在校准值真的变了时才写回输入框：否则未提交的编辑会被每帧覆盖掉。
     */
    private void syncFromState() {
        final StockMaintainerNetwork.State state = this.container.state;
        if (state == null) { return; }
        for (int i = 0; i < ROWS; i++) {
            final String threshold = state.thresholds[i] > 0 ? MaintainerAmount.format(state.thresholds[i]) : "";
            final String batch = state.batches[i] > 0 ? MaintainerAmount.format(state.batches[i]) : "";
            if (!threshold.equals(this.committedThresholds[i])) {
                this.committedThresholds[i] = threshold;
                if (!this.thresholds[i].isFocused()) { this.thresholds[i].setText(threshold); }
            }
            if (!batch.equals(this.committedBatches[i])) {
                this.committedBatches[i] = batch;
                if (!this.batches[i].isFocused()) { this.batches[i].setText(batch); }
            }
            this.pendingMode[i] = state.configured[i] ? state.modes[i] : this.pendingMode[i];
        }
    }

    @Override
    public void onGuiClosed() {
        this.commitAll();
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    public void drawBG(final int x, final int y, final int mouseX, final int mouseY) {
        drawRect(x, y, x + this.xSize, y + this.ySize, 0xff344b4d);
        drawRect(x + 2, y + 2, x + 374, y + 270, 0xffd9e2dc);
        drawRect(x + 2, y + 2, x + 374, y + 25, 0xff263a40);
        drawRect(x + 2, y + 2, x + 5, y + 25, 0xff3cd4b2);
        this.drawHeaderSignal(x, y);
        drawRect(x + 6, y + 28, x + 370, y + 39, 0xffb6c8c0);
        final StockMaintainerNetwork.State state = this.container.state;
        for (int i = 0; i < ROWS; i++) {
            final int rowY = y + TOP + i * ROW_H;
            final boolean configured = state != null && state.configured[i];
            final boolean warning = state != null && configured && this.warning(state.statuses[i]);
            drawRect(x + 6, rowY, x + 370, rowY + 16,
                    warning ? 0xfff3dedb : i % 2 == 0 ? 0xffe6eee8 : 0xffedf3ed);
            if (configured) {
                drawRect(x + 6, rowY, x + 9, rowY + 16, warning ? 0xffdc544e : 0xff4eb499);
            }
            this.drawModeChip(x, rowY, this.modeOf(state, i));
            drawRect(x + SLOT_X, rowY, x + SLOT_X + 16, rowY + 16, 0xffafc1ba);
            drawRect(x + SLOT_X + 1, rowY + 1, x + SLOT_X + 15, rowY + 15, 0xfff4f7f1);
            this.drawFieldBox(x, rowY, THRESHOLD_X, warning);
            this.drawFieldBox(x, rowY, BATCH_X, warning);
            this.drawConfirm(x, rowY, this.dirty(i));
            this.drawToggle(x, rowY, state, i);
            this.drawSmartToggle(x, rowY, state, i);
        }
        for (final Slot slot : this.inventorySlots.inventorySlots) {
            drawRect(x + slot.xPos, y + slot.yPos, x + slot.xPos + 16, y + slot.yPos + 16, 0xffb9c8c2);
            drawRect(x + slot.xPos + 1, y + slot.yPos + 1, x + slot.xPos + 15, y + slot.yPos + 15, 0xffedf2ec);
        }
        RenderHelper.enableGUIStandardItemLighting();
        if (state != null) {
            for (int i = 0; i < ROWS; i++) {
                final ItemStack icon = state.icons[i];
                if (!icon.isEmpty()) {
                    this.itemRender.renderItemAndEffectIntoGUI(icon, x + SLOT_X, y + TOP + i * ROW_H);
                }
            }
        }
        RenderHelper.disableStandardItemLighting();
        for (final GuiTextField field : this.thresholds) { field.drawTextBox(); }
        for (final GuiTextField field : this.batches) { field.drawTextBox(); }
    }

    private void drawFieldBox(final int x, final int rowY, final int fieldX, final boolean warning) {
        drawRect(x + fieldX - 1, rowY + 1, x + fieldX + FIELD_W, rowY + 15,
                warning ? 0xffc7a8a3 : 0xffa7bdb4);
        drawRect(x + fieldX, rowY + 2, x + fieldX + FIELD_W - 1, rowY + 14, 0xfffbfffb);
    }

    private void drawConfirm(final int x, final int rowY, final boolean dirty) {
        if (!dirty) { return; }
        drawRect(x + STATE_X, rowY + 1, x + STATE_X + CONFIRM_W, rowY + 16, 0xff2f7a68);
        drawRect(x + STATE_X + 1, rowY + 2, x + STATE_X + CONFIRM_W - 1, rowY + 15, 0xff3f9c85);
    }

    private void drawModeChip(final int x, final int rowY, final int mode) {
        final boolean fluid = mode == MaintainerRule.MODE_FLUID;
        drawRect(x + MODE_X, rowY + 4, x + MODE_X + MODE_W, rowY + 13, fluid ? 0xff3f7f9c : 0xff6d8b80);
        drawRect(x + MODE_X + 1, rowY + 5, x + MODE_X + MODE_W - 1, rowY + 12, fluid ? 0xffa8d8ef : 0xffd5e6dd);
    }

    private void drawHeaderSignal(final int x, final int y) {
        final StockMaintainerNetwork.State state = this.container.state;
        final boolean online = state != null && state.status == TileStockMaintainer.STATUS_ACTIVE;
        int working = 0;
        if (state != null) {
            for (int i = 0; i < ROWS; i++) {
                if (state.configured[i] && MaintainerStatus.holdsOrder(state.statuses[i])) { working++; }
            }
        }
        drawRect(x + 275, y + 6, x + 344, y + 21, online && working > 0 ? 0xff345f58 : 0xff40545a);
        drawRect(x + 279, y + 10, x + 284, y + 16, online && working > 0 ? 0xff3cd4b2 : 0xff607579);
    }

    private void drawToggle(final int x, final int rowY, final StockMaintainerNetwork.State state, final int i) {
        if (state == null || !state.configured[i]) { return; }
        final boolean on = state.enabled[i];
        drawRect(x + TOGGLE_X, rowY + 4, x + TOGGLE_X + 16, rowY + 13, on ? 0xff2f7a68 : 0xff9aaca5);
        drawRect(x + TOGGLE_X + (on ? 8 : 1), rowY + 5, x + TOGGLE_X + (on ? 15 : 8), rowY + 12,
                on ? 0xffc8f2e2 : 0xffe3ebe5);
    }

    /** 每行第二个开关：本条的材料不够整批时减量下单。与左边启用开关同款画法。 */
    private void drawSmartToggle(final int x, final int rowY, final StockMaintainerNetwork.State state, final int i) {
        if (state == null || !state.configured[i]) { return; }
        final boolean on = state.smartPartBatches[i];
        drawRect(x + SMART_TOGGLE_X, rowY + 4, x + SMART_TOGGLE_X + 16, rowY + 13,
                on ? 0xff3f7f9c : 0xff9aaca5);
        drawRect(x + SMART_TOGGLE_X + (on ? 8 : 1), rowY + 5, x + SMART_TOGGLE_X + (on ? 15 : 8), rowY + 12,
                on ? 0xffa8d8ef : 0xffe3ebe5);
    }

    @Override
    public void drawFG(final int x, final int y, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(tr("title"), 200), 11, 9, 0xf1f5ef);
        final StockMaintainerNetwork.State state = this.container.state;
        if (state != null) {
            final String label = state.status == TileStockMaintainer.STATUS_ACTIVE ? tr("active")
                    : state.status == TileStockMaintainer.STATUS_NO_CHANNEL ? tr("no_channel") : tr("no_power");
            this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(label, 43), 288, 9, 0xf1f5ef);
        }
        this.fontRenderer.drawString((this.container.page + 1) + " / " + PAGES, 296, 181, 0x324b4d);
        if (state != null) {
            for (int i = 0; i < ROWS; i++) { this.drawRowText(state, i); }
        }
        for (int i = 0; i < ROWS; i++) { this.drawConfirmText(i); }
        this.fontRenderer.drawString(tr("mark"), 54, 30, 0x355050);
        this.fontRenderer.drawString(tr("current"), 120, 30, 0x355050);
        this.fontRenderer.drawString(tr("threshold"), 168, 30, 0x355050);
        this.fontRenderer.drawString(tr("batch"), 218, 30, 0x355050);
        this.fontRenderer.drawString(tr("state"), 268, 30, 0x355050);
        final String footer = this.status.isEmpty() ? I18n.format("container.inventory") : this.status;
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(footer, 150), 58, 181,
                this.status.isEmpty() ? 0x3e5555 : 0xaa3b37);
    }

    private void drawRowText(final StockMaintainerNetwork.State state, final int i) {
        final int rowY = TOP + i * ROW_H + 4;
        final int mode = this.modeOf(state, i);
        if (!state.configured[i]) {
            // 未配置行只画模式文字，目标名称区留空。
            this.fontRenderer.drawString(mode == MaintainerRule.MODE_FLUID ? tr("fluid") : tr("item_mode"),
                    MODE_X, rowY, 0x7c918c);
            return;
        }
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(state.labels[i], NAME_W), NAME_X, rowY, 0x2d4144);
        final String count = state.countsValid[i]
                ? MaintainerAmount.compact(state.counts[i]) + (mode == MaintainerRule.MODE_FLUID ? "mB" : "")
                : "-";
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(count, 44), COUNT_X, rowY,
                state.countsValid[i] ? 0x2d6260 : 0x7c918c);
        // 状态格让位给「确定」按钮时，状态文字不画（按钮点击后状态自然回来）。
        if (this.dirty(i)) { return; }
        // 减量下的单要看得见实下多少；整批就照旧只写「合成中」，不添噪声。
        final boolean partial = MaintainerStatus.holdsOrder(state.statuses[i])
                && state.orderBatches[i] > 0 && state.orderBatches[i] < state.batches[i];
        final String stateText = partial
                ? this.stateLabel(state.statuses[i]) + " " + MaintainerAmount.compact(state.orderBatches[i])
                : this.stateLabel(state.statuses[i]);
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(stateText, 60),
                STATE_X, rowY, this.warning(state.statuses[i]) ? 0xad3e3b : 0x2d6260);
    }

    /** 「确定」按钮上的文字。drawFG 里画，drawBG 阶段不画文字。 */
    private void drawConfirmText(final int i) {
        if (!this.dirty(i)) { return; }
        final String label = tr("confirm");
        final int textX = STATE_X + (CONFIRM_W + 34 - this.fontRenderer.getStringWidth(label)) / 2;
        this.fontRenderer.drawString(label, textX, TOP + i * ROW_H + 4, 0xf1f5ef);
    }

    private String stateLabel(final int status) {
        switch (status) {
            case MaintainerStatus.DISABLED: return tr("state.disabled");
            case MaintainerStatus.OFFLINE: return tr("state.offline");
            case MaintainerStatus.INCOMPLETE: return tr("state.incomplete");
            case MaintainerStatus.STOCKED: return tr("state.stocked");
            case MaintainerStatus.QUEUED: return tr("state.queued");
            case MaintainerStatus.CALCULATING: return tr("state.calculating");
            case MaintainerStatus.NO_PATTERN: return tr("state.no_pattern");
            case MaintainerStatus.WAITING_CPU: return tr("state.waiting_cpu");
            case MaintainerStatus.CRAFTING: return tr("state.crafting");
            case MaintainerStatus.SUBMIT_FAILED: return tr("state.submit_failed");
            case MaintainerStatus.ORDER_UNKNOWN: return tr("state.order_unknown");
            case MaintainerStatus.NO_DISCRETIZER: return tr("state.no_discretizer");
            case MaintainerStatus.PARTIAL: return tr("state.partial");
            default: return "";
        }
    }

    private boolean warning(final int status) {
        return status == MaintainerStatus.NO_PATTERN || status == MaintainerStatus.SUBMIT_FAILED
                || status == MaintainerStatus.ORDER_UNKNOWN || status == MaintainerStatus.NO_DISCRETIZER
                || status == MaintainerStatus.INCOMPLETE;
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        final StockMaintainerNetwork.State state = this.container.state;
        if (state == null) { return; }
        for (int i = 0; i < ROWS; i++) {
            final int rowY = TOP + i * ROW_H;
            if (!state.configured[i]) { continue; }
            if (!state.icons[i].isEmpty() && this.isPointInRegion(SLOT_X, rowY, 16, 16, mouseX, mouseY)) {
                this.renderToolTip(state.icons[i], mouseX, mouseY);
            }
            if (this.isPointInRegion(COUNT_X - 1, rowY, 46, 16, mouseX, mouseY)) {
                this.drawHoveringText(state.countsValid[i] ? tr("current_exact", state.counts[i]) : tr("state.offline"),
                        mouseX, mouseY);
            }
            if (this.isPointInRegion(STATE_X - 1, rowY, 62, 16, mouseX, mouseY)) {
                if (this.dirty(i)) {
                    this.drawHoveringText(tr("confirm_hint"), mouseX, mouseY);
                } else {
                    this.drawHoveringText(this.stateHint(state.statuses[i]), mouseX, mouseY);
                }
            }
            if (this.isPointInRegion(TOGGLE_X, rowY + 4, 16, 9, mouseX, mouseY)) {
                this.drawHoveringText(tr(state.enabled[i] ? "toggle_off" : "toggle_on"), mouseX, mouseY);
            }
            // 智能下单开关：除了说明当前状态，还要讲清开它会有什么后果。
            if (this.isPointInRegion(SMART_TOGGLE_X, rowY + 4, 16, 9, mouseX, mouseY)) {
                this.drawHoveringText(java.util.Arrays.asList(
                        tr(state.smartPartBatches[i] ? "smart_off" : "smart_on"), tr("smart_risk")),
                        mouseX, mouseY);
            }
        }
        for (int i = 0; i < ROWS; i++) {
            if (this.isPointInRegion(MODE_X, TOP + i * ROW_H + 4, MODE_W, 9, mouseX, mouseY)) {
                this.drawHoveringText(tr(state.configured[i] ? "mode_locked" : "mode_hint"), mouseX, mouseY);
            }
        }
    }

    private String stateHint(final int status) {
        switch (status) {
            case MaintainerStatus.NO_PATTERN: return tr("hint.no_pattern");
            case MaintainerStatus.WAITING_CPU: return tr("hint.waiting_cpu");
            case MaintainerStatus.ORDER_UNKNOWN: return tr("hint.order_unknown");
            case MaintainerStatus.NO_DISCRETIZER: return tr("hint.no_discretizer");
            case MaintainerStatus.INCOMPLETE: return tr("hint.incomplete");
            case MaintainerStatus.SUBMIT_FAILED: return tr("hint.submit_failed");
            case MaintainerStatus.CRAFTING: return tr("hint.crafting");
            case MaintainerStatus.PARTIAL: return tr("hint.partial");
            default: return this.stateLabel(status);
        }
    }

    // ---------------------------------------------------------------- HEI 拖入

    @Optional.Method(modid = "jei")
    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(final Object ingredient) {
        final List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        final StockMaintainerNetwork.State state = this.container.state;
        final boolean isItem = ingredient instanceof ItemStack;
        final boolean hasFluid = !isItem && FluidMaintainerBridge.fluidOf(ingredient) != null;
        if (!isItem && !hasFluid) { return targets; }
        for (int i = 0; i < ROWS; i++) {
            final int row = i;
            final int index = this.container.page * ROWS + i;
            final int mode = this.modeOf(state, i);
            // 拖入类型必须与本行模式相符，不因拖入液体容器而偷偷改变语义。
            if (mode == MaintainerRule.MODE_FLUID ? isItem : !isItem) { continue; }
            targets.add(new IGhostIngredientHandler.Target<Object>() {
                @Override
                public Rectangle getArea() {
                    return new Rectangle(guiLeft + SLOT_X, guiTop + TOP + row * ROW_H, 16, 16);
                }

                @Override
                public void accept(final Object dropped) {
                    if (dropped instanceof ItemStack) {
                        markItem(index, (ItemStack) dropped);
                    } else {
                        markFluid(index, dropped);
                    }
                }
            });
        }
        return targets;
    }
}
