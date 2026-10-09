package com.extrarawstyle.ae2us.craftstorage;

import appeng.block.crafting.BlockCraftingUnit;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.block.SoundType;
import net.minecraft.creativetab.CreativeTabs;

/**
 * 高级合成存储器方块：继承原版合成存储器的方块基类，沿用多结构成型、右键开 CPU 界面与拆解行为。
 * 容量由瓦片按档位给出；构造时借 STORAGE_1K 这一枚举值过基类构造（不读枚举算容量，视觉全走自定义 blockstate）。
 */
public class BlockAdvancedCraftingStorage extends BlockCraftingUnit {
    public final StorageTier tier;

    public BlockAdvancedCraftingStorage(final StorageTier tier) {
        super(CraftingUnitType.STORAGE_1K);
        this.tier = tier;
        this.setRegistryName(Ae2us.MODID, tier.registryName());
        this.setTranslationKey("ae2us." + tier.registryName());
        this.setHardness(3.0F);
        this.setResistance(6.0F);
        this.setSoundType(SoundType.METAL);
        this.setCreativeTab(CreativeTabs.REDSTONE);
        this.setTileEntity(TileAdvancedCraftingStorage.class);
    }
}
