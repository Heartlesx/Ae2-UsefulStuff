package com.extrarawstyle.ae2us;

import appeng.container.ContainerNull;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import java.util.function.Predicate;

public final class GenericCircuitSubstitution {
    private static final ResourceLocation GENERIC_ITEM = new ResourceLocation("gtlitecore", "gtlite_meta_item_1");
    private static final String[] TIERS = {"Ulv", "Lv", "Mv", "Hv", "Ev", "Iv", "Luv", "Zpm",
            "Uv", "Uhv", "Uev", "Uiv", "Uxv", "Opv", "Max"};

    private GenericCircuitSubstitution() {
    }

    public static boolean isGeneric(final ItemStack stack) {
        return !stack.isEmpty() && GENERIC_ITEM.equals(stack.getItem().getRegistryName())
                && stack.getMetadata() >= 286 && stack.getMetadata() <= 300;
    }

    public static ItemStack replacement(final ItemStack stack) {
        if (stack.isEmpty() || isGeneric(stack)) {
            return stack;
        }
        for (final int ore : OreDictionary.getOreIDs(stack)) {
            for (int tier = 0; tier < TIERS.length; tier++) {
                if (!OreDictionary.getOreName(ore).equals("circuit" + TIERS[tier])) {
                    continue;
                }
                for (final ItemStack generic : OreDictionary.getOres("circuit" + TIERS[tier], false)) {
                    if (isGeneric(generic) && generic.getMetadata() == 286 + tier) {
                        final ItemStack result = generic.copy();
                        result.setCount(stack.getCount());
                        return result;
                    }
                }
            }
        }
        return stack;
    }

    public static ItemStack[] replace(final ItemStack[] inputs, final ItemStack[] outputs,
            final Predicate<ItemStack[]> valid) {
        if (inputs == null || outputs == null) {
            return inputs;
        }
        for (final ItemStack output : outputs) {
            if (isGeneric(output)) {
                return inputs;
            }
        }
        final ItemStack[] result = inputs.clone();
        boolean changed = false;
        for (int slot = 0; slot < inputs.length; slot++) {
            result[slot] = replacement(inputs[slot]);
            changed |= result[slot] != inputs[slot];
        }
        if (!changed) {
            return inputs;
        }
        if (valid.test(result)) {
            return result;
        }
        // A recipe may accept a tier circuit in one slot but require an exact model in another.
        System.arraycopy(inputs, 0, result, 0, inputs.length);
        for (int slot = 0; slot < inputs.length; slot++) {
            final ItemStack candidate = replacement(inputs[slot]);
            if (candidate != inputs[slot]) {
                result[slot] = candidate;
                if (!valid.test(result)) {
                    result[slot] = inputs[slot];
                }
            }
        }
        return result;
    }

    public static Predicate<ItemStack[]> craftingValidator(final ItemStack[] inputs,
            final ItemStack[] outputs, final World world) {
        if (inputs == null || inputs.length != 9 || outputs == null || outputs.length != 1) {
            return candidate -> false;
        }
        final IRecipe recipe = CraftingManager.findMatchingRecipe(matrix(inputs), world);
        return candidate -> {
            final InventoryCrafting grid = matrix(candidate);
            return recipe != null && recipe.matches(grid, world)
                    && ItemStack.areItemStacksEqual(outputs[0], recipe.getCraftingResult(grid));
        };
    }

    private static InventoryCrafting matrix(final ItemStack[] inputs) {
        final InventoryCrafting grid = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int slot = 0; slot < inputs.length; slot++) {
            grid.setInventorySlotContents(slot, inputs[slot].copy());
        }
        return grid;
    }
}
