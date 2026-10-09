package com.extrarawstyle.ae2us.alarm;

import appeng.api.config.SecurityPermissions;
import appeng.container.AEBaseContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

/** 报警器配置容器：仅玩家背包真实槽位，条目与阈值走 AlarmNetwork 自定义同步。 */
public final class ContainerStorageAlarm extends AEBaseContainer {
    public final TileStorageAlarm tile;
    public AlarmNetwork.State state;
    public int page;
    private long lastRevision = -1;
    private boolean dirty = true;

    public ContainerStorageAlarm(final InventoryPlayer inventory, final TileStorageAlarm tile) {
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

    public void refresh() { this.dirty = true; }

    @Override
    public void detectAndSendChanges() {
        if (!this.getPlayerInv().player.world.isRemote) {
            this.verifyPermissions(SecurityPermissions.BUILD, false);
            if (this.dirty || this.lastRevision != this.tile.revision()) {
                this.lastRevision = this.tile.revision();
                this.page = Math.max(0, Math.min(this.page, TileStorageAlarm.CAPACITY / 8 - 1));
                AlarmNetwork.sendState((EntityPlayerMP) this.getPlayerInv().player, this);
                this.dirty = false;
            }
        }
        super.detectAndSendChanges();
    }
}
