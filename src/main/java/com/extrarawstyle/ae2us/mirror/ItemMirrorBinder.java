package com.extrarawstyle.ae2us.mirror;

import appeng.helpers.IInterfaceHost;
import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

/**
 * 镜像绑定器。四个动作：
 * <ul>
 *     <li>潜行右键源接口：把该接口设为"卡上选定的源"（替换之前的选定）；</li>
 *     <li>右键镜像：把卡上选定的源接口绑到这个镜像；</li>
 *     <li>潜行右键镜像：把这个镜像与"卡上选定的源"解绑，镜像的其它绑定不受影响；</li>
 *     <li>潜行右键空气：清空卡上的选定。</li>
 * </ul>
 * 走 {@code onItemUseFirst}：AE2 的接口部件会在普通右键里先开 GUI 把点击吃掉。
 */
public final class ItemMirrorBinder extends Item {

    private static final String KEY = "mirrorSource";

    ItemMirrorBinder() {
        this.setRegistryName(Ae2us.MODID, "mirror_binder");
        this.setTranslationKey("ae2us.mirror_binder");
        this.setMaxStackSize(1);
        this.setCreativeTab(appeng.core.CreativeTab.instance != null
                ? appeng.core.CreativeTab.instance : net.minecraft.creativetab.CreativeTabs.REDSTONE);
    }

    @Override
    public EnumActionResult onItemUseFirst(final EntityPlayer player, final World world, final BlockPos pos,
            final EnumFacing side, final float hitX, final float hitY, final float hitZ, final EnumHand hand) {
        final PartMirrorProvider mirror = MirrorBindings.findMirror(world, pos);

        // 平时右键普通接口：不选定也不解绑，交回给 AE2 自己开 GUI。
        // 客户端与服务端必须走同一套判定，一侧 SUCCESS 一侧 PASS 会把接口界面弄没。
        if (!player.isSneaking() && mirror == null) {
            return EnumActionResult.PASS;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        final ItemStack held = player.getHeldItem(hand);
        if (player.isSneaking()) {
            return mirror != null
                    ? this.unbind(player, held, world, pos, mirror)
                    : this.select(player, held, world, pos);
        }
        return this.bind(player, held, world, pos, mirror);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(final World world, final EntityPlayer player, final EnumHand hand) {
        final ItemStack held = player.getHeldItem(hand);
        if (!player.isSneaking()) {
            return super.onItemRightClick(world, player, hand);
        }
        if (!world.isRemote) {
            final boolean had = held.hasTagCompound() && held.getTagCompound().hasKey(KEY);
            if (held.hasTagCompound()) {
                held.getTagCompound().removeTag(KEY);
            }
            player.sendMessage(new TextComponentTranslation(
                    had ? "ae2us.mirror.binder.cleared" : "ae2us.mirror.binder.nothing_selected"));
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }

    /** 潜行右键源接口：记到卡上。 */
    private EnumActionResult select(final EntityPlayer player, final ItemStack held, final World world, final BlockPos pos) {
        final IInterfaceHost host = MirrorBindings.findInterfaceHost(world, pos);
        if (host == null) {
            player.sendMessage(new TextComponentTranslation("ae2us.mirror.binder.not_interface"));
            return EnumActionResult.SUCCESS;
        }
        if (host instanceof PartMirrorProvider) {
            player.sendMessage(new TextComponentTranslation("ae2us.mirror.binder.mirror_as_source"));
            return EnumActionResult.SUCCESS;
        }

        final NBTTagCompound tag = held.hasTagCompound() ? held.getTagCompound() : new NBTTagCompound();
        final NBTTagCompound source = new NBTTagCompound();
        source.setInteger("dim", world.provider.getDimension());
        source.setInteger("x", pos.getX());
        source.setInteger("y", pos.getY());
        source.setInteger("z", pos.getZ());
        tag.setTag(KEY, source);
        held.setTagCompound(tag);

        player.sendMessage(new TextComponentTranslation("ae2us.mirror.binder.selected",
                pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        return EnumActionResult.SUCCESS;
    }

    /** 右键镜像：把卡上选定的源绑上去。 */
    private EnumActionResult bind(final EntityPlayer player, final ItemStack held, final World world,
            final BlockPos pos, final PartMirrorProvider mirror) {
        final NBTTagCompound source = held.hasTagCompound() ? held.getTagCompound().getCompoundTag(KEY) : null;
        if (source == null || source.isEmpty()) {
            player.sendMessage(new TextComponentTranslation("ae2us.mirror.binder.no_selection"));
            return EnumActionResult.SUCCESS;
        }

        final int dimension = source.getInteger("dim");
        final BlockPos sourcePos = new BlockPos(source.getInteger("x"), source.getInteger("y"), source.getInteger("z"));

        if (dimension != world.provider.getDimension()) {
            player.sendMessage(new TextComponentTranslation("ae2us.mirror.binder.wrong_dimension"));
            return EnumActionResult.SUCCESS;
        }
        if (MirrorBindings.findSource(world, sourcePos) == null) {
            player.sendMessage(new TextComponentTranslation("ae2us.mirror.binder.source_gone",
                    sourcePos.getX() + ", " + sourcePos.getY() + ", " + sourcePos.getZ()));
            return EnumActionResult.SUCCESS;
        }
        if (mirror.isBoundTo(dimension, sourcePos)) {
            player.sendMessage(new TextComponentTranslation("ae2us.mirror.binder.already_bound",
                    sourcePos.getX() + ", " + sourcePos.getY() + ", " + sourcePos.getZ()));
            return EnumActionResult.SUCCESS;
        }

        mirror.bind(dimension, sourcePos);
        player.sendMessage(new TextComponentTranslation("ae2us.mirror.binder.bound",
                sourcePos.getX() + ", " + sourcePos.getY() + ", " + sourcePos.getZ()));
        return EnumActionResult.SUCCESS;
    }

    /** 潜行右键镜像：只解掉卡上选定的那个源。 */
    private EnumActionResult unbind(final EntityPlayer player, final ItemStack held, final World world,
            final BlockPos pos, final PartMirrorProvider mirror) {
        final NBTTagCompound source = held.hasTagCompound() ? held.getTagCompound().getCompoundTag(KEY) : null;
        if (source == null || source.isEmpty()) {
            player.sendMessage(new TextComponentTranslation("ae2us.mirror.binder.unbind_no_selection"));
            return EnumActionResult.SUCCESS;
        }

        final int dimension = source.getInteger("dim");
        final BlockPos sourcePos = new BlockPos(source.getInteger("x"), source.getInteger("y"), source.getInteger("z"));

        if (mirror.unbind(dimension, sourcePos)) {
            player.sendMessage(new TextComponentTranslation("ae2us.mirror.binder.unbound",
                    sourcePos.getX() + ", " + sourcePos.getY() + ", " + sourcePos.getZ()));
        } else {
            player.sendMessage(new TextComponentTranslation("ae2us.mirror.binder.not_bound",
                    sourcePos.getX() + ", " + sourcePos.getY() + ", " + sourcePos.getZ()));
        }
        return EnumActionResult.SUCCESS;
    }
}
