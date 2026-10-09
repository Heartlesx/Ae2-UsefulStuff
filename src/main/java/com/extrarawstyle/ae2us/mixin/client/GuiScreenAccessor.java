package com.extrarawstyle.ae2us.mixin.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * buttonList 是 GuiScreen 的 protected 字段，取出来才能把按钮加到界面上。
 * 这里直接挂在声明它的类上（不挂在子类），避免依赖"能否 shadow 继承来的成员"。
 * 名字用运行时名 field_146292_n：本工程不生成 mixin refmap，注解里的名字必须是运行时真实名字。
 */
@Mixin(value = GuiScreen.class, remap = false)
public interface GuiScreenAccessor {

    @Accessor(value = "field_146292_n", remap = false)
    List<GuiButton> ae2us$buttonList();

    @Accessor(value = "keyHandled", remap = false)
    void ae2us$setKeyHandled(boolean handled);
}
