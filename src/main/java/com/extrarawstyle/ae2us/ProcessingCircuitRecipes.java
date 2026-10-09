package com.extrarawstyle.ae2us;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.category.GTRecipeCategory;
import gregtech.api.recipes.chance.output.impl.ChancedFluidOutput;
import gregtech.api.recipes.chance.output.impl.ChancedItemOutput;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ProcessingCircuitRecipes {
    private static final ResourceLocation FLUID_PACKET = new ResourceLocation("ae2fc", "fluid_packet");

    private ProcessingCircuitRecipes() {
    }

    public static Predicate<ItemStack[]> validator(final ItemStack[] inputs, final ItemStack[] outputs,
            final String uid) {
        final List<Recipe> matches = new ArrayList<>();
        if (inputs == null || outputs == null) {
            return candidate -> false;
        }
        final List<ItemStack> items = items(inputs);
        final List<FluidStack> fluids = fluids(inputs);
        for (final GTRecipeCategory category : GTRecipeCategory.getCategories()) {
            if (uid != null && !uid.isEmpty() && !uid.equals(category.getUniqueID())) {
                continue;
            }
            for (final Recipe recipe : category.getRecipeMap().getRecipesByCategory()
                    .getOrDefault(category, Collections.emptyList())) {
                if (sameOutputs(recipe, outputs)) {
                    final Recipe patternRecipe = forPattern(recipe);
                    if (patternRecipe.matches(false, items, fluids)) {
                        matches.add(patternRecipe);
                    }
                }
            }
        }
        // Without a known machine category, require every matching original recipe to accept the change.
        return candidate -> !matches.isEmpty()
                && matches.stream().allMatch(recipe -> recipe.matches(false, items(candidate), fluids(candidate)));
    }

    private static Recipe forPattern(final Recipe recipe) {
        final List<GTRecipeInput> inputs = consumableInputs(recipe.getInputs());
        final List<GTRecipeInput> fluids = consumableInputs(recipe.getFluidInputs());
        if (inputs.size() == recipe.getInputs().size() && fluids.size() == recipe.getFluidInputs().size()) {
            return recipe;
        }
        // HEI omits NC machine tools from patterns; validate only materials the pattern supplies.
        return new Recipe(inputs, recipe.getOutputs(), recipe.getChancedOutputs(), fluids,
                recipe.getFluidOutputs(), recipe.getChancedFluidOutputs(), recipe.getDuration(), recipe.getEUt(),
                recipe.isHidden(), recipe.getIsCTRecipe(), recipe.propertyStorage(), recipe.getRecipeCategory());
    }

    static List<GTRecipeInput> consumableInputs(final List<GTRecipeInput> inputs) {
        return inputs.stream().filter(input -> !input.isNonConsumable()).collect(Collectors.toList());
    }

    private static boolean sameOutputs(final Recipe recipe, final ItemStack[] outputs) {
        return matchesOutputs(recipe.getOutputs(), chancedItems(recipe), recipe.getFluidOutputs(),
                chancedFluids(recipe), outputs);
    }

    /**
     * 样板产物与配方产物的匹配规则（抽出来便于离线自检）。
     * required = 固定产物（必须齐全）；chanced = 概率副产物（GT 的 chanced output，样板里可有可无——
     * 玩家常把副产物从样板里删掉，那不该让配方认不出来，否则电路自动替换会被静默跳过）。
     * 样板里不允许出现配方之外的产物。
     */
    static boolean matchesOutputs(final List<ItemStack> requiredItems, final List<ItemStack> chancedItems,
            final List<FluidStack> requiredFluids, final List<FluidStack> chancedFluids,
            final ItemStack[] outputs) {
        final List<ItemStack> remainingItems = items(outputs);
        for (final ItemStack required : requiredItems) {
            final int index = findItem(remainingItems, required);
            if (index < 0) {
                return false;
            }
            remainingItems.remove(index);
        }
        final List<ItemStack> optionalItems = new ArrayList<>(chancedItems);
        for (final ItemStack extra : remainingItems) {
            final int index = findItem(optionalItems, extra);
            if (index < 0) {
                return false;
            }
            optionalItems.remove(index);
        }
        final List<FluidStack> remainingFluids = fluids(outputs);
        for (final FluidStack required : requiredFluids) {
            final int index = findFluid(remainingFluids, required);
            if (index < 0) {
                return false;
            }
            remainingFluids.remove(index);
        }
        final List<FluidStack> optionalFluids = new ArrayList<>(chancedFluids);
        for (final FluidStack extra : remainingFluids) {
            final int index = findFluid(optionalFluids, extra);
            if (index < 0) {
                return false;
            }
            optionalFluids.remove(index);
        }
        return true;
    }

    private static int findFluid(final List<FluidStack> fluids, final FluidStack expected) {
        for (int i = 0; i < fluids.size(); i++) {
            if (expected.isFluidEqual(fluids.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static List<ItemStack> chancedItems(final Recipe recipe) {
        final List<ItemStack> result = new ArrayList<>();
        for (final ChancedItemOutput entry : recipe.getChancedOutputs().getChancedEntries()) {
            result.add(entry.getIngredient());
        }
        return result;
    }

    private static List<FluidStack> chancedFluids(final Recipe recipe) {
        final List<FluidStack> result = new ArrayList<>();
        for (final ChancedFluidOutput entry : recipe.getChancedFluidOutputs().getChancedEntries()) {
            result.add(entry.getIngredient());
        }
        return result;
    }

    private static int findItem(final List<ItemStack> items, final ItemStack expected) {
        for (int i = 0; i < items.size(); i++) {
            if (ItemStack.areItemsEqual(expected, items.get(i))
                    && ItemStack.areItemStackTagsEqual(expected, items.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static List<ItemStack> items(final ItemStack[] stacks) {
        final List<ItemStack> items = new ArrayList<>();
        for (final ItemStack stack : stacks) {
            if (!stack.isEmpty() && !FLUID_PACKET.equals(stack.getItem().getRegistryName())) {
                items.add(stack.copy());
            }
        }
        return items;
    }

    private static List<FluidStack> fluids(final ItemStack[] stacks) {
        final List<FluidStack> fluids = new ArrayList<>();
        for (final ItemStack stack : stacks) {
            if (!stack.isEmpty() && FLUID_PACKET.equals(stack.getItem().getRegistryName())
                    && stack.hasTagCompound()) {
                final FluidStack fluid = FluidStack.loadFluidStackFromNBT(
                        stack.getTagCompound().getCompoundTag("FluidStack"));
                if (fluid != null) {
                    fluids.add(fluid);
                }
            }
        }
        return fluids;
    }
}
