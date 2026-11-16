package com.extrarawstyle.ae2us.client;

import appeng.api.config.RedstoneMode;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.client.gui.AEBaseGui;
import appeng.container.interfaces.IJEIGhostIngredients;
import com.extrarawstyle.ae2us.orebus.ContainerOreExportBus;
import com.extrarawstyle.ae2us.orebus.OreBusNetwork;
import com.extrarawstyle.ae2us.orebus.OreExportRules;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.oredict.OreDictionary;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Optional.Interface(iface = "appeng.container.interfaces.IJEIGhostIngredients", modid = "jei")
public final class GuiOreExportBus extends AEBaseGui implements IJEIGhostIngredients {
    private final ContainerOreExportBus container;
    private GuiTextField input;
    private GuiTextField priorityInput;
    private boolean priorityEditing;
    private boolean partitionPending;
    private boolean partitionDialog;
    private boolean waitingPartitionResult;
    private int partitionBefore;
    private boolean black;
    private int selected = -1;
    private int editing = -1;
    private String status = "";
    private String[] choices;
    private boolean[] checked;
    private int choiceOffset;
    private boolean choiceBlack;
    private ItemStack marked = ItemStack.EMPTY;
    private boolean dragMarked;
    private boolean draggingScroll;
    private OreBusNetwork.State previous;

    public GuiOreExportBus(final ContainerOreExportBus container) {
        super(container);
        this.container = container;
        this.xSize = 310;
        this.ySize = container.storage == null ? 238 : 260;
    }

    private static String tr(final String key, final Object... args) { return I18n.format("ae2us.orebus." + key, args); }

    // 像素容器视觉，取值与样板上传界面（PatternUploadClient）保持一致。
    private static final int COL_OUTLINE = 0xFF1B1B1B;
    private static final int COL_FACE = 0xFFC6C6C6;
    private static final int COL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COL_SHADOW = 0xFF555555;
    private static final int COL_SUNKEN = 0xFF8B8B8B;
    private static final int COL_SUNKEN_EDGE = 0xFF373737;
    private static final int COL_ROW_HOVER = 0xFFD9E4E6;
    private static final int COL_ROW_SELECTED = 0xFFFFFFFF;
    private static final int COL_INPUT = 0xFFFFFFFF;
    private static final int COL_INPUT_IDLE = 0xFFE4E4E4;
    private static final int COL_TEXT = 0xFF202020;
    private static final int COL_TEXT_DIM = 0xFF4A4A4A;
    private static final int COL_ERROR = 0xFF9C2020;

