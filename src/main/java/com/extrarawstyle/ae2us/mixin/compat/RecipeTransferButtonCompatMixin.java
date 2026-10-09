package com.extrarawstyle.ae2us.mixin.compat;

import mezz.jei.gui.recipes.RecipeTransferButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 给 HEI 4.35.0 的 RecipeTransferButton 补回 init(Container, EntityPlayer)。
 * <p>
 * HEI 4.35.0 把 init 改名成 update（方法体逐条指令相同，只有名字变了），而 AE2 v0.56.7 是
 * 按旧名编译的，于是悬停 JEI 传输按钮、AE2 要显示"缺物品"错误时在
 * appeng.integration.modules.jei.JEIMissingItem.showError:228 抛 NoSuchMethodError 崩溃。
 * 这里把旧名字补回去、转发到 update，让 AE2 的调用点能解析。
 * <p>
 * 只在目标类确实没有 init 时才应用（由 FeatureMixinPlugin 用 RecipeTransferButtonCompat 判断），
 * 否则旧版 HEI（4.32.0 自带 init）上会和方法冲突。
 * <p>
 * update 用 @Shadow 声明：编译期对着的是 libs 里的 HEI 4.32.0，那里还没有 update，
 * 只有运行期的 4.35.0 才需要它存在，所以不能在编译期直接引用。
 */
@Mixin(value = RecipeTransferButton.class, remap = false)
public abstract class RecipeTransferButtonCompatMixin {

    @Shadow
    public abstract void update(Container container, EntityPlayer player);

    public void init(final Container container, final EntityPlayer player) {
        this.update(container, player);
    }
}
