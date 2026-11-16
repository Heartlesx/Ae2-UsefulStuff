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

    // 原版容器界面配色：硬边、无渐变。色号与样板上传界面（PatternUploadClient）一致。
    private static final int COL_OUTLINE = 0xFF1B1B1B;
    private static final int COL_FACE = 0xFFC6C6C6;
    private static final int COL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COL_SHADOW = 0xFF555555;
    private static final int COL_FIELD = 0xFF8B8B8B;
    private static final int COL_FIELD_SHADOW = 0xFF373737;
    private static final int COL_TEXT = 0xFF202020;
    private static final int COL_TEXT_DIM = 0xFF4A4A4A;
    private static final int COL_SEPARATOR = 0xFFB4B4B4;
    private static final int COL_WARN_ROW = 0xFFCDB6B3;
    private static final int COL_OK_BAR = 0xFF2E7D32;
    private static final int COL_WARN_BAR = 0xFFA0352F;
    private static final int COL_WARN_TEXT = 0xFFA0352F;
    /** 按钮脸：常态灰面加左上高光 / 右下阴影，与 widgets.png 观感一致。 */
    private static final int COL_BUTTON = 0xFF6D6D6D;
    private static final int COL_BUTTON_HIGHLIGHT = 0xFFA0A0A0;
    private static final int COL_BUTTON_SHADOW = 0xFF3A3A3A;

    private final ContainerStockMaintainer container;
    private final GuiTextField[] thresholds = new GuiTextField[ROWS];
    private final GuiTextField[] batches = new GuiTextField[ROWS];
    private final String[] committedThresholds = new String[ROWS];
    private final String[] committedBatches = new String[ROWS];
    /** 每行的待定模式：仅客户端，不随目标一起同步。只在当前页有效，翻页即回到物品。 */
    private final int[] pendingMode = new int[ROWS];
    /**
     * 上一次已经应用的服务端状态页码；-1 表示还没收到过状态。
     * 翻页请求发出后页码不会立刻变，真正换页以服务端回来的 {@code state.page} 为准，
     * 所以这里记的是「已确认的页」，不是「请求的页」。
     */
    private int appliedPage = -1;
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

    // ---------------------------------------------------------------- 原版像素绘制

    /** 原版容器面板：黑描边、灰底面、左上高光 / 右下阴影，全部一像素硬边。 */
    private static void panel(final int x, final int y, final int width, final int height) {
        drawRect(x, y, x + width, y + height, COL_OUTLINE);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, COL_FACE);
        drawRect(x + 1, y + 1, x + width - 1, y + 2, COL_HIGHLIGHT);
        drawRect(x + 1, y + 1, x + 2, y + height - 1, COL_HIGHLIGHT);
        drawRect(x + 1, y + height - 2, x + width - 1, y + height - 1, COL_SHADOW);
        drawRect(x + width - 2, y + 1, x + width - 1, y + height - 1, COL_SHADOW);
    }

    /** 凹槽：左上压暗、右下提亮，用来做槽位、模式格和输入框底。 */
    private static void inset(final int x, final int y, final int width, final int height, final int fill) {
        drawRect(x, y, x + width, y + height, fill);
        drawRect(x, y, x + width, y + 1, COL_FIELD_SHADOW);
        drawRect(x, y, x + 1, y + height, COL_FIELD_SHADOW);
        drawRect(x + width - 1, y, x + width, y + height, COL_HIGHLIGHT);
        drawRect(x, y + height - 1, x + width, y + height, COL_HIGHLIGHT);
    }

    /** 刻线：上暗下亮的一像素分隔线（原版标题栏下那条）。 */
    private static void groove(final int x, final int y, final int width) {
        drawRect(x, y, x + width, y + 1, COL_SHADOW);
        drawRect(x, y + 1, x + width, y + 2, COL_HIGHLIGHT);
    }

    /** 按钮脸：黑描边 + 灰面；pressed 时明暗反过来，读作「按下去」。 */
    private static void buttonFace(final int x, final int y, final int width, final int height,
                                   final boolean pressed) {
        drawRect(x, y, x + width, y + height, COL_OUTLINE);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, COL_BUTTON);
        final int top = pressed ? COL_BUTTON_SHADOW : COL_BUTTON_HIGHLIGHT;
        final int bottom = pressed ? COL_BUTTON_HIGHLIGHT : COL_BUTTON_SHADOW;
        drawRect(x + 1, y + 1, x + width - 1, y + 2, top);
        drawRect(x + 1, y + 1, x + 2, y + height - 1, top);
        drawRect(x + 1, y + height - 2, x + width - 1, y + height - 1, bottom);
        drawRect(x + width - 2, y + 1, x + width - 1, y + height - 1, bottom);
    }

    /** 像素对勾：5×5 里点几块 1px 方块，画在按钮中央。 */
    private static void drawTick(final int x, final int y, final int color) {
        drawRect(x, y + 3, x + 1, y + 4, color);
        drawRect(x + 1, y + 4, x + 2, y + 5, color);
        drawRect(x + 2, y + 3, x + 3, y + 4, color);
        drawRect(x + 3, y + 2, x + 4, y + 3, color);
        drawRect(x + 4, y + 1, x + 5, y + 2, color);
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
        field.setTextColor(0x1A1A1A);
        return field;
    }

    /**
     * 画一个输入框。背景由 drawFieldBox 自绘，所以关掉了 GuiTextField 自带背景；
     * 关掉后文字会从 field.x 直接起笔、贴着凹底左边缘，这里绘制期间临时右移 3px，画完还原
     * —— mouseClicked 用的是原始坐标，命中区仍覆盖整个可见输入框。
     */
    private void drawField(final GuiTextField field) {
        final int x = field.x;
        field.x = x + 3;
        field.drawTextBox();
        field.x = x;
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
            if (state != null && state.configured[i] && this.isPointInRegion(TOGGLE_X, rowY, 16, 16, x, y)) {
                this.commitAll();
                this.send(StockMaintainerNetwork.SET_ENABLED, index, state.enabled[i] ? "0" : "1", ItemStack.EMPTY);
                return;
            }
            if (state != null && state.configured[i]
                    && this.isPointInRegion(SMART_TOGGLE_X, rowY, 16, 16, x, y)) {
                this.commitAll();
                this.send(StockMaintainerNetwork.SET_SMART_PART_BATCH, index,
                        state.smartPartBatches[i] ? "0" : "1", ItemStack.EMPTY);
                return;
            }
            // 状态格被换成「确定」按钮时点它：只提交本行，不牵动别的行。
            // 命中区与画出来的按钮一致（rowY+1..rowY+16），否则上下各有几像素点了没反应。
            if (this.dirty(i) && this.isPointInRegion(STATE_X, rowY + 1, CONFIRM_W, 15, x, y)) {
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
     *
     * <p>待定模式的归零只在这里、只按「已确认的页码」发生：首个有效状态、或服务端确认的页
     * 真的换了，就把 8 个槽重新初始化成物品；同页的普通刷新原样保留玩家的手动选择。
     * 翻页按钮、滚轮、上一页/下一页最终都是发一个请求，换页统一由这一处识别。</p>
     */
    private void syncFromState() {
        final StockMaintainerNetwork.State state = this.container.state;
        if (state == null) { return; }
        if (state.page != this.appliedPage) {
            this.appliedPage = state.page;
            java.util.Arrays.fill(this.pendingMode, MaintainerRule.MODE_ITEM);
        }
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
        panel(x, y, this.xSize, this.ySize);
        this.drawHeaderSignal(x, y);
        groove(x + 4, y + 25, this.xSize - 8);
        final StockMaintainerNetwork.State state = this.container.state;
        for (int i = 0; i < ROWS; i++) {
            final int rowY = y + TOP + i * ROW_H;
            final boolean configured = state != null && state.configured[i];
            final boolean warning = state != null && configured && this.warning(state.statuses[i]);
            if (warning) {
                drawRect(x + 6, rowY, x + 370, rowY + 16, COL_WARN_ROW);
            }
            if (i < ROWS - 1) {
                drawRect(x + 6, rowY + 16, x + 370, rowY + 17, COL_SEPARATOR);
            }
            if (configured) {
                drawRect(x + 4, rowY, x + 6, rowY + 16, warning ? COL_WARN_BAR : COL_OK_BAR);
            }
            this.drawModeChip(x, rowY);
            inset(x + SLOT_X, rowY, 16, 16, COL_FIELD);
            this.drawFieldBox(x, rowY, THRESHOLD_X, warning);
            this.drawFieldBox(x, rowY, BATCH_X, warning);
            this.drawConfirm(x, rowY, this.dirty(i));
            this.drawToggle(x, rowY, state, i);
            this.drawSmartToggle(x, rowY, state, i);
        }
        for (final Slot slot : this.inventorySlots.inventorySlots) {
            inset(x + slot.xPos, y + slot.yPos, 16, 16, COL_FIELD);
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
        for (final GuiTextField field : this.thresholds) { this.drawField(field); }
        for (final GuiTextField field : this.batches) { this.drawField(field); }
    }

    /** 输入框：黑框 + 凹槽底。凹槽自带左上暗、右下亮，就是原版输入框的样子。 */
    private void drawFieldBox(final int x, final int rowY, final int fieldX, final boolean warning) {
        drawRect(x + fieldX - 1, rowY + 1, x + fieldX + FIELD_W, rowY + 15,
                warning ? 0xFF8C6B67 : COL_OUTLINE);
        inset(x + fieldX, rowY + 2, FIELD_W - 1, 12, COL_FACE);
    }

    private void drawConfirm(final int x, final int rowY, final boolean dirty) {
        if (!dirty) { return; }
        buttonFace(x + STATE_X, rowY + 1, CONFIRM_W, 15, false);
    }

    /** 模式格：凹槽底。文字在 drawFG 里画，因为 drawBG 阶段不画文字。 */
    private void drawModeChip(final int x, final int rowY) {
        inset(x + MODE_X, rowY + 3, MODE_W, 10, COL_FIELD);
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
        drawRect(x + 296, y + 7, x + 306, y + 17, COL_OUTLINE);
        drawRect(x + 297, y + 8, x + 305, y + 16,
                online && working > 0 ? 0xFF4CBB4C : online ? 0xFFB09A3C : COL_SHADOW);
    }

    private void drawToggle(final int x, final int rowY, final StockMaintainerNetwork.State state, final int i) {
        if (state == null || !state.configured[i]) { return; }
        final boolean on = state.enabled[i];
        buttonFace(x + TOGGLE_X, rowY, 16, 16, on);
        if (on) { drawTick(x + TOGGLE_X + 6, rowY + 5, 0xFFF2F2F2); }
    }

    /** 每行第二个开关：本条的材料不够整批时减量下单。与左边启用开关同款画法，对勾用暖黄区分。 */
    private void drawSmartToggle(final int x, final int rowY, final StockMaintainerNetwork.State state, final int i) {
        if (state == null || !state.configured[i]) { return; }
        final boolean on = state.smartPartBatches[i];
        buttonFace(x + SMART_TOGGLE_X, rowY, 16, 16, on);
        if (on) { drawTick(x + SMART_TOGGLE_X + 6, rowY + 5, 0xFFFFD24A); }
    }

    @Override
    public void drawFG(final int x, final int y, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(tr("title"), 200), 8, 9, COL_TEXT);
        final StockMaintainerNetwork.State state = this.container.state;
        if (state != null) {
            final String label = state.status == TileStockMaintainer.STATUS_ACTIVE ? tr("active")
                    : state.status == TileStockMaintainer.STATUS_NO_CHANNEL ? tr("no_channel") : tr("no_power");
            this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(label, 43), 310, 9, COL_TEXT_DIM);
        }
        this.fontRenderer.drawString((this.container.page + 1) + " / " + PAGES, 296, 181, COL_TEXT_DIM);
        if (state != null) {
            for (int i = 0; i < ROWS; i++) { this.drawRowText(state, i); }
        }
        for (int i = 0; i < ROWS; i++) { this.drawConfirmText(i); }
        this.fontRenderer.drawString(tr("mark"), NAME_X, 30, COL_TEXT_DIM);
        this.fontRenderer.drawString(tr("current"), COUNT_X, 30, COL_TEXT_DIM);
        this.fontRenderer.drawString(tr("threshold"), THRESHOLD_X, 30, COL_TEXT_DIM);
        this.fontRenderer.drawString(tr("batch"), BATCH_X, 30, COL_TEXT_DIM);
        this.fontRenderer.drawString(tr("state"), STATE_X, 30, COL_TEXT_DIM);
        this.fontRenderer.drawString(tr("enable"), TOGGLE_X, 30, COL_TEXT_DIM);
        this.fontRenderer.drawString(tr("smart"), SMART_TOGGLE_X, 30, COL_TEXT_DIM);
        final String footer = this.status.isEmpty() ? I18n.format("container.inventory") : this.status;
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(footer, 150), 58, 181,
                this.status.isEmpty() ? COL_TEXT_DIM : COL_WARN_TEXT);
    }

    private void drawRowText(final StockMaintainerNetwork.State state, final int i) {
        final int rowY = TOP + i * ROW_H + 4;
        final int mode = this.modeOf(state, i);
        final String modeText = mode == MaintainerRule.MODE_FLUID ? tr("fluid") : tr("item_mode");
        if (!state.configured[i]) {
            // 未配置行只画模式文字，目标名称区留空。
            this.fontRenderer.drawString(modeText, MODE_X + 2, rowY, COL_TEXT_DIM);
            return;
        }
        this.fontRenderer.drawString(modeText, MODE_X + 2, rowY, COL_TEXT);
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(state.labels[i], NAME_W), NAME_X, rowY, COL_TEXT);
        final String count = state.countsValid[i]
                ? MaintainerAmount.compact(state.counts[i]) + (mode == MaintainerRule.MODE_FLUID ? "mB" : "")
                : "-";
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(count, 44), COUNT_X, rowY,
                state.countsValid[i] ? COL_TEXT : COL_TEXT_DIM);
        // 状态格让位给「确定」按钮时，状态文字不画（按钮点击后状态自然回来）。
        if (this.dirty(i)) { return; }
        // 减量下的单要看得见实下多少；整批就照旧只写「合成中」，不添噪声。
        final boolean partial = MaintainerStatus.holdsOrder(state.statuses[i])
                && state.orderBatches[i] > 0 && state.orderBatches[i] < state.batches[i];
        final String stateText = partial
                ? this.stateLabel(state.statuses[i]) + " " + MaintainerAmount.compact(state.orderBatches[i])
                : this.stateLabel(state.statuses[i]);
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(stateText, 60),
                STATE_X, rowY, this.warning(state.statuses[i]) ? COL_WARN_TEXT : COL_TEXT);
    }

    /** 「确定」按钮上的文字。drawFG 里画，drawBG 阶段不画文字。 */
    private void drawConfirmText(final int i) {
        if (!this.dirty(i)) { return; }
        final String label = tr("confirm");
        // 按钮铺的是 STATE_X..STATE_X+CONFIRM_W、rowY+1..rowY+16，文字按这块矩形居中。
        final int textX = STATE_X + (CONFIRM_W - this.fontRenderer.getStringWidth(label)) / 2;
        final int textY = TOP + i * ROW_H + 1 + (15 - this.fontRenderer.FONT_HEIGHT) / 2;
        this.fontRenderer.drawString(label, textX, textY, 0xFFFFFFFF);
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
            if (this.isPointInRegion(TOGGLE_X, rowY, 16, 16, mouseX, mouseY)) {
                this.drawHoveringText(tr(state.enabled[i] ? "toggle_off" : "toggle_on"), mouseX, mouseY);
            }
            // 智能下单开关：除了说明当前状态，还要讲清开它会有什么后果。
            if (this.isPointInRegion(SMART_TOGGLE_X, rowY, 16, 16, mouseX, mouseY)) {
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
