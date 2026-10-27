package com.extrarawstyle.ae2us.mirror;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.helpers.IInterfaceHost;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * 镜像样板供应器绑定了哪些源接口，以及怎么把一个坐标解析成源接口。
 * <p>
 * 源接口只认 {@link IInterfaceHost}：AE2 的接口方块、接口面板，以及 Fluid Craft for AE2 的二合一接口
 * （它的方块版与面板版都实现了这个接口），一套判据全覆盖。
 * 镜像自己不接受被绑定，以免绑成环。
 */
public final class MirrorBindings {

    private static final String KEY = "mirrorBindings";

    private MirrorBindings() {
    }

    /** 一条绑定：哪个维度的哪个坐标。 */
    public static final class Binding {
        public final int dimension;
        public final BlockPos pos;

        Binding(final int dimension, final BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }

        boolean matches(final int dim, final BlockPos other) {
            return this.dimension == dim && this.pos.equals(other);
        }
    }

    public static List<Binding> read(final NBTTagCompound tag) {
        final List<Binding> list = new ArrayList<>();
        final NBTTagList entries = tag.getTagList(KEY, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < entries.tagCount(); i++) {
            final NBTTagCompound entry = entries.getCompoundTagAt(i);
            list.add(new Binding(entry.getInteger("dim"),
                    new BlockPos(entry.getInteger("x"), entry.getInteger("y"), entry.getInteger("z"))));
        }
        return list;
    }

    public static void write(final NBTTagCompound tag, final List<Binding> list) {
        final NBTTagList entries = new NBTTagList();
        for (final Binding binding : list) {
            final NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("dim", binding.dimension);
            entry.setInteger("x", binding.pos.getX());
            entry.setInteger("y", binding.pos.getY());
            entry.setInteger("z", binding.pos.getZ());
            entries.appendTag(entry);
        }
        tag.setTag(KEY, entries);
    }

    /** 这个方块上有没有镜像部件。 */
    public static PartMirrorProvider findMirror(final World world, final BlockPos pos) {
        final TileEntity tile = tileAt(world, pos);
        if (!(tile instanceof IPartHost)) {
            return null;
        }
        final IPartHost host = (IPartHost) tile;
        // 只遍历六个侧面：AEPartLocation.INTERNAL 是线缆本体，传 null 还会 NPE。
        for (final AEPartLocation side : AEPartLocation.SIDE_LOCATIONS) {
            final IPart part = host.getPart(side);
            if (part instanceof PartMirrorProvider) {
                return (PartMirrorProvider) part;
            }
        }
        return null;
    }

    /**
     * 把这个坐标解析成一个可以镜像的源接口。镜像本身不算源（防环），
     * 所以拿到的是镜像时返回 null。
     */
    public static IInterfaceHost findSource(final World world, final BlockPos pos) {
        final IInterfaceHost host = findInterfaceHost(world, pos);
        return host instanceof PartMirrorProvider ? null : host;
    }

    /** 坐标上无论是方块接口还是面板接口，都取成 IInterfaceHost。 */
    public static IInterfaceHost findInterfaceHost(final World world, final BlockPos pos) {
        final TileEntity tile = tileAt(world, pos);
        if (tile == null) {
            return null;
        }
        if (tile instanceof IInterfaceHost) {
            return (IInterfaceHost) tile;
        }
        if (tile instanceof IPartHost) {
            final IPartHost host = (IPartHost) tile;
            for (final AEPartLocation side : AEPartLocation.SIDE_LOCATIONS) {
                final IPart part = host.getPart(side);
                if (part instanceof IInterfaceHost) {
                    return (IInterfaceHost) part;
                }
            }
        }
        return null;
    }

    private static TileEntity tileAt(final World world, final BlockPos pos) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) {
            return null;
        }
        return world.getTileEntity(pos);
    }
}
