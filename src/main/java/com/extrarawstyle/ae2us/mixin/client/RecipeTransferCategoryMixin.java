package com.extrarawstyle.ae2us.mixin.client;

import com.extrarawstyle.ae2us.PatternUploadNetwork;
import com.extrarawstyle.ae2us.PatternCategory;

import appeng.container.implementations.ContainerPatternEncoder;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.transfer.RecipeTransferUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import mezz.jei.api.gui.IGuiIngredient;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecipeTransferUtil.class, remap = false)
public abstract class RecipeTransferCategoryMixin {
    @Inject(method = "transferRecipe(Lnet/minecraft/inventory/Container;Lmezz/jei/gui/recipes/RecipeLayout;Lnet/minecraft/entity/player/EntityPlayer;Z)Z",
            at = @At("RETURN"))
    private static void ae2us$recordCategory(final Container container, final RecipeLayout recipeLayout,
            final EntityPlayer player, final boolean maxTransfer, final CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && container instanceof ContainerPatternEncoder) {
            final String uid = recipeLayout.getRecipeCategory().getUid();
            final String title = recipeLayout.getRecipeCategory().getTitle();
            LogManager.getLogger("AE2US").debug("HEI transfer: category={} title={}", uid, title);
            String mold = "";
            String moldTitle = "";
            // 模具按配方内容识别（moldKey 认 GT 的模具/模头物品），不按机器白名单——合金炉等使用
            // 模具的机器曾因此漏掉映射；白名单现在只用于"无模具记录的旧样板禁止回退分类"判定。
            for (final IGuiIngredient<ItemStack> ingredient : recipeLayout.getItemStacks().getGuiIngredients().values()) {
                if (!ingredient.isInput()) {
                    continue;
                }
                for (final ItemStack stack : ingredient.getAllIngredients()) {
                    if (stack != null && !PatternCategory.moldKey(stack).isEmpty()) {
                        mold = PatternCategory.moldKey(stack);
                        moldTitle = stack.getDisplayName();
                        break;
                    }
                }
                if (!mold.isEmpty()) {
                    break;
                }
            }
            LogManager.getLogger("AE2US").debug("HEI mold captured: {} {}", mold, moldTitle);
            PatternUploadNetwork.captureCategory(uid, title, mold, moldTitle);
        }
    }
}
