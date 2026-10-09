package com.extrarawstyle.ae2us.mixin;

import com.extrarawstyle.ae2us.PatternCategory;
import com.extrarawstyle.ae2us.PatternCategoryCapture;
import com.extrarawstyle.ae2us.GenericCircuitSubstitution;
import com.extrarawstyle.ae2us.ProcessingCircuitRecipes;

import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.slot.SlotFakeCraftingMatrix;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.function.Predicate;

@Mixin(value = ContainerPatternEncoder.class, remap = false)
public abstract class PatternEncoderCategoryMixin implements PatternCategoryCapture {
    @Shadow protected abstract ItemStack[] getInputs();
    @Shadow protected abstract ItemStack[] getOutputs();
    @Shadow protected SlotFakeCraftingMatrix[] craftingSlots;

    @Unique private String ae2us$pendingUid;
    @Unique private String ae2us$pendingTitle;
    @Unique private String ae2us$pendingMold = "";
    @Unique private String ae2us$pendingMoldTitle = "";
    @Unique private String ae2us$uid;
    @Unique private String ae2us$title;
    @Unique private ItemStack[] ae2us$inputs;
    @Unique private ItemStack[] ae2us$outputs;
    @Unique private boolean ae2us$crafting;

    @Override
    public void ae2us$pendingCategory(final String uid, final String title, final String mold, final String moldTitle) {
        ae2us$pendingUid = uid;
        ae2us$pendingTitle = title;
        ae2us$pendingMold = mold;
        ae2us$pendingMoldTitle = moldTitle;
        ae2us$clearSnapshot();
    }

    @Override
    public void ae2us$captureTransferredRecipe() {
        ae2us$replaceCircuits();
        if (ae2us$pendingUid == null) {
            return;
        }
        final ItemStack[] inputs = getInputs();
        final ItemStack[] outputs = getOutputs();
        if (inputs != null && outputs != null) {
            ae2us$uid = ae2us$pendingUid;
            ae2us$title = ae2us$pendingTitle;
            ae2us$inputs = ae2us$copy(inputs);
            ae2us$outputs = ae2us$copy(outputs);
            ae2us$crafting = ((ContainerPatternEncoder) (Object) this).isCraftingMode();
            LogManager.getLogger("AE2US").debug("HEI category captured: {}", ae2us$uid);
        }
    }

    @Inject(method = "encode", at = @At("HEAD"))
    private void ae2us$replaceBeforeEncoding(final CallbackInfo ci) {
        ae2us$replaceCircuits();
    }

    @Override
    public void ae2us$replaceCircuits() {
        final ContainerPatternEncoder container = (ContainerPatternEncoder) (Object) this;
        if (container.getPlayerInv().player.world.isRemote) {
            return;
        }
        final ItemStack[] inputs = getInputs();
        if (inputs == null || Arrays.stream(inputs)
                .noneMatch(stack -> GenericCircuitSubstitution.replacement(stack) != stack)) {
            return;
        }
        final ItemStack[] outputs = getOutputs();
        if (outputs == null || Arrays.stream(outputs).anyMatch(GenericCircuitSubstitution::isGeneric)) {
            return;
        }
        final Predicate<ItemStack[]> valid;
        if (container.isCraftingMode()) {
            valid = GenericCircuitSubstitution.craftingValidator(inputs, outputs, container.getPlayerInv().player.world);
        } else if (Loader.isModLoaded("gregtech")) {
            valid = ProcessingCircuitRecipes.validator(inputs, outputs, ae2us$pendingUid);
        } else {
            return;
        }
        final ItemStack[] replaced = GenericCircuitSubstitution.replace(inputs, outputs, valid);
        int changed = 0;
        for (int slot = 0; slot < inputs.length; slot++) {
            if (!ItemStack.areItemStacksEqual(inputs[slot], replaced[slot])) {
                craftingSlots[slot].putStack(replaced[slot]);
                changed++;
            }
        }
        if (changed > 0) {
            getOutputs();
            container.detectAndSendChanges();
            LogManager.getLogger("AE2US").debug("Replaced {} circuit slots with generic circuits", changed);
        }
    }

    @ModifyArg(method = "encode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/ItemStack;func_77982_d(Lnet/minecraft/nbt/NBTTagCompound;)V"), index = 0)
    private NBTTagCompound ae2us$tagEncodedPattern(final NBTTagCompound tag) {
        return ae2us$tagEncodedData(tag);
    }

    @Override
    public NBTTagCompound ae2us$tagEncodedData(final NBTTagCompound tag) {
        if (ae2us$inputs != null && ae2us$crafting == ((ContainerPatternEncoder) (Object) this).isCraftingMode()
                && PatternCategory.stillSameRecipe(ae2us$inputs, getInputs(), ae2us$outputs, getOutputs(), true)) {
            // Keep recording recipe edits in the existing NBT key, including output counts.
            final boolean modifiedInputs = !PatternCategory.matches(ae2us$inputs, getInputs(), false)
                    || !PatternCategory.matches(ae2us$outputs, getOutputs(), false);
            PatternCategory.write(tag, ae2us$uid, ae2us$title, modifiedInputs,
                    ae2us$pendingMold, ae2us$pendingMoldTitle);
            LogManager.getLogger("AE2US").debug("Encoded pattern tagged: {} modifiedInputs={}",
                    ae2us$uid, modifiedInputs);
        } else if (ae2us$inputs != null) {
            // 产物数组是紧凑的（只含非空槽），排查时把两侧数量与两侧匹配结果一起打出来。
            LogManager.getLogger("AE2US").debug(
                    "Encoded pattern category skipped: {} modeMatches={} outputsMatch={} inputsMatch={} (capturedOutputs={} nowOutputs={})",
                    ae2us$uid, ae2us$crafting == ((ContainerPatternEncoder) (Object) this).isCraftingMode(),
                    PatternCategory.matchesAfterTrimming(ae2us$outputs, getOutputs(), true),
                    PatternCategory.matchesAfterTrimming(ae2us$inputs, getInputs(), true),
                    ae2us$outputs == null ? -1 : ae2us$outputs.length,
                    getOutputs() == null ? -1 : getOutputs().length);
        }
        return tag;
    }

    @Override
    public ItemStack ae2us$tagEncodedStack(final ItemStack stack) {
        if (!stack.isEmpty() && stack.hasTagCompound()) {
            ae2us$tagEncodedData(stack.getTagCompound());
        }
        return stack;
    }

    @Unique
    private void ae2us$clearSnapshot() {
        ae2us$uid = null;
        ae2us$title = null;
        ae2us$inputs = null;
        ae2us$outputs = null;
    }

    @Unique
    private static ItemStack[] ae2us$copy(final ItemStack[] stacks) {
        final ItemStack[] copy = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            copy[i] = stacks[i].copy();
        }
        return copy;
    }

}
