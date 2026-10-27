package com.extrarawstyle.ae2us.maintainer.fluid;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.fluids.util.AEFluidStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

/**
 * 流体条目的公共部分。这里只用 AE2UEL 自带的流体类型（IAEFluidStack / AEFluidStack /
 * IFluidStorageChannel），不引用任何 AE2FC 类，所以：
 * - 未安装 AE2FC 时，已保存的流体规则仍能正常读出、显示与存档，不会静默丢配置；
 * - 此时流体规则只差“没有 AE2FC 流体离散器”这一个环境条件，界面会明确提示。
 *
 * 真正依赖 AE2FC 的两件事（虚拟 drop 合成请求、离散器探测）在 Ae2fcFluidAdapter 里，
 * 由 {@link #ae2fcPresent()} 判定后才触达。
 *
 * 硬性约束：不要用 Class.forName 探测模组在场——加载失败会把类名永久写进
 * LaunchClassLoader 的 invalidClasses，之后同类加载直接抛无 cause 的 CNFE。
 */
public final class FluidMaintainerBridge {
    private static Boolean present;

    private FluidMaintainerBridge() { }

    /** AE2FC 是否已加载。只查 FML 的模组清单，不做类加载探测。 */
    public static boolean ae2fcPresent() {
        if (present == null) {
            try {
                present = net.minecraftforge.fml.common.Loader.isModLoaded("ae2fc");
            } catch (final Throwable notReady) {
                // Loader 未初始化时不要缓存否定结果，等下次再问。
                return false;
            }
        }
        return true && present;
    }

    /** 流体存储通道：AE2UEL 自带，恒定可用。 */
    public static IStorageChannel<?> channel() {
        return appeng.api.AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
    }

    /**
     * 从网络流体清单里取该目标的实际存量（mB）；目标不是流体时返回 -1。
     * 身份按注册名 + NBT 判定，数量不参与——和 AE 自己的流体堆比较口径一致，
     * 但不走 AEFluidStack.equals（它依赖 FML 平台比较器）。
     */
    public static long stored(final Iterable<?> fluids, final Object target) {
        if (!(target instanceof IAEFluidStack) || fluids == null) { return -1L; }
        final IAEFluidStack wanted = (IAEFluidStack) target;
        final FluidStack wantedFluid = wanted.getFluidStack();
        if (wantedFluid == null || wantedFluid.getFluid() == null) { return -1L; }
        for (final Object entry : fluids) {
            if (!(entry instanceof IAEFluidStack)) { continue; }
            final IAEFluidStack candidate = (IAEFluidStack) entry;
            if (!sameFluid(candidate, wantedFluid)) { continue; }
            return candidate.getStackSize();
        }
        return 0L;
    }

    /** 同一种流体：注册名相同且 NBT 标签相等。 */
    private static boolean sameFluid(final IAEFluidStack candidate, final FluidStack wanted) {
        final FluidStack fluid = candidate.getFluidStack();
        if (fluid == null || fluid.getFluid() != wanted.getFluid()) { return false; }
        return fluid.tag == null ? wanted.tag == null : fluid.tag.equals(wanted.tag);
    }

    /** 把流体目标写进 NBT：沿用 AE 流体堆自己的序列化，身份是注册名 + NBT。 */
    public static boolean writeTarget(final Object target, final NBTTagCompound tag) {
        if (!(target instanceof IAEFluidStack)) { return false; }
        ((IAEFluidStack) target).writeToNBT(tag);
        return true;
    }

    public static IAEFluidStack readTarget(final NBTTagCompound tag) {
        if (tag == null || tag.isEmpty()) { return null; }
        final IAEFluidStack fluid = AEFluidStack.fromNBT(tag);
        return fluid == null || fluid.getFluid() == null ? null : fluid;
    }

    /**
     * HEI 拖入或容器读到的流体转成 AE 流体堆。Forge 的 FluidStack 与 AE 的流体堆都是
     * AE2UEL 类型，不需要 AE2FC；AE2FC 的虚拟包才转给兼容层解码。
     */
    public static IAEStack<?> fluidOf(final Object raw) {
        if (raw instanceof FluidStack) {
            final FluidStack stack = (FluidStack) raw;
            if (stack.getFluid() == null) { return null; }
            final IAEFluidStack fluid = AEFluidStack.fromFluidStack(stack);
            return fluid == null ? null : fluid.setStackSize(1);
        }
        if (raw instanceof IAEFluidStack) {
            final IAEFluidStack fluid = (IAEFluidStack) raw;
            return fluid.getFluid() == null ? null : fluid.copy().setStackSize(1);
        }
        return ae2fcPresent() ? Ae2fcFluidAdapter.fluidOfFake(raw) : null;
    }

    /** 界面图标：AE2 自带的流体占位物品，会渲染成对应流体。 */
    public static ItemStack displayStack(final Object target) {
        if (!(target instanceof IAEFluidStack)) { return ItemStack.EMPTY; }
        final ItemStack stack = ((IAEFluidStack) target).asItemStackRepresentation();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    public static String displayName(final Object target) {
        if (!(target instanceof IAEFluidStack)) { return ""; }
        final FluidStack fluid = ((IAEFluidStack) target).getFluidStack();
        return fluid == null ? "" : fluid.getLocalizedName();
    }

    /** 合成请求：需要 AE2FC 的虚拟 drop；未安装时返回 null。 */
    public static appeng.api.storage.data.IAEItemStack request(final Object target, final long batch) {
        return ae2fcPresent() ? Ae2fcFluidAdapter.request(target, batch) : null;
    }

    /**
     * 网络里是否存在可用的流体离散器。这个版本的流体自动合成靠它把流体交付回网；
     * 同网两台及以上时 AE2FC 自己不再提供桥接库存，这里按同样口径判定。
     */
    public static boolean hasDiscretizer(final appeng.api.networking.IGridHost host) {
        return ae2fcPresent() && Ae2fcFluidAdapter.hasDiscretizer(host);
    }
}
