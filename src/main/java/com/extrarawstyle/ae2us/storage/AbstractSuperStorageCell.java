package com.extrarawstyle.ae2us.storage;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellUpgrades;
import appeng.util.Platform;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * 2048M 存储元件（物品 / 流体两件共用）的实现。容量 2,147,483,647 字节（int 上限，直接重写
 * getBytes 给出——走不了 AE2 的 KB 构造器，kilobytes * 1024 会溢出）、最多 256 种类型
 * （AE2 内核的 63 上限由 {@code CellTypeLimitMixin} 放开）、每个类型只象征性占 1 字节，
 * 其余字节全留给物品本身。空闲耗电照 NAE2 口径按容量取对数，约 31。
 * <p>
 * 直接实现 {@link IStorageCell}（不继承 AE2 的 AbstractStorageCell）：那条路要传一个
 * MaterialType 当"拆解核心"，我们没打算做合成配方与拆解，也就不需要它。
 */
public abstract class AbstractSuperStorageCell<T extends IAEStack<T>> extends AEBaseItem implements IStorageCell<T> {

    /** 容量：2,147,483,647 字节（int 上限）。 */
    public static final int BYTES = 2147483647;
    /** 类型上限：256 种（AE2 内核 63 上限由 CellTypeLimitMixin 放开）。 */
    public static final int TYPES = 256;
    /** 每个类型占用的字节数：象征性 1 字节，其余全给物品。 */
    public static final int BYTES_PER_TYPE = 1;
    /** 空闲耗电：与容量匹配（2^31 的对数取整）。 */
    public static final double IDLE_DRAIN = 31.0;

    protected AbstractSuperStorageCell() {
        this.setMaxStackSize(1);
    }

    @Override
    public int getBytes(final ItemStack cellItem) {
        return BYTES;
    }

    @Override
    public int getBytesPerType(final ItemStack cellItem) {
        return BYTES_PER_TYPE;
    }

    @Override
    public int getTotalTypes(final ItemStack cellItem) {
        return TYPES;
    }

    @Override
    public double getIdleDrain() {
        return IDLE_DRAIN;
    }

    @Override
    public boolean isBlackListed(final ItemStack cellItem, final T requestedAddition) {
        return false;
    }

    @Override
    public boolean storableInStorageCell() {
        return false;
    }

    @Override
    public boolean isStorageCell(final ItemStack i) {
        return true;
    }

    @Override
    public boolean isEditable(final ItemStack is) {
        return true;
    }

    @Override
    public IItemHandler getUpgradesInventory(final ItemStack is) {
        return new CellUpgrades(is, 2);
    }

    @Override
    public FuzzyMode getFuzzyMode(final ItemStack is) {
        final String fz = Platform.openNbtData(is).getString("FuzzyMode");
        try {
            return FuzzyMode.valueOf(fz);
        } catch (final Throwable t) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public void setFuzzyMode(final ItemStack is, final FuzzyMode fzMode) {
        Platform.openNbtData(is).setString("FuzzyMode", fzMode.name());
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
            final ITooltipFlag advancedTooltips) {
        AEApi.instance().client().addCellInformation(
                AEApi.instance().registries().cell().getCellInventory(stack, null, this.getChannel()), lines);
    }
}
