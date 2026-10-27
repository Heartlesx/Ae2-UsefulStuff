package com.extrarawstyle.ae2us.maintainer;

import appeng.api.AEApi;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * ME库存维持器方块：无朝向立方体，显示离线与有订单在跑两态。
 * 拆卸时把 32 条规则与当前订单识别信息写进掉落物，重新放置后恢复。
 */
public class BlockStockMaintainer extends BlockContainer {
    public static final PropertyBool LIT = PropertyBool.create("lit");
    public static final PropertyBool WORKING = PropertyBool.create("working");

    public BlockStockMaintainer() {
        super(Material.IRON);
        this.setTranslationKey("ae2us.me_stock_maintainer");
        this.setHardness(3.0F);
        this.setResistance(6.0F);
        this.setSoundType(SoundType.METAL);
        this.setCreativeTab(CreativeTabs.REDSTONE);
        this.setDefaultState(this.blockState.getBaseState().withProperty(LIT, false).withProperty(WORKING, false));
        this.setRegistryName(Ae2us.MODID, "me_stock_maintainer");
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int meta) {
        return new TileStockMaintainer();
    }

    @Override
    public EnumBlockRenderType getRenderType(final IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean onBlockActivated(final World world, final BlockPos pos, final IBlockState state,
                                    final EntityPlayer player, final EnumHand hand, final EnumFacing side,
                                    final float hitX, final float hitY, final float hitZ) {
        if (player.isSneaking()) { return false; }
        if (!world.isRemote) {
            player.openGui(Ae2us.INSTANCE, StockMaintainerRegistration.GUI_ID, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(final World world, final BlockPos pos, final IBlockState state,
                                final EntityLivingBase placer, final ItemStack stack) {
        final TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileStockMaintainer) { ((TileStockMaintainer) tile).readConfig(stack); }
    }

    @Override
    public void harvestBlock(final World world, final EntityPlayer player, final BlockPos pos,
                             final IBlockState state, final TileEntity tile, final ItemStack tool) {
        if (tile instanceof TileStockMaintainer && ((TileStockMaintainer) tile).book().configuredCount() > 0) {
            final ItemStack drop = new ItemStack(this);
            ((TileStockMaintainer) tile).writeConfig(drop);
            this.spawnAsEntity(world, pos, drop);
            return;
        }
        super.harvestBlock(world, player, pos, state, tile, tool);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LIT, WORKING);
    }

    @Override
    public IBlockState getStateFromMeta(final int meta) {
        return this.getDefaultState().withProperty(LIT, (meta & 1) != 0).withProperty(WORKING, (meta & 2) != 0);
    }

    @Override
    public int getMetaFromState(final IBlockState state) {
        return (state.getValue(LIT) ? 1 : 0) | (state.getValue(WORKING) ? 2 : 0);
    }

    /** 配方材料用 AE2 definitions 取，不硬编码旧 metadata。 */
    static ItemStack[] recipeInputs() {
        return new ItemStack[]{
                AEApi.instance().definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY),
                AEApi.instance().definitions().parts().levelEmitter().maybeStack(1).orElse(ItemStack.EMPTY),
                AEApi.instance().definitions().materials().cardCrafting().maybeStack(1).orElse(ItemStack.EMPTY),
                AEApi.instance().definitions().materials().engProcessor().maybeStack(1).orElse(ItemStack.EMPTY)
        };
    }
}
