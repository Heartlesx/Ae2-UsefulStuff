package com.extrarawstyle.ae2us.mixin;

import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.slot.SlotRestrictedInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ContainerPatternEncoder.class, remap = false)
public interface PatternEncoderAccessor {

    @Accessor("patternSlotOUT")
    SlotRestrictedInput ae2us$getPatternOutputSlot();
}
