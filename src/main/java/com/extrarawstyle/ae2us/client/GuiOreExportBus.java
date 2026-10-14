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

    @Override
    public void initGui() {
        super.initGui();
        final String text = this.input == null ? "" : this.input.getText();
        this.input = new GuiTextField(0, this.fontRenderer, this.guiLeft + 9, this.guiTop + 130, 188, 14);
        this.input.setMaxStringLength(8000);
        this.input.setText(text);
        if (this.container.storage != null) {
            final String priorityText = this.priorityInput == null ? "0" : this.priorityInput.getText();
            this.priorityInput = new GuiTextField(1, this.fontRenderer, this.guiLeft + 211, this.guiTop + 190, 51, 14);
            this.priorityInput.setMaxStringLength(11);
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
        drawRect(x, y, x + this.xSize, y + this.ySize, 0xffc6c6c6);
        drawRect(x + (this.black ? 74 : 8), y + 41, x + (this.black ? 138 : 72), y + 43, 0xff367a36);
        drawRect(x + 7, y + 45, x + 205, y + 127, 0xff383e38);
        drawRect(x + 211, y + 103, x + 302, y + 125, 0xffe0e9dc);
        final OreBusNetwork.State state = this.container.state;
        if (state != null && state.black == this.black) {
            final int row = this.selected - state.offset;
            if (row >= 0 && row < state.names.size()) { drawRect(x + 9, y + 46 + row * 10, x + 194, y + 56 + row * 10, 0xff456e45); }
            drawRect(x + 196, y + 46, x + 203, y + 126, 0xff202720);
            final int thumb = state.total <= 8 ? 80 : Math.max(8, 640 / state.total);
            final int top = state.total <= 8 ? 0 : (80 - thumb) * state.offset / (state.total - 8);
            drawRect(x + 196, y + 46 + top, x + 203, y + 46 + top + thumb, 0xff98ad92);
        }
        for (final Slot slot : this.inventorySlots.inventorySlots) {
            drawRect(x + slot.xPos - 1, y + slot.yPos - 1, x + slot.xPos + 17, y + slot.yPos + 17, 0xff777777);
            drawRect(x + slot.xPos, y + slot.yPos, x + slot.xPos + 16, y + slot.yPos + 16, 0xffeeeeee);
        }
        if (this.choices == null) {
            this.input.drawTextBox();
            if (this.priorityInput != null) { this.priorityInput.drawTextBox(); }
        }
    }

    @Override
    public void drawFG(final int x, final int y, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(I18n.format(this.container.storage == null
                ? "item.ae2us.ore_export_bus.name" : "item.ae2us.ore_storage_bus.name"), 124), 8, 8, 0x253925);
        final OreBusNetwork.State state = this.container.state;
        if (state != null) {
            final String counts = tr("counts", state.whiteCount, state.blackCount);
            this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(counts, 170), 136, 8, 0x333333);
            if (state.black == this.black) {
                for (int i = 0; i < state.names.size(); i++) {
                    this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth((state.offset + i + 1) + ". " + state.names.get(i), 181),
                            10, 47 + i * 10, 0xffffff);
                }
            }
            if (this.container.storage == null) {
                this.fontRenderer.drawString(tr("budget", state.budget), 205, 187, 0x333333);
            } else {
                this.fontRenderer.drawString(tr("sticky." + (state.sticky ? 1 : 0)), 211, 233, 0x333333);
                if (state.whiteCount == 0 && this.status.isEmpty()) { this.status = tr("empty_storage"); }
                else if (state.whiteCount > 0 && this.status.equals(tr("empty_storage"))) { this.status = ""; }
            }
        }
        this.fontRenderer.drawString(tr(this.container.storage == null ? "mode" : "access"), 211, 40, 0x333333);
        this.fontRenderer.drawString(tr(this.container.storage == null ? "redstone" : "display"), 211, 73, 0x333333);
        if (this.container.storage != null) { this.fontRenderer.drawString(tr("priority"), 211, 178, 0x333333); }
        this.fontRenderer.drawString(tr("mark"), 241, 110, 0x284528);
        // 物品图标走 AEBaseGui.drawItem（GUI 标准光照 + 白颜色）：手搓 renderItemAndEffectIntoGUI
        // 会继承绘制前的遗留 GL 颜色与光照状态，图标比槽位物品暗一截且随场景时暗时亮。
        if (!this.marked.isEmpty()) { this.drawItem(218, 106, this.marked); }
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, 144, 0x333333);
        this.fontRenderer.drawString(tr("upgrades"), 205, 144, 0x333333);
        if (!this.status.isEmpty()) {
            this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(this.status, this.container.storage == null ? 96 : 294),
                    this.container.storage == null ? 205 : 8, this.container.storage == null ? 201 : 247, 0x993333);
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
            // 框体：窗口同款灰底 + 深墨绿描边（窗口本身没有边框，不描一圈会和窗口糊在一起）。
            drawRect(panelX - 1, panelY - 1, panelX + 267, panelY + panelH + 1, 0xff383e38);
            drawRect(panelX, panelY, panelX + 266, panelY + panelH, 0xffc6c6c6);
            this.fontRenderer.drawString(tr("select_ores"), panelX + 10, panelY + 10, 0x253925);
            if (!this.marked.isEmpty() && !this.partitionDialog) {
                // 标题行右侧标明正在给哪个物品选矿典，防手滑选错对象。
                final int nameRight = panelX + 238;
                final int nameMax = nameRight - panelX - 18 - this.fontRenderer.getStringWidth(tr("select_ores"));
                final String name = this.fontRenderer.trimStringToWidth(this.marked.getDisplayName(), Math.max(20, nameMax));
                this.fontRenderer.drawString(name, nameRight - this.fontRenderer.getStringWidth(name), panelY + 10, 0x333333);
                this.drawItem(panelX + 242, panelY + 6, this.marked);
                RenderHelper.disableStandardItemLighting();   // drawItem 开了 GUI 光照，名单区绘制要关回去
            }
            // 名单区：与左列名单同款深墨绿底 + 白字，勾选/悬停用名单里那条选中绿。
            final int fieldX = panelX + 8;
            final int fieldW = 250;
            drawRect(fieldX, listY, fieldX + fieldW, listY + rows * 18 + 4, 0xff383e38);
            if (this.choices.length > 8) {
                final int trackX = fieldX + fieldW - 9;
                final int trackTop = listY + 2;
                final int trackH = rows * 18;
                drawRect(trackX, trackTop, trackX + 7, trackTop + trackH, 0xff202720);
                final int thumb = Math.max(6, trackH * 8 / this.choices.length);
                final int thumbTop = trackTop + (trackH - thumb) * this.choiceOffset / (this.choices.length - 8);
                drawRect(trackX, thumbTop, trackX + 7, thumbTop + thumb, 0xff98ad92);
            }
            final int textMax = fieldW - 28 - (this.choices.length > 8 ? 10 : 0);
            for (int i = 0; i < rows; i++) {
                final int index = i + this.choiceOffset;
                if (index >= this.choices.length) { break; }
                final int rowY = listY + 2 + i * 18;
                final boolean hovered = mouseX >= fieldX && mouseX < fieldX + fieldW
                        && mouseY >= rowY && mouseY < rowY + 18;
                if (this.checked[index]) {
                    drawRect(fieldX + 2, rowY, fieldX + fieldW - 2, rowY + 18, 0xff456e45);
                } else if (hovered) {
                    drawRect(fieldX + 2, rowY, fieldX + fieldW - 2, rowY + 18, 0x60456e45);
                }
                final int boxY = rowY + 4;
                drawRect(fieldX + 4, boxY, fieldX + 15, boxY + 11, 0xff98ad92);
                drawRect(fieldX + 5, boxY + 1, fieldX + 14, boxY + 10, this.checked[index] ? 0xffe0e9dc : 0xff383e38);
                if (this.checked[index]) { this.fontRenderer.drawString("x", fieldX + 7, boxY + 2, 0x367a36); }
                this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(this.choices[index], textMax),
                        fieldX + 24, rowY + 5, 0xffffff);
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
