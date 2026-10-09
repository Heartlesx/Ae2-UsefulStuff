package com.extrarawstyle.ae2us.client;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.widgets.GuiImgButton;
import net.minecraft.client.resources.I18n;

/**
 * 复用原版阻挡按钮的图标（Settings.BLOCK + YesNo），只把 tooltip 换成本模组的说明，
 * 另外在 tooltip 里点明它依赖原版阻挡。
 */
public class SmartBlockingButton extends GuiImgButton {

    public SmartBlockingButton(final int x, final int y) {
        super(x, y, Settings.BLOCK, YesNo.NO);
    }

    @Override
    public String getMessage() {
        final boolean enabled = this.getCurrentValue() == YesNo.YES;
        return I18n.format("ae2us.tooltip.smart_blocking") + '\n'
                + I18n.format(enabled ? "ae2us.tooltip.smart_blocking.on" : "ae2us.tooltip.smart_blocking.off");
    }
}
