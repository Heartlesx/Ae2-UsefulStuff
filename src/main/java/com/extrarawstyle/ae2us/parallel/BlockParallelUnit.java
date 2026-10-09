package com.extrarawstyle.ae2us.parallel;

import appeng.block.crafting.BlockCraftingUnit;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.block.SoundType;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 高级并行处理单元方块：继承原版并行处理单元方块，从而沿用多结构成型、右键开 CPU 界面与拆解行为；
 * "一块顶 N 块"的计数在瓦片与 CraftingCPUClusterMixin 里。
 * <p>
 * 外观：三态贴图走 blockstate——未成型（中性轨）、成型未通电（弱色轨）、成型通电（亮色轨）；
 * 斜轨图案在 16×16 内自带平铺相位，相邻方块之间自然连成连续线路，不需要额外渲染代码。
 */
public class BlockParallelUnit extends BlockCraftingUnit {
    public final ParallelTier tier;

    public BlockParallelUnit(final ParallelTier tier) {
        super(CraftingUnitType.ACCELERATOR);
        this.tier = tier;
        this.setRegistryName(Ae2us.MODID, tier.registryName());
        this.setTranslationKey("ae2us." + tier.registryName());
        this.setHardness(3.0F);
        this.setResistance(6.0F);
        this.setSoundType(SoundType.METAL);
        this.setCreativeTab(CreativeTabs.REDSTONE);
        this.setTileEntity(TileParallelUnit.class);
    }

    @Override
    public void addInformation(final ItemStack stack, @Nullable final World world, final List<String> tooltip,
                               final net.minecraft.client.util.ITooltipFlag flag) {
        tooltip.add(I18n.translateToLocalFormatted("ae2us.parallel_unit.tooltip", this.tier.parallels()));
    }
}
