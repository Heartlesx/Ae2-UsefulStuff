package com.extrarawstyle.ae2us.client;

import appeng.client.gui.AEBaseGui;
import appeng.container.interfaces.IJEIGhostIngredients;
import com.extrarawstyle.ae2us.alarm.AlarmNetwork;
import com.extrarawstyle.ae2us.alarm.ContainerStorageAlarm;
import com.extrarawstyle.ae2us.alarm.ThresholdText;
import com.extrarawstyle.ae2us.alarm.TileStorageAlarm;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.Optional;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Optional.Interface(iface = "appeng.container.interfaces.IJEIGhostIngredients", modid = "jei")
public final class GuiStorageAlarm extends AEBaseGui implements IJEIGhostIngredients {
    private static final int ROWS = 8;
    private static final int PAGES = TileStorageAlarm.CAPACITY / ROWS;
    private static final int TOP = 38, ROW_H = 17, SLOT_X = 14;
    private static final int FIELD_X = 198, FIELD_W = 51;

    private final ContainerStorageAlarm container;
    private final GuiTextField[] fields = new GuiTextField[ROWS];
    private final String[] committed = new String[ROWS];
    private String status = "";
    private AlarmNetwork.State previous;

    public GuiStorageAlarm(final ContainerStorageAlarm container) {
        super(container);
        this.container = container;
        this.xSize = 280;
        this.ySize = 272;
    }

    private static String tr(final String key, final Object... args) { return I18n.format("ae2us.alarm." + key, args); }

