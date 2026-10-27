package com.extrarawstyle.ae2us.maintainer;

import appeng.api.config.SecurityPermissions;
import appeng.container.AEBaseContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

/** 维持器配置容器：只有玩家背包真实槽位，规则数据走 StockMaintainerNetwork 自定义同步。 */
public final class ContainerStockMaintainer extends AEBaseContainer {
    public final TileStockMaintainer tile;
    public StockMaintainerNetwork.State state;
    public int page;
    private long lastRevision = -1;
    private boolean dirty = true;

    public ContainerStockMaintainer(final InventoryPlayer inventory, final TileStockMaintainer tile) {
        super(inventory, tile, null);
        this.tile = tile;
        this.bindPlayerInventory(inventory, 51, 196);
    }

    @Override
    public boolean canInteractWith(final EntityPlayer player) {
        return super.canInteractWith(player) && player.getDistanceSq(this.tile.getPos()) <= 64
                && this.tile.getWorld().getTileEntity(this.tile.getPos()) == this.tile;
    }

    public boolean mayEdit(final EntityPlayer player) {
        return this.canInteractWith(player) && this.hasAccess(SecurityPermissions.BUILD, false);
    }

    public void refresh() {
        this.dirty = true;
    }

    @Override
    public void detectAndSendChanges() {
        if (!this.getPlayerInv().player.world.isRemote) {
            this.verifyPermissions(SecurityPermissions.BUILD, false);
            if (this.dirty || this.lastRevision != this.tile.revision()) {
                this.lastRevision = this.tile.revision();
                this.page = Math.max(0, Math.min(this.page, MaintainerBook.PAGES - 1));
                StockMaintainerNetwork.sendState((EntityPlayerMP) this.getPlayerInv().player, this);
                this.dirty = false;
            }
        }
        super.detectAndSendChanges();
    }
}
