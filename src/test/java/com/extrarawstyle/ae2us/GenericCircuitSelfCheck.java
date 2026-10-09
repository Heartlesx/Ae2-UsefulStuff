package com.extrarawstyle.ae2us;

import gregtech.api.recipes.ingredients.GTRecipeInput;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public final class GenericCircuitSelfCheck {
    private GenericCircuitSelfCheck() {
    }

    public static void main(final String[] args) {
        Bootstrap.register();
        final Item generic = new Item().setHasSubtypes(true).setRegistryName("gtlitecore", "gtlite_meta_item_1");
        final Item concrete = new Item().setHasSubtypes(true).setRegistryName("ae2us", "test_circuits");
        // Bootstrap freezes registries; this isolated JVM needs registered test items and recipes.
        ((ForgeRegistry<Item>) ForgeRegistries.ITEMS).unfreeze();
        ForgeRegistries.ITEMS.register(generic);
        ForgeRegistries.ITEMS.register(concrete);
        ((ForgeRegistry<Item>) ForgeRegistries.ITEMS).freeze();
        final String[] tiers = {"Ulv", "Lv", "Mv", "Hv", "Ev", "Iv", "Luv", "Zpm",
                "Uv", "Uhv", "Uev", "Uiv", "Uxv", "Opv", "Max"};
        for (int tier = 0; tier < tiers.length; tier++) {
            OreDictionary.registerOre("circuit" + tiers[tier], new ItemStack(concrete, 1, tier));
            OreDictionary.registerOre("circuit" + tiers[tier], new ItemStack(generic, 1, 286 + tier));
            final ItemStack original = new ItemStack(concrete, 7, tier);
            final ItemStack replacement = GenericCircuitSubstitution.replacement(original);
            check(replacement.getItem() == generic && replacement.getMetadata() == 286 + tier
                    && replacement.getCount() == 7, "same tier and quantity: " + tiers[tier]);
            check(original.getItem() == concrete && original.getCount() == 7, "original not mutated");
            check(GenericCircuitSubstitution.replacement(replacement) == replacement,
                    "generic circuit is already normalized");
        }

        final ItemStack ev = new ItemStack(concrete, 2, 4);
        final ItemStack lv = new ItemStack(concrete, 3, 1);
        final ItemStack board = new ItemStack(concrete, 1, 50);
        OreDictionary.registerOre("circuitBoard", board);
        final ItemStack programmed = new ItemStack(concrete, 1, 51);
        OreDictionary.registerOre("circuitIntegrated", programmed);
        check(GenericCircuitSubstitution.replacement(board) == board, "ordinary circuit board unchanged");
        check(GenericCircuitSubstitution.replacement(programmed) == programmed, "programmed circuit unchanged");
        check(GenericCircuitSubstitution.replacement(ItemStack.EMPTY).isEmpty(), "empty slot unchanged");

        final ItemStack[] inputs = {ev, lv, board, programmed, ItemStack.EMPTY};
        final ItemStack[] outputs = {new ItemStack(Items.IRON_INGOT)};
        final ItemStack[] replaced = GenericCircuitSubstitution.replace(inputs, outputs, candidate -> true);
        check(replaced[0].getMetadata() == 290 && replaced[1].getMetadata() == 287,
                "mixed EV and LV inputs keep independent tiers");
        check(replaced[2] == board && replaced[3] == programmed && replaced[4].isEmpty(),
                "unrelated input slots unchanged");
        final ItemStack[] rejected = GenericCircuitSubstitution.replace(inputs, outputs, candidate -> false);
        check(Arrays.equals(inputs, rejected), "invalid or unverified recipes preserve every original input");
        final ItemStack[] partial = GenericCircuitSubstitution.replace(inputs, outputs,
                candidate -> candidate[1].getItem() == concrete);
        check(partial[0].getItem() == generic && partial[1] == lv,
                "ore circuit changes while exact-model slot remains original");
        check(GenericCircuitSubstitution.replace(inputs, new ItemStack[]{new ItemStack(generic, 1, 290)},
                candidate -> { throw new AssertionError("conversion must not attempt substitution"); }) == inputs,
                "generic circuit production patterns are exempt");

        final GTRecipeInput consumedCircuit = new TestRecipeInput(2);
        final GTRecipeInput programmedTool = new TestRecipeInput(1).setNonConsumable();
        final GTRecipeInput consumedMaterial = new TestRecipeInput(4);
        final List<GTRecipeInput> fullMachineInputs = Arrays.asList(consumedMaterial, consumedCircuit, programmedTool);
        check(fullMachineInputs.size() > 2, "reproduce: full machine input requirements include an omitted NC tool");
        final List<GTRecipeInput> patternInputs = ProcessingCircuitRecipes.consumableInputs(fullMachineInputs);
        check(patternInputs.equals(Arrays.asList(consumedMaterial, consumedCircuit)),
                "LV emitter/drone pattern validates its materials without requiring the omitted NC circuit");
        check(patternInputs.get(0).getAmount() == 4 && patternInputs.get(1).getAmount() == 2,
                "consumed quantities remain strict when NC inputs are excluded");
        check(fullMachineInputs.size() == 3 && programmedTool.isNonConsumable(),
                "original machine recipe requirements are not changed");
        check(ProcessingCircuitRecipes.consumableInputs(Arrays.asList(consumedMaterial, consumedCircuit))
                .equals(Arrays.asList(consumedMaterial, consumedCircuit)), "recipes without NC inputs unchanged");
        check(ProcessingCircuitRecipes.consumableInputs(Arrays.asList(programmedTool)).isEmpty(),
                "nonconsumable fluid/tool input is excluded by the same rule");

        final ResourceLocation recipeId = new ResourceLocation("ae2us", "test_ev_machine");
        final ShapedOreRecipe recipe = new ShapedOreRecipe(recipeId, new ItemStack(Items.GOLD_INGOT),
                "C", 'C', "circuitEv");
        recipe.setRegistryName(recipeId);
        ((ForgeRegistry<IRecipe>) ForgeRegistries.RECIPES).unfreeze();
        ForgeRegistries.RECIPES.register(recipe);
        final ItemStack[] grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        grid[0] = new ItemStack(concrete, 1, 4);
        final ItemStack[] machineOutput = {new ItemStack(Items.GOLD_INGOT)};
        final Predicate<ItemStack[]> valid = GenericCircuitSubstitution.craftingValidator(grid, machineOutput, null);
        final ItemStack[] crafted = GenericCircuitSubstitution.replace(grid, machineOutput, valid);
        check(crafted[0].getItem() == generic && crafted[0].getMetadata() == 290,
                "actual crafting recipe accepts generic EV circuit and preserves output");
        check(!GenericCircuitSubstitution.craftingValidator(grid,
                new ItemStack[]{new ItemStack(Items.DIAMOND)}, null).test(crafted),
                "changed crafting output is rejected");
        final ResourceLocation exactId = new ResourceLocation("ae2us", "test_exact_lv");
        final ShapedOreRecipe exactRecipe = new ShapedOreRecipe(exactId, new ItemStack(Items.DIAMOND),
                "C", 'C', new ItemStack(concrete, 1, 1));
        exactRecipe.setRegistryName(exactId);
        ForgeRegistries.RECIPES.register(exactRecipe);
        ((ForgeRegistry<IRecipe>) ForgeRegistries.RECIPES).freeze();
        grid[0] = new ItemStack(concrete, 1, 1);
        final ItemStack[] exactOutput = {new ItemStack(Items.DIAMOND)};
        check(GenericCircuitSubstitution.replace(grid, exactOutput,
                GenericCircuitSubstitution.craftingValidator(grid, exactOutput, null))[0] == grid[0],
                "actual exact-model recipe is not rewritten");

        // 处理配方的产物匹配：固定产物必须齐全；概率副产物（GT chanced output）可以被玩家从样板里删掉，
        // 删掉之后仍要能认出配方，否则"取消副产物"会让电路自动替换被静默跳过。
        final List<ItemStack> requiredItems = Arrays.asList(new ItemStack(Items.IRON_INGOT));
        final List<ItemStack> chancedItems = Arrays.asList(new ItemStack(Items.GOLD_NUGGET));
        final List<FluidStack> noFluids = java.util.Collections.emptyList();
        check(ProcessingCircuitRecipes.matchesOutputs(requiredItems, chancedItems, noFluids, noFluids,
                        new ItemStack[]{new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_NUGGET)}),
                "固定产物与副产物齐全时认出配方");
        check(ProcessingCircuitRecipes.matchesOutputs(requiredItems, chancedItems, noFluids, noFluids,
                        new ItemStack[]{new ItemStack(Items.IRON_INGOT)}),
                "样板取消副产物后仍认出配方（电路自动替换不被跳过）");
        check(!ProcessingCircuitRecipes.matchesOutputs(requiredItems, chancedItems, noFluids, noFluids,
                        new ItemStack[]{new ItemStack(Items.GOLD_NUGGET)}), "缺固定产物不认");
        check(!ProcessingCircuitRecipes.matchesOutputs(requiredItems, chancedItems, noFluids, noFluids,
                        new ItemStack[]{new ItemStack(Items.IRON_INGOT), new ItemStack(Items.DIAMOND)}),
                "掺入配方外的产物不认");
        final Item fluidPacketItem = new Item().setRegistryName("ae2fc", "fluid_packet");
        final List<ItemStack> noItems = java.util.Collections.emptyList();
        final List<FluidStack> requiredFluids = Arrays.asList(new FluidStack(FluidRegistry.WATER, 1000));
        final List<FluidStack> chancedFluids = Arrays.asList(new FluidStack(FluidRegistry.LAVA, 500));
        check(ProcessingCircuitRecipes.matchesOutputs(noItems, noItems, requiredFluids, chancedFluids,
                        new ItemStack[]{fluidPacket(fluidPacketItem, "water", 1000),
                                fluidPacket(fluidPacketItem, "lava", 500)}),
                "流体固定产物与副产物齐全时认出配方");
        check(ProcessingCircuitRecipes.matchesOutputs(noItems, noItems, requiredFluids, chancedFluids,
                        new ItemStack[]{fluidPacket(fluidPacketItem, "water", 1000)}),
                "样板取消流体副产物后仍认出配方");
        check(!ProcessingCircuitRecipes.matchesOutputs(noItems, noItems, requiredFluids, chancedFluids,
                        new ItemStack[]{fluidPacket(fluidPacketItem, "lava", 500)}), "缺流体固定产物不认");
        System.out.println("Generic circuit checks passed (15 tiers, quantities, exemptions, crafting validity"
                + " and byproduct-tolerant recipe outputs)");
    }

    private static ItemStack fluidPacket(final Item item, final String fluid, final int amount) {
        final ItemStack stack = new ItemStack(item);
        final NBTTagCompound tag = new NBTTagCompound();
        final NBTTagCompound fluidTag = new NBTTagCompound();
        fluidTag.setString("FluidName", fluid);
        fluidTag.setInteger("Amount", amount);
        tag.setTag("FluidStack", fluidTag);
        stack.setTagCompound(tag);
        return stack;
    }

    private static void check(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static final class TestRecipeInput extends GTRecipeInput {
        private TestRecipeInput(final int count) {
            amount = count;
        }

        @Override protected GTRecipeInput copy() { return copyWithAmount(amount); }
        @Override public GTRecipeInput copyWithAmount(final int count) { return new TestRecipeInput(count); }
        @Override protected int computeHash() { return amount; }
        @Override public boolean equals(final Object other) { return this == other; }
        @Override public boolean equalIgnoreAmount(final GTRecipeInput other) { return this == other; }
    }
}
