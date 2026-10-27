package com.extrarawstyle.ae2us.maintainer.fluid;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import com.glodblock.github.common.item.fake.FakeFluids;
import net.minecraftforge.fluids.FluidStack;

/**
 * 真正碰到 AE2FC 类型的兼容层。只允许从 {@link FluidMaintainerBridge} 在其判定 AE2FC 在场后调到，
 * 所以未安装 AE2FC 的环境不会因为这里的类而尝试加载 AE2FC。
 *
 * 用法约定：流体目标的身份是 IAEFluidStack（注册名 + NBT），合成请求是 AE2FC 的虚拟 drop，
 * 1 drop 对应 1 mB；不经过 FluidStack.amount（int）中转大数量。
 */
public final class Ae2fcFluidAdapter {
    private Ae2fcFluidAdapter() { }

    /**
     * 合成请求：AE2FC 的虚拟 drop 数量即 mB 数。
     * packFluid2AEDrops 取 FluidStack.amount 作为 drop 数，所以这里先用不超过 int 的模板拿身份，
     * 再把 long 数量写回去，避免大批量被 int 截断。
     */
    public static IAEItemStack request(final Object target, final long batch) {
        if (!(target instanceof IAEFluidStack) || batch <= 0) { return null; }
        final FluidStack template = ((IAEFluidStack) target).getFluidStack();
        if (template == null || template.getFluid() == null) { return null; }
        template.amount = (int) Math.min(Integer.MAX_VALUE, batch);
        final IAEItemStack packed = FakeFluids.packFluid2AEDrops(template);
        if (packed == null) { return null; }
        packed.setStackSize(batch);
        packed.setCraftable(false);
        packed.setCountRequestable(0);
        return packed;
    }

    /**
     * 网络里是否有可用流体离散器。AE2FC 自己的 getCellArray 在同类机器数 >= 2 时不再提供桥接库存，
     * 所以这里用同一口径：恰好一台才算可用。
     */
    public static boolean hasDiscretizer(final appeng.api.networking.IGridHost host) {
        if (!(host instanceof appeng.me.helpers.IGridProxyable)) { return false; }
        try {
            final appeng.api.networking.IGrid grid = ((appeng.me.helpers.IGridProxyable) host).getProxy().getGrid();
            final appeng.api.networking.IMachineSet set = grid.getMachines(
                    com.glodblock.github.common.tile.TileFluidDiscretizer.class);
            return set.size() == 1;
        } catch (final appeng.me.GridAccessException offline) {
            return false;
        }
    }

    /** AE2FC 的虚拟物品（drop / packet）解码成 AE 流体堆；其它对象返回 null。 */
    public static IAEStack<?> fluidOfFake(final Object raw) {
        if (!(raw instanceof net.minecraft.item.ItemStack)) { return null; }
        final net.minecraft.item.ItemStack stack = (net.minecraft.item.ItemStack) raw;
        if (stack.isEmpty() || !com.glodblock.github.common.item.fake.FakeItemRegister.isFakeItem(stack)) {
            return null;
        }
        final Object decoded = com.glodblock.github.common.item.fake.FakeItemRegister.getStack(stack);
        if (!(decoded instanceof FluidStack)) { return null; }
        final FluidStack fluid = (FluidStack) decoded;
        if (fluid.getFluid() == null) { return null; }
        final IAEFluidStack ae = appeng.fluids.util.AEFluidStack.fromFluidStack(fluid);
        return ae == null ? null : ae.setStackSize(1);
    }
}
