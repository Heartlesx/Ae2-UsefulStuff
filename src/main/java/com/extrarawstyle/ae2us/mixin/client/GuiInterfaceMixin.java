package com.extrarawstyle.ae2us.mixin.client;

import com.extrarawstyle.ae2us.NetworkHandler;
import com.extrarawstyle.ae2us.api.ISmartBlockingContainer;
import com.extrarawstyle.ae2us.client.SmartBlockingButton;

import appeng.api.config.YesNo;
import appeng.client.gui.implementations.GuiInterface;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 给 ME 接口界面加一个智能阻挡按钮。
 * 二合一接口（Fluid Craft for AE2）的界面是 GuiInterface 的子类，它的 addButtons/drawFG/func_146284_a
 * 都会调用父类实现，所以它也会自动拿到这个按钮，不需要单独适配。
 * func_73866_w_ 与 func_146284_a 是 Minecraft 方法在运行时的方法名（本工程不生成 refmap）。
 */
@Mixin(value = GuiInterface.class, remap = false)
public abstract class GuiInterfaceMixin {

    @Unique
    private SmartBlockingButton ae2us$smartButton;

    // 放在 initGui 末尾：AE2 自己和其它附属模组的按钮这时都已经进了列表，
    // 才能把我们的按钮接在左侧那一列的下方，而不是压在别人身上。
    @Inject(method = "func_73866_w_", at = @At("TAIL"))
    private void ae2us$addButton(final CallbackInfo ci) {
        final List<GuiButton> buttons = ((GuiScreenAccessor) (Object) this).ae2us$buttonList();
        final int left = ((GuiContainer) (Object) this).getGuiLeft() - 18;

        int bottom = ((GuiContainer) (Object) this).getGuiTop();
        for (final GuiButton button : buttons) {
            if (button.x == left && button.y > bottom) {
                bottom = button.y;
            }
        }

        this.ae2us$smartButton = new SmartBlockingButton(left, bottom + 18);
        buttons.add(this.ae2us$smartButton);
    }

    @Inject(method = "drawFG", at = @At("TAIL"))
    private void ae2us$updateButton(final int offsetX, final int offsetY, final int mouseX, final int mouseY,
            final CallbackInfo ci) {
        if (this.ae2us$smartButton != null) {
            final ISmartBlockingContainer container = (ISmartBlockingContainer) ((GuiUpgradeableAccessor) (Object) this)
                    .ae2us$container();
            this.ae2us$smartButton.set(container.ae2us$isSmartBlockingEnabled() ? YesNo.YES : YesNo.NO);
        }
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true)
    private void ae2us$onButtonPressed(final GuiButton button, final CallbackInfo ci) {
        if (button == this.ae2us$smartButton) {
            NetworkHandler.sendToggle();
            ci.cancel();
        }
    }
}