    @Override
    public void initGui() {
        super.initGui();
        for (int i = 0; i < ROWS; i++) {
            final int y = TOP + i * ROW_H + 4;
            if (this.fields[i] == null) {
                this.fields[i] = new GuiTextField(i, this.fontRenderer, this.guiLeft + FIELD_X, this.guiTop + y, FIELD_W, 14);
            } else {
                this.fields[i].x = this.guiLeft + FIELD_X;
                this.fields[i].y = this.guiTop + y;
            }
            this.fields[i].setMaxStringLength(24);
            this.fields[i].setEnableBackgroundDrawing(false);
            this.fields[i].setTextColor(0x304449);
        }
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(0, this.guiLeft + 208, this.guiTop + 178, 16, 14, "<"));
        this.buttonList.add(new GuiButton(1, this.guiLeft + 254, this.guiTop + 178, 16, 14, ">"));
        Keyboard.enableRepeatEvents(true);
    }

    private void send(final int operation, final int index, final String text, final ItemStack stack) {
        AlarmNetwork.action(this.container.windowId, operation, index, text, stack);
    }

    private void sendPage(final int page) {
        this.send(AlarmNetwork.PAGE, MathHelper.clamp(page, 0, PAGES - 1), "", ItemStack.EMPTY);
    }

    private void commit(final int row) {
        final GuiTextField field = this.fields[row];
        if (field.getText().equals(this.committed[row])) { return; }
        final Long value = ThresholdText.parse(field.getText());
        if (value == null) {
            this.status = tr("invalid");
            field.setText(this.committed[row] == null ? "" : this.committed[row]);
            return;
        }
        this.committed[row] = String.valueOf(value);
        this.send(AlarmNetwork.SET_THRESHOLD, this.container.page * ROWS + row, field.getText(), ItemStack.EMPTY);
        this.status = "";
    }

    private void commitAll() {
        for (int i = 0; i < ROWS; i++) { this.commit(i); }
    }

    @Override
    protected void actionPerformed(final GuiButton button) {
        this.commitAll();
        this.sendPage(this.container.page + (button.id == 0 ? -1 : 1));
    }

    @Override
    protected void mouseClicked(final int x, final int y, final int button) throws IOException {
        for (int i = 0; i < ROWS; i++) {
            if (this.isPointInRegion(SLOT_X, TOP + i * ROW_H, 16, 16, x, y)) {
                final int index = this.container.page * ROWS + i;
                if (button == 1) {
                    this.send(AlarmNetwork.CLEAR, index, "", ItemStack.EMPTY);
                } else if (button == 0 && !this.mc.player.inventory.getItemStack().isEmpty()) {
                    this.send(AlarmNetwork.SET_ITEM, index, "", this.mc.player.inventory.getItemStack());
                }
                return;
            }
        }
        for (final GuiTextField field : this.fields) { field.mouseClicked(x, y, button); }
        super.mouseClicked(x, y, button);
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
            final GuiTextField field = this.fields[i];
            if (!field.isFocused()) { continue; }
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.commit(i);
                field.setFocused(false);
                return;
            }
            if (key == Keyboard.KEY_ESCAPE) {
                field.setText(this.committed[i] == null ? "" : this.committed[i]);
                field.setFocused(false);
                return;
            }
            if (field.textboxKeyTyped(character, key)) { return; }
        }
        super.keyTyped(character, key);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (final GuiTextField field : this.fields) { field.updateCursorCounter(); }
        if (this.previous != this.container.state && this.container.state != null) {
            this.previous = this.container.state;
            for (int i = 0; i < ROWS; i++) {
                this.committed[i] = String.valueOf(this.container.state.thresholds[i]);
                if (!this.fields[i].isFocused()) { this.fields[i].setText(this.committed[i]); }
            }
        }
        for (int i = 0; i < ROWS; i++) {
            final GuiTextField field = this.fields[i];
            if (!field.isFocused() && !field.getText().equals(this.committed[i])) {
                field.setText(this.committed[i] == null ? "" : this.committed[i]);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        this.commitAll();
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public void drawBG(final int x, final int y, final int mouseX, final int mouseY) {
        drawRect(x, y, x + this.xSize, y + this.ySize, 0xff344b4d);
        drawRect(x + 2, y + 2, x + 278, y + 270, 0xffd9e2dc);
        drawRect(x + 2, y + 2, x + 278, y + 23, 0xff263a40);
        drawRect(x + 2, y + 2, x + 5, y + 23, 0xff3cd4b2);
        final AlarmNetwork.State state = this.container.state;
        int low = 0;
        if (state != null && state.status == TileStorageAlarm.STATUS_ACTIVE) {
            for (final boolean below : state.below) { if (below) { low++; } }
        }
        drawRect(x + 203, y + 6, x + 270, y + 19,
                state == null || state.status != TileStorageAlarm.STATUS_ACTIVE ? 0xff40545a
                        : low > 0 ? 0xff684445 : 0xff345f58);
        drawRect(x + 207, y + 10, x + 212, y + 15,
                state == null || state.status != TileStorageAlarm.STATUS_ACTIVE ? 0xff607579
                        : low > 0 ? 0xffed5a53 : 0xff3cd4b2);
        drawRect(x + 6, y + 25, x + 274, y + 37, 0xffb6c8c0);
        for (int i = 0; i < ROWS; i++) {
            final int rowY = y + TOP + i * ROW_H;
            final ItemStack item = state == null ? ItemStack.EMPTY : state.items[i];
            final boolean below = state != null && state.status == TileStorageAlarm.STATUS_ACTIVE && state.below[i];
            drawRect(x + 6, rowY, x + 274, rowY + 16,
                    below ? 0xfff3dedb : i % 2 == 0 ? 0xffe6eee8 : 0xffedf3ed);
            if (!item.isEmpty()) {
                drawRect(x + 6, rowY, x + 9, rowY + 16, below ? 0xffdc544e : 0xff4eb499);
            }
            drawRect(x + SLOT_X, rowY, x + SLOT_X + 16, rowY + 16, 0xffafc1ba);
            drawRect(x + SLOT_X + 1, rowY + 1, x + SLOT_X + 15, rowY + 15, 0xfff4f7f1);
            drawRect(x + 193, rowY + 1, x + 254, rowY + 15, below ? 0xffc7a8a3 : 0xffa7bdb4);
            drawRect(x + 194, rowY + 2, x + 253, rowY + 14, 0xfffbfffb);
        }
        for (final Slot slot : this.inventorySlots.inventorySlots) {
            drawRect(x + slot.xPos, y + slot.yPos, x + slot.xPos + 16, y + slot.yPos + 16, 0xffb9c8c2);
            drawRect(x + slot.xPos + 1, y + slot.yPos + 1, x + slot.xPos + 15, y + slot.yPos + 15, 0xffedf2ec);
        }
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < ROWS; i++) {
            final ItemStack item = state == null ? ItemStack.EMPTY : state.items[i];
            if (!item.isEmpty()) { this.itemRender.renderItemAndEffectIntoGUI(item, x + SLOT_X, y + TOP + i * ROW_H); }
        }
        RenderHelper.disableStandardItemLighting();
        for (final GuiTextField field : this.fields) { field.drawTextBox(); }
    }

    @Override
    public void drawFG(final int x, final int y, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(tr("title"), 184), 11, 8, 0xf1f5ef);
        final AlarmNetwork.State state = this.container.state;
        if (state != null) {
            int low = 0;
            for (final boolean below : state.below) { if (below) { low++; } }
            final String label = state.status == TileStorageAlarm.STATUS_ACTIVE
                    ? low > 0 ? tr("page_low", low) : tr("active")
                    : state.status == TileStorageAlarm.STATUS_NO_CHANNEL ? tr("no_channel") : tr("no_power");
            this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(label, 51), 216, 8, 0xf1f5ef);
            this.fontRenderer.drawString((this.container.page + 1) + " / " + PAGES, 229, 181, 0x324b4d);
            for (int i = 0; i < ROWS; i++) {
                final ItemStack item = state.items[i];
                if (!item.isEmpty()) {
                    this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(item.getDisplayName(), 80), 40,
                            TOP + i * ROW_H + 4, 0x2d4144);
                    final String count = state.status == TileStorageAlarm.STATUS_ACTIVE ? compactCount(state.counts[i]) : "-";
                    this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(count, 53), 128,
                            TOP + i * ROW_H + 4, state.below[i] ? 0xaa3b37 : 0x2d6260);
                }
                if (state.status == TileStorageAlarm.STATUS_ACTIVE && state.below[i]) {
                    this.fontRenderer.drawString("!", 261, TOP + i * ROW_H + 4, 0xad3e3b);
                }
            }
        }
        this.fontRenderer.drawString(tr("item"), 40, 27, 0x355050);
        this.fontRenderer.drawString(tr("current"), 127, 27, 0x355050);
        this.fontRenderer.drawString(tr("threshold"), 197, 27, 0x355050);
        final String footer = this.status.isEmpty() ? I18n.format("container.inventory") : this.status;
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(footer, 145), 58, 181,
                this.status.isEmpty() ? 0x3e5555 : 0xaa3b37);
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        final AlarmNetwork.State state = this.container.state;
        if (state == null) { return; }
        for (int i = 0; i < ROWS; i++) {
            if (!state.items[i].isEmpty() && this.isPointInRegion(SLOT_X, TOP + i * ROW_H, 16, 16, mouseX, mouseY)) {
                this.renderToolTip(state.items[i], mouseX, mouseY);
            }
            if (!state.items[i].isEmpty() && state.status == TileStorageAlarm.STATUS_ACTIVE
                    && this.isPointInRegion(122, TOP + i * ROW_H, 61, 16, mouseX, mouseY)) {
                this.drawHoveringText(tr("current_exact", state.counts[i]), mouseX, mouseY);
            }
        }
    }

    @Optional.Method(modid = "jei")
    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(final Object ingredient) {
        if (!(ingredient instanceof ItemStack)) { return Collections.emptyList(); }
        final List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            final int row = i;
            targets.add(new IGhostIngredientHandler.Target<ItemStack>() {
                @Override
                public Rectangle getArea() {
                    return new Rectangle(guiLeft + SLOT_X, guiTop + TOP + row * ROW_H, 16, 16);
                }

                @Override
                public void accept(final ItemStack stack) {
                    send(AlarmNetwork.SET_ITEM, container.page * ROWS + row, "", stack);
                }
            });
        }
        return targets;
    }

    private static String compactCount(final long count) {
        if (count < 1000) { return Long.toString(count); }
        final long unit;
        final String suffix;
        if (count >= 1000000000000L) { unit = 1000000000000L; suffix = "T"; }
        else if (count >= 1000000000L) { unit = 1000000000L; suffix = "B"; }
        else if (count >= 1000000L) { unit = 1000000L; suffix = "M"; }
        else { unit = 1000L; suffix = "K"; }
        final long whole = count / unit;
        final long decimal = count % unit * 10 / unit;
        return whole + (whole < 100 && decimal > 0 ? "." + decimal : "") + suffix;
    }
}
