package com.extrarawstyle.ae2us.mixin.client;

import com.extrarawstyle.ae2us.api.ICraftableItemSource;

import mezz.jei.gui.ingredients.GuiIngredient;
import mezz.jei.gui.recipes.RecipeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Rectangle;

/**
 * JEI（本整合包是 HadEnoughItems）/ HEI 配方界面里的"有样板"标记：配方格子里有样板的物品也画黄色小"+"。
 * 数据还是来自背后开着的 AE2 容器——在样板终端里对着物品按 R 时，AE2 的容器没有被关掉，
 * 所以不需要新增网络包，也不用去玩家身上找无线终端；没开 AE2 界面时没有数据，就不标记（已确认的方案 A）。
 *
 * 注入 drawRecipe 的末尾：draw = drawRecipe（画配方格子）+ drawOverlays（画 tooltip），
 * 所以标记压在格子上、又被 tooltip 压住。这个方法自己会恢复矩阵，末尾是绝对屏幕坐标，
 * 槽位屏幕坐标 = RecipeLayout.getPosX/getPosY + GuiIngredient.getRect().x/y。
 * HEI 类里的成员名与运行时一致（remap = false），本工程不生成 refmap。
 * 这个 mixin 由 JeiMixinLoader（late loader）注册，不能放进早期配置，否则会把 HEI 的类毒化。
 */
@Mixin(value = RecipeLayout.class, remap = false)
public abstract class RecipeLayoutMixin {

    @Unique
    private static boolean ae2us$logged;

    @Unique
    private static boolean ae2us$loggedNoSource;

    @Inject(method = "drawRecipe", at = @At("TAIL"))
    private void ae2us$drawPatternMarks(final Minecraft minecraft, final int mouseX, final int mouseY,
            final CallbackInfo ci) {
        final ICraftableItemSource source = ae2us$craftableSource();
        if (source == null) {
            if (!ae2us$loggedNoSource) {
                ae2us$loggedNoSource = true;
                LogManager.getLogger("AE2US")
                        .info("JEI 配方页：背后没有 AE2 容器（当前容器={}），不标记", ae2us$containerName(minecraft));
            }
            return;
        }

        final RecipeLayout layout = (RecipeLayout) (Object) this;
        GlStateManager.disableDepth();
        int marked = 0;
        for (final GuiIngredient<ItemStack> ingredient : layout.getItemStacks().getGuiIngredients().values()) {
            final ItemStack displayed = ingredient.getDisplayedIngredient();
            if (displayed == null || displayed.isEmpty() || !source.ae2us$isCraftable(displayed)) {
                continue;
            }

            final Rectangle rect = ingredient.getRect();
            final int x = layout.getPosX() + rect.x + 1;
            final int y = layout.getPosY() + rect.y + 1;
            Gui.drawRect(x, y + 2, x + 5, y + 3, 0xFFFFDD00);
            Gui.drawRect(x + 2, y, x + 3, y + 5, 0xFFFFDD00);
            marked++;
        }
        GlStateManager.enableDepth();

        if (!ae2us$logged && marked > 0) {
            ae2us$logged = true;
            LogManager.getLogger("AE2US").info("JEI 配方页标记 {} 个槽位", marked);
        }
        // Gui.drawRect 会改掉当前颜色，必须还原，否则后面的内容会被染色。
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /** 数据源：当前开着的容器是不是 AE2 终端/样板终端（是的话它已经带着网络的样板清单）。 */
    @Unique
    private static ICraftableItemSource ae2us$craftableSource() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        final Container container = minecraft.player.openContainer;
        return container instanceof ICraftableItemSource ? (ICraftableItemSource) container : null;
    }

    @Unique
    private static String ae2us$containerName(final Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return "无";
        }
        return minecraft.player.openContainer.getClass().getSimpleName();
    }

}