    /** 面板：黑描边 + 灰底 + 左上高光 / 右下阴影，全部一像素硬边。 */
    private void panel(final int x, final int y, final int width, final int height) {
        drawRect(x, y, x + width, y + height, COL_OUTLINE);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, COL_FACE);
        drawRect(x + 1, y + 1, x + width - 1, y + 2, COL_HIGHLIGHT);
        drawRect(x + 1, y + 1, x + 2, y + height - 1, COL_HIGHLIGHT);
        drawRect(x + 1, y + height - 2, x + width - 1, y + height - 1, COL_SHADOW);
        drawRect(x + width - 2, y + 1, x + width - 1, y + height - 1, COL_SHADOW);
    }

    /** 凹槽：左上压暗、右下提亮。 */
    private void inset(final int x, final int y, final int width, final int height, final int fill) {
        drawRect(x, y, x + width, y + height, fill);
        drawRect(x, y, x + width, y + 1, COL_SUNKEN_EDGE);
        drawRect(x, y, x + 1, y + height, COL_SUNKEN_EDGE);
        drawRect(x + width - 1, y, x + width, y + height, COL_HIGHLIGHT);
        drawRect(x, y + height - 1, x + width, y + height, COL_HIGHLIGHT);
    }

    /** 刻线：上暗下亮的一像素分隔线。 */
    private void groove(final int x, final int y, final int width) {
        drawRect(x, y, x + width, y + 1, COL_SHADOW);
        drawRect(x, y + 1, x + width, y + 2, COL_HIGHLIGHT);
    }

    /**
     * 背包槽位：18×18 的凹陷小格。
     *
     * 不用 inset()，因为槽位是紧挨着排的，四条边都描会让相邻格子的高光和阴影叠成一道亮线；
     * 原版背包槽只靠左上压暗、右下提亮就已经能读出凹陷感。
     */
    private void slotBox(final int x, final int y) {
        drawRect(x, y, x + 18, y + 18, COL_SUNKEN);
        drawRect(x, y, x + 18, y + 1, COL_SUNKEN_EDGE);
        drawRect(x, y, x + 1, y + 18, COL_SUNKEN_EDGE);
        drawRect(x + 17, y, x + 18, y + 18, COL_HIGHLIGHT);
        drawRect(x, y + 17, x + 18, y + 18, COL_HIGHLIGHT);
    }

    /**
     * 输入框：凹槽本身充当边框（左上压暗、右下提亮），聚焦时再加一圈一像素高光。
     *
     * 不用外扩描边是因为这里的输入框夹在列表和标签之间，净空只有十几像素，外扩 2px 会顶到
     * 相邻元素。GuiTextField 的坐标只把边框算在凹槽内部，命中区域不变。
     */
    private void drawInput(final GuiTextField field) {
        final int fx = field.x;
        final int fy = field.y;
        final int fw = field.width;
        final int fh = field.height;
        if (field.isFocused()) {
            drawRect(fx - 1, fy - 1, fx + fw + 1, fy + fh + 1, COL_HIGHLIGHT);
        }
        inset(fx, fy, fw, fh, field.isFocused() ? COL_INPUT : COL_INPUT_IDLE);
        field.x = fx + 4;
        field.y = fy + (fh - 8) / 2;
        field.drawTextBox();
        field.x = fx;
        field.y = fy;
    }

    @Override
    public void initGui() {
        super.initGui();
        final String text = this.input == null ? "" : this.input.getText();
        // 左右边缘与上方规则列表凹槽（x+7 起、宽 188）对齐，避免看上去错开 2px。
        // 上移 2px、压到 12 高，给下面的「物品栏」标签留出间隙（原来底边正好压在标签顶边上）。
        this.input = new GuiTextField(0, this.fontRenderer, this.guiLeft + 7, this.guiTop + 128, 188, 12);
        this.input.setMaxStringLength(8000);
        // 背景必须关掉：开着时 drawTextBox 会多画一层纯黑底、并自行把文字下移一次，
        // 与 drawInput 里的居中偏移叠加，就成了黑底 + 文字偏下。
        this.input.setEnableBackgroundDrawing(false);
        this.input.setText(text);
        if (this.container.storage != null) {
            final String priorityText = this.priorityInput == null ? "0" : this.priorityInput.getText();
            this.priorityInput = new GuiTextField(1, this.fontRenderer, this.guiLeft + 211, this.guiTop + 190, 51, 14);
            this.priorityInput.setMaxStringLength(11);
            this.priorityInput.setEnableBackgroundDrawing(false);
            this.priorityInput.setText(priorityText);
        }
        Keyboard.enableRepeatEvents(true);
        this.buttons();
    }

    private void button(final int id, final int x, final int y, final int width, final String text) {
        this.buttonList.add(new GuiButton(id, this.guiLeft + x, this.guiTop + y, width, 18, text));
    }

    private void buttons() {
        this.buttonList.clear();
        if (this.choices != null) {
            final int buttonY = this.dialogListY() + this.dialogRows() * 18 + 4 + 10 - this.guiTop;
            this.button(20, 52, buttonY, 96, tr("confirm"));
            this.button(21, 162, buttonY, 96, tr("cancel"));
            return;
        }
        final OreBusNetwork.State state = this.container.state;
        this.button(0, 8, 23, 64, tr("white"));
        this.button(1, 74, 23, 64, tr("black"));
        this.button(2, 142, 23, 14, "^");
        this.button(3, 158, 23, 14, "v");
        this.button(4, 174, 23, 14, "-");
        this.button(5, 190, 23, 14, "...");
        if (this.container.storage == null) {
            this.button(6, 211, 49, 91, tr("mode." + (state == null ? 0 : state.mode)));
            this.button(7, 211, 82, 91, tr("redstone." + (state == null ? 0 : state.redstone)));
        } else {
            this.button(9, 211, 49, 91, tr("access." + (state == null ? 0 : state.access)));
            this.button(10, 211, 82, 91, tr("display." + (state == null ? 0 : state.display)));
            this.button(11, 266, 188, 36, tr("save"));
            this.button(12, 211, 213, 91, tr("partition"));
        }
        this.button(8, 211, 126, 91, tr(this.editing < 0 ? "add" : "save"));
        for (final GuiButton button : this.buttonList) {
            if (button.id >= 2 && button.id <= 5) { button.enabled = this.selected >= 0; }
            if (button.id == 7) { button.enabled = state != null && state.redstoneCard; }
            if (button.id == 12) { button.enabled = !this.partitionPending; }
        }
    }

    /** 标签选择对话框可见行数：随名单长度自适应，最少 2 行、最多 8 行（再多滚轮翻页）。 */
    private int dialogRows() {
        return this.choices == null ? 2 : Math.max(2, Math.min(8, this.choices.length));
    }

    /** 对话框名单区的屏幕 Y：整个面板按行数自适应高度并垂直居中。 */
    private int dialogListY() {
        return this.guiTop + (this.ySize - (this.dialogRows() * 18 + 4 + 62)) / 2 + 26;
    }

    private void send(final int operation, final int index, final String text) {
        if (operation != OreBusNetwork.PAGE) { this.waitingPartitionResult = false; }
        OreBusNetwork.action(this.container.windowId, operation, this.black, index, text);
    }

    @Override
    protected void actionPerformed(final GuiButton button) {
        final OreBusNetwork.State state = this.container.state;
        if (button.id == 20) {
            final StringBuilder names = new StringBuilder();
            for (int i = 0; i < this.choices.length; i++) {
                if (this.checked[i]) { names.append(this.choices[i]).append('\n'); }
            }
            OreBusNetwork.action(this.container.windowId, OreBusNetwork.ADD, this.choiceBlack, 0, names.toString());
            if (this.partitionDialog) {
                this.waitingPartitionResult = names.length() > 0;
                this.partitionBefore = state == null ? 0 : state.whiteCount;
            }
            this.partitionDialog = false;
            this.choices = null;
            this.buttons();
        } else if (button.id == 21) {
            this.partitionDialog = false;
            this.choices = null;
            this.buttons();
        } else if (button.id <= 1) {
            this.black = button.id == 1;
            this.selected = -1;
            this.editing = -1;
            this.send(OreBusNetwork.PAGE, 0, "");
            this.buttons();
        } else if (button.id >= 2 && button.id <= 4) {
            this.send(button.id == 2 ? OreBusNetwork.UP : button.id == 3 ? OreBusNetwork.DOWN : OreBusNetwork.REMOVE, this.selected, "");
            if (button.id == 2 && this.selected > 0) { this.selected--; }
            if (button.id == 3 && state != null && this.selected + 1 < state.total) { this.selected++; }
            if (button.id == 4) { this.selected = -1; }
            this.editing = -1;
            this.buttons();
        } else if (button.id == 5 && state != null) {
            final int local = this.selected - state.offset;
            if (local >= 0 && local < state.names.size()) {
                this.input.setText(state.names.get(local));
                this.editing = this.selected;
                this.input.setFocused(true);
                this.buttons();
            }
        } else if (button.id == 6 && state != null) {
            this.send(OreBusNetwork.MODE, (state.mode + 1) % OreExportRules.Mode.values().length, "");
        } else if (button.id == 7 && state != null) {
            this.send(OreBusNetwork.REDSTONE, (state.redstone + 1) % RedstoneMode.values().length, "");
        } else if (button.id == 8) {
            this.addText(this.input.getText());
        } else if (button.id == 9 && state != null) {
            this.send(OreBusNetwork.ACCESS, (state.access + 1) % 3, "");
        } else if (button.id == 10 && state != null) {
            this.send(OreBusNetwork.DISPLAY, (state.display + 1) % 2, "");
        } else if (button.id == 11) {
            try {
                final int priority = Integer.parseInt(this.priorityInput.getText().trim());
                this.send(OreBusNetwork.PRIORITY, 0, Integer.toString(priority));
                this.priorityEditing = false;
                this.priorityInput.setFocused(false);
                this.status = "";
            } catch (final NumberFormatException ignored) { this.status = tr("invalid_priority"); }
        } else if (button.id == 12) {
            this.partitionPending = true;
            this.container.partitionReady = false;
            this.send(OreBusNetwork.PARTITION, 0, "");
            this.buttons();
        }
    }

    private void addText(final String text) {
        final List<String> lines = Arrays.asList(text.split("\\r?\\n"));
        for (final String line : lines) {
            if (!line.trim().isEmpty() && !OreExportRules.valid(line.trim())) {
                this.status = tr("invalid");
                return;
            }
        }
        if (this.editing >= 0) {
            if (lines.size() != 1 || !OreExportRules.valid(text.trim())) { this.status = tr("invalid"); return; }
            this.send(OreBusNetwork.REPLACE, this.editing, text.trim());
        } else {
            this.send(OreBusNetwork.ADD, 0, text);
        }
        this.input.setText("");
        this.editing = -1;
        this.status = "";
        this.buttons();
    }

    private void mark(final ItemStack stack) {
        if (stack.isEmpty()) { return; }
        this.marked = stack.copy();
        this.marked.setCount(1);
        final int[] ids = OreDictionary.getOreIDs(stack);
        if (ids.length == 0) { this.status = tr("no_ore"); return; }
        final String[] names = new String[ids.length];
        for (int i = 0; i < ids.length; i++) { names[i] = OreDictionary.getOreName(ids[i]); }
        if (names.length == 1) {
            this.send(OreBusNetwork.ADD, 0, names[0]);
        } else {
            this.choices = names;
            this.checked = new boolean[names.length];
            this.choiceOffset = 0;
            this.choiceBlack = this.black;
            this.input.setFocused(false);
            this.buttons();
        }
        this.status = "";
    }

    private boolean marker(final int x, final int y) {
        return this.isPointInRegion(211, 103, 91, 22, x, y);
    }

    @Override
    protected void mouseClicked(final int x, final int y, final int button) throws IOException {
        this.dragMarked = false;
        if (this.choices != null) {
            for (final GuiButton control : this.buttonList) {
                if (control.mousePressed(this.mc, x, y)) { this.actionPerformed(control); return; }
            }
            final int listY = this.dialogListY();
            if (x >= this.guiLeft + 30 && x < this.guiLeft + 280 && y >= listY + 2
                    && y < listY + 2 + this.dialogRows() * 18) {
                final int row = (y - listY - 2) / 18 + this.choiceOffset;
                if (row < this.choices.length) { this.checked[row] = !this.checked[row]; }
            }
            return;
        }
        if (this.marker(x, y)) {
            this.mark(this.mc.player.inventory.getItemStack());
            this.dragMarked = true;
            return;
        }
        final OreBusNetwork.State state = this.container.state;
        if (state != null && state.black == this.black && this.isPointInRegion(9, 46, 184, 80, x, y)) {
            final int local = (y - this.guiTop - 46) / 10;
            if (local < state.names.size()) { this.selected = state.offset + local; this.buttons(); }
            return;
        }
        if (state != null && this.isPointInRegion(196, 46, 8, 80, x, y)) {
            this.draggingScroll = true;
            this.page((int) ((y - this.guiTop - 46) / 79.0 * Math.max(0, state.total - 8)));
            return;
        }
        this.input.mouseClicked(x, y, button);
        if (this.priorityInput != null) {
            this.priorityInput.mouseClicked(x, y, button);
            if (this.priorityInput.isFocused()) { this.priorityEditing = true; }
        }
        super.mouseClicked(x, y, button);
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int button, final long time) {
        if (this.choices != null) { return; }
        if (this.draggingScroll && this.container.state != null) {
            this.page((int) ((y - this.guiTop - 46) / 79.0 * Math.max(0, this.container.state.total - 8)));
            return;
        }
        if (!this.dragMarked && this.marker(x, y) && !this.mc.player.inventory.getItemStack().isEmpty()) {
            this.dragMarked = true;
            this.mark(this.mc.player.inventory.getItemStack());
            return;
        }
        super.mouseClickMove(x, y, button, time);
    }

    @Override
    protected void mouseReleased(final int x, final int y, final int button) {
        this.draggingScroll = false;
        if (this.choices != null) { return; }
        super.mouseReleased(x, y, button);
    }

    @Override
    protected void handleMouseClick(final Slot slot, final int slotId, final int mouseButton, final ClickType type) {
        if (slot instanceof appeng.container.slot.AppEngSlot && ((appeng.container.slot.AppEngSlot) slot).isPlayerSide()
                && type == ClickType.QUICK_MOVE && !slot.getStack().isEmpty()
                && !(slot.getStack().getItem() instanceof IUpgradeModule)) {
            this.mark(slot.getStack());
            return;
        }
        super.handleMouseClick(slot, slotId, mouseButton, type);
    }

    private void page(final int offset) {
        final OreBusNetwork.State state = this.container.state;
        if (state != null) { this.send(OreBusNetwork.PAGE, Math.max(0, Math.min(offset, Math.max(0, state.total - 8))), ""); }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        final int wheel = Mouse.getEventDWheel();
        if (wheel == 0) { return; }
        if (this.choices != null) {
            this.choiceOffset = Math.max(0, Math.min(Math.max(0, this.choices.length - 8), this.choiceOffset + (wheel > 0 ? -1 : 1)));
        } else if (this.container.state != null) {
            this.page(this.container.state.offset + (wheel > 0 ? -3 : 3));
        }
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (this.choices != null) {
            if (key == Keyboard.KEY_ESCAPE) { this.choices = null; this.partitionDialog = false; this.buttons(); }
            return;
        }
        if (this.priorityInput != null && this.priorityInput.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.actionPerformed(new GuiButton(11, 0, 0, ""));
                return;
            }
            if (this.priorityInput.textboxKeyTyped(character, key)) { return; }
            if (key != Keyboard.KEY_ESCAPE) { return; }
        }
        if (this.input.isFocused()) {
            if (isKeyComboCtrlV(key)) {
                final String pasted = getClipboardString();
                if (pasted.contains("\n") || pasted.length() > 8000) { this.addText(pasted); return; }
            }
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { this.addText(this.input.getText()); return; }
            if (this.input.textboxKeyTyped(character, key)) { return; }
            if (key != Keyboard.KEY_ESCAPE) { return; }
        }
        super.keyTyped(character, key);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.input.updateCursorCounter();
        if (this.priorityInput != null) { this.priorityInput.updateCursorCounter(); }
        if (this.container.partitionReady && this.partitionPending && this.choices == null) {
            this.container.partitionReady = false;
            this.partitionPending = false;
            if (this.container.partitionNames.isEmpty()) {
                this.status = tr("no_partition");
            } else {
                this.choices = this.container.partitionNames.toArray(new String[0]);
                this.checked = new boolean[this.choices.length];
                this.choiceOffset = 0;
                this.choiceBlack = false;
                this.partitionDialog = true;
                this.input.setFocused(false);
                this.priorityInput.setFocused(false);
            }
            this.buttons();
        }
        if (this.previous != this.container.state) {
            this.previous = this.container.state;
            if (this.priorityInput != null && this.previous != null && !this.priorityEditing) {
                this.priorityInput.setText(Integer.toString(this.previous.priority));
            }
            if (this.waitingPartitionResult && this.previous != null) {
                this.status = tr("partition_added", Math.max(0, this.previous.whiteCount - this.partitionBefore));
            }
            this.buttons();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void drawBG(final int x, final int y, final int mouseX, final int mouseY) {
        panel(x, y, this.xSize, this.ySize);
        // 标题区与内容区之间的一像素刻线。
        groove(x + 6, y + 21, this.xSize - 12);
        // 白/黑名单切换按钮下方的当前页标识条。
        drawRect(x + (this.black ? 74 : 8), y + 41, x + (this.black ? 138 : 72), y + 43, COL_SUNKEN_EDGE);
        // 规则列表凹槽（只铺到滚动条左侧，滚动条自己是一道窄凹槽）。
        inset(x + 7, y + 45, 188, 82, COL_SUNKEN);
        final OreBusNetwork.State state = this.container.state;
        if (state != null && state.black == this.black) {
            final int row = this.selected - state.offset;
            if (row >= 0 && row < state.names.size()) {
                // 选中行压在凹槽上，左上高光 / 右下阴影做出凸起感。
                final int rowTop = y + 46 + row * 10;
                drawRect(x + 9, rowTop, x + 194, rowTop + 10, COL_ROW_SELECTED);
                drawRect(x + 9, rowTop, x + 194, rowTop + 1, COL_HIGHLIGHT);
                drawRect(x + 9, rowTop + 9, x + 194, rowTop + 10, COL_SHADOW);
            }
            final int thumb = state.total <= 8 ? 80 : Math.max(8, 640 / state.total);
            final int top = state.total <= 8 ? 0 : (80 - thumb) * state.offset / (state.total - 8);
            inset(x + 196, y + 46, 7, 80, COL_SUNKEN_EDGE);
            drawRect(x + 197, y + 47 + top, x + 202, y + 47 + top + Math.max(4, thumb - 2), COL_FACE);
        }
        // 物品标记区凹槽。
        inset(x + 211, y + 103, 91, 22, COL_INPUT_IDLE);
        // 槽位用原版容器的凹陷画法：中灰底 + 左上压暗 / 右下提亮。之前是纯黑外框配浅灰内底，
        // 深框浅底看着是凸出来的，和原版背包槽的凹陷感相反，一排排紧挨着就很扎眼。
        for (final Slot slot : this.inventorySlots.inventorySlots) {
            this.slotBox(x + slot.xPos - 1, y + slot.yPos - 1);
        }
        if (this.choices == null) {
            this.drawInput(this.input);
            if (this.priorityInput != null) { this.drawInput(this.priorityInput); }
        }
    }

    @Override
    public void drawFG(final int x, final int y, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(I18n.format(this.container.storage == null
                ? "item.ae2us.ore_export_bus.name" : "item.ae2us.ore_storage_bus.name"), 124), 8, 8, COL_TEXT);
        final OreBusNetwork.State state = this.container.state;
        if (state != null) {
            final String counts = tr("counts", state.whiteCount, state.blackCount);
            this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(counts, 170), 136, 8, COL_TEXT_DIM);
            if (state.black == this.black) {
                for (int i = 0; i < state.names.size(); i++) {
                    // 选中行是浅色凸起块，文字要跟着换成深色才看得清。
                    final boolean selectedRow = this.selected - state.offset == i;
                    this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth((state.offset + i + 1) + ". " + state.names.get(i), 181),
                            10, 47 + i * 10, selectedRow ? COL_TEXT : 0xFFFFFFFF);
                }
            }
            if (this.container.storage == null) {
                this.fontRenderer.drawString(tr("budget", state.budget), 205, 187, COL_TEXT_DIM);
            } else {
                this.fontRenderer.drawString(tr("sticky." + (state.sticky ? 1 : 0)), 211, 233, COL_TEXT_DIM);
                if (state.whiteCount == 0 && this.status.isEmpty()) { this.status = tr("empty_storage"); }
                else if (state.whiteCount > 0 && this.status.equals(tr("empty_storage"))) { this.status = ""; }
            }
        }
        this.fontRenderer.drawString(tr(this.container.storage == null ? "mode" : "access"), 211, 40, COL_TEXT_DIM);
        this.fontRenderer.drawString(tr(this.container.storage == null ? "redstone" : "display"), 211, 73, COL_TEXT_DIM);
        if (this.container.storage != null) { this.fontRenderer.drawString(tr("priority"), 211, 178, COL_TEXT_DIM); }
        this.fontRenderer.drawString(tr("mark"), 241, 110, COL_TEXT_DIM);
        // 物品图标走 AEBaseGui.drawItem（GUI 标准光照 + 白颜色）：手搓 renderItemAndEffectIntoGUI
        // 会继承绘制前的遗留 GL 颜色与光照状态，图标比槽位物品暗一截且随场景时暗时亮。
        if (!this.marked.isEmpty()) { this.drawItem(218, 106, this.marked); }
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, 144, COL_TEXT_DIM);
        this.fontRenderer.drawString(tr("upgrades"), 205, 144, COL_TEXT_DIM);
        if (!this.status.isEmpty()) {
            this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(this.status, this.container.storage == null ? 96 : 294),
                    this.container.storage == null ? 205 : 8, this.container.storage == null ? 201 : 247, COL_ERROR);
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.choices != null) {
            // 到这里时 vanilla 已为"光标上的物品"重新打开了标准光照（GuiContainer.drawScreen 帧尾
            // enableStandardItemLighting 且不再关闭）。不先关掉，对话框所有平面绘制（面板、复选
            // 框、按钮）都会被光照乘暗——环境光 0.4 + 方向光 ≈0.11，面板 0xd6 渲染成 0x6d。
            RenderHelper.disableStandardItemLighting();
            final int rows = this.dialogRows();
            final int listY = this.dialogListY();
            final int panelX = this.guiLeft + 22;
            final int panelY = listY - 26;
            final int panelH = rows * 18 + 4 + 62;
            // 框体沿用主窗口的同款像素面板：黑描边 + 灰底 + 明暗层次。
            this.panel(panelX - 1, panelY - 1, 267, panelH + 2);
            this.fontRenderer.drawString(tr("select_ores"), panelX + 10, panelY + 9, COL_TEXT);
            this.groove(panelX + 6, panelY + 20, 255);
            if (!this.marked.isEmpty() && !this.partitionDialog) {
                // 标题行右侧标明正在给哪个物品选矿典，防手滑选错对象。
                final int nameRight = panelX + 238;
                final int nameMax = nameRight - panelX - 18 - this.fontRenderer.getStringWidth(tr("select_ores"));
                final String name = this.fontRenderer.trimStringToWidth(this.marked.getDisplayName(), Math.max(20, nameMax));
                this.fontRenderer.drawString(name, nameRight - this.fontRenderer.getStringWidth(name), panelY + 9, COL_TEXT_DIM);
                this.drawItem(panelX + 242, panelY + 5, this.marked);
                RenderHelper.disableStandardItemLighting();   // drawItem 开了 GUI 光照，名单区绘制要关回去
            }
            // 名单区：凹槽 + 凸起的勾选行，与主窗口规则列表同一套层次。
            final int fieldX = panelX + 8;
            final int fieldW = 250;
            final int fieldH = rows * 18 + 4;
            this.inset(fieldX, listY, fieldW, fieldH, COL_SUNKEN);
            if (this.choices.length > 8) {
                final int trackX = fieldX + fieldW - 9;
                final int trackTop = listY + 2;
                final int trackH = rows * 18;
                this.inset(trackX, trackTop, 7, trackH, COL_SUNKEN_EDGE);
                final int thumb = Math.max(6, trackH * 8 / this.choices.length);
                final int thumbTop = trackTop + (trackH - thumb) * this.choiceOffset / (this.choices.length - 8);
                drawRect(trackX + 1, thumbTop, trackX + 6, thumbTop + Math.max(4, thumb), COL_FACE);
            }
            final int textMax = fieldW - 28 - (this.choices.length > 8 ? 10 : 0);
            for (int i = 0; i < rows; i++) {
                final int index = i + this.choiceOffset;
                if (index >= this.choices.length) { break; }
                final int rowY = listY + 2 + i * 18;
                final boolean hovered = mouseX >= fieldX && mouseX < fieldX + fieldW
                        && mouseY >= rowY && mouseY < rowY + 18;
                if (this.checked[index]) {
                    drawRect(fieldX + 2, rowY, fieldX + fieldW - 2, rowY + 18, COL_ROW_SELECTED);
                    drawRect(fieldX + 2, rowY, fieldX + fieldW - 2, rowY + 1, COL_HIGHLIGHT);
                    drawRect(fieldX + 2, rowY + 17, fieldX + fieldW - 2, rowY + 18, COL_SHADOW);
                } else if (hovered) {
                    drawRect(fieldX + 2, rowY, fieldX + fieldW - 2, rowY + 18, COL_ROW_HOVER);
                }
                // 勾选框：凹槽底 + 描边，选中时填实心方块（不只靠颜色区分）。
                final int boxX = fieldX + 4;
                final int boxY = rowY + 4;
                drawRect(boxX, boxY, boxX + 11, boxY + 11, COL_OUTLINE);
                drawRect(boxX + 1, boxY + 1, boxX + 10, boxY + 10,
                        this.checked[index] ? COL_TEXT : COL_INPUT_IDLE);
                if (this.checked[index]) {
                    drawRect(boxX + 3, boxY + 3, boxX + 8, boxY + 8, COL_INPUT_IDLE);
                }
                this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(this.choices[index], textMax),
                        fieldX + 24, rowY + 5, this.checked[index] ? COL_TEXT : 0xFFFFFFFF);
            }
            for (final GuiButton button : this.buttonList) { button.drawButton(this.mc, mouseX, mouseY, partialTicks); }
        } else {
            final OreBusNetwork.State state = this.container.state;
            if (state != null && state.black == this.black && this.isPointInRegion(9, 46, 184, 80, mouseX, mouseY)) {
                final int row = (mouseY - this.guiTop - 46) / 10;
                if (row < state.names.size()) { this.drawHoveringText(state.names.get(row), mouseX, mouseY); }
            }
            for (final GuiButton button : this.buttonList) {
                if (button.isMouseOver() && button.id >= 2 && button.id <= 5) {
                    this.drawHoveringText(tr("tool." + button.id), mouseX, mouseY);
                }
                if (button.isMouseOver() && button.id == 10) {
                    this.drawHoveringText(tr("display_tip"), mouseX, mouseY);
                }
                if (button.isMouseOver() && button.id == 12) {
                    this.drawHoveringText(tr("partition_tip"), mouseX, mouseY);
                }
            }
        }
    }

    @Optional.Method(modid = "jei")
    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(final Object ingredient) {
        if (!(ingredient instanceof ItemStack) || this.choices != null) { return Collections.emptyList(); }
        return Collections.singletonList(new IGhostIngredientHandler.Target<ItemStack>() {
            @Override
            public Rectangle getArea() { return new Rectangle(guiLeft + 211, guiTop + 103, 91, 22); }
            @Override
            public void accept(final ItemStack stack) { mark(stack); }
        });
    }
}
