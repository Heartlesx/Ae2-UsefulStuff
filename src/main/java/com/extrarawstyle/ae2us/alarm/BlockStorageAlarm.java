package com.extrarawstyle.ae2us.alarm;

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

import javax.annotation.Nullable;

/** 库存报警器方块：无朝向立方体，显示离线、运行中与缺货三态；配置随掉落物保留。 */
public class BlockStorageAlarm extends BlockContainer {
    public static final PropertyBool LIT = PropertyBool.create("lit");
    public static final PropertyBool ALERT = PropertyBool.create("alert");

    public BlockStorageAlarm() {
        super(Material.IRON);
        this.setTranslationKey("ae2us.me_storage_alarm");
        this.setHardness(3.0F);
        this.setResistance(6.0F);
        this.setSoundType(SoundType.METAL);
        this.setCreativeTab(CreativeTabs.REDSTONE);
        this.setDefaultState(this.blockState.getBaseState().withProperty(LIT, false).withProperty(ALERT, false));
        this.setRegistryName(Ae2us.MODID, "me_storage_alarm");
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int meta) {
        return new TileStorageAlarm();
    }

    @Override
    public EnumBlockRenderType getRenderType(final IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean onBlockActivated(final World world, final BlockPos pos, final IBlockState state,
                                    final EntityPlayer player, final EnumHand hand, final EnumFacing side,
                                    final float hitX, final float hitY, final float hitZ) {
        if (!player.isSneaking()) {
            if (!world.isRemote) {
                player.openGui(Ae2us.INSTANCE, AlarmRegistration.GUI_ID, world, pos.getX(), pos.getY(), pos.getZ());
            }
            return true;
        }
        return false;
    }

    @Override
    public void onBlockPlacedBy(final World world, final BlockPos pos, final IBlockState state,
                                final EntityLivingBase placer, final ItemStack stack) {
        final TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileStorageAlarm) { ((TileStorageAlarm) tile).readConfig(stack); }
    }

    @Override
    public void harvestBlock(final World world, final EntityPlayer player, final BlockPos pos,
                             final IBlockState state, @Nullable final TileEntity tile, final ItemStack tool) {
        if (tile instanceof TileStorageAlarm && ((TileStorageAlarm) tile).configuredCount() > 0) {
            final ItemStack drop = new ItemStack(this);
            ((TileStorageAlarm) tile).writeConfig(drop);
            this.spawnAsEntity(world, pos, drop);
            return;
        }
        super.harvestBlock(world, player, pos, state, tile, tool);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LIT, ALERT);
    }

    @Override
    public IBlockState getStateFromMeta(final int meta) {
        return this.getDefaultState().withProperty(LIT, (meta & 1) != 0).withProperty(ALERT, (meta & 2) != 0);
    }

    @Override
    public int getMetaFromState(final IBlockState state) {
        return (state.getValue(LIT) ? 1 : 0) | (state.getValue(ALERT) ? 2 : 0);
    }
}
