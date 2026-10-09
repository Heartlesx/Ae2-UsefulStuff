package com.extrarawstyle.ae2us;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public interface PatternCategoryCapture {
    void ae2us$pendingCategory(String uid, String title, String mold, String moldTitle);

    void ae2us$captureTransferredRecipe();

    void ae2us$replaceCircuits();

    NBTTagCompound ae2us$tagEncodedData(NBTTagCompound tag);

    ItemStack ae2us$tagEncodedStack(ItemStack stack);
}
