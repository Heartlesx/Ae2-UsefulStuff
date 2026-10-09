package com.extrarawstyle.ae2us.orebus;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public final class OreExportTransfer {
    private ItemStack pending = ItemStack.EMPTY;

    public ItemStack pending() { return this.pending; }

    public boolean recover(final IMEInventory<IAEItemStack> inventory, final IActionSource source) {
        if (!this.pending.isEmpty()) {
            final IAEItemStack remaining = inventory.injectItems(AEItemStack.fromItemStack(this.pending), Actionable.MODULATE, source);
            this.pending = remaining == null ? ItemStack.EMPTY : remaining.createItemStack();
        }
        return this.pending.isEmpty();
    }

    public int send(final InventoryAdaptor destination, final IMEInventory<IAEItemStack> inventory,
                    final IEnergySource energy, final IActionSource source, final IAEItemStack item, final int limit) {
        int sent = 0;
        // Escrow holds extracted items until insertion or rollback finishes, including across saves.
        while (sent < limit && this.pending.isEmpty()) {
            final ItemStack sample = item.createItemStack();
            sample.setCount(Math.min(limit - sent, sample.getMaxStackSize()));
            final ItemStack simulated = destination.simulateAdd(sample.copy());
            final int fits = sample.getCount() - (simulated.isEmpty() ? 0 : simulated.getCount());
            if (fits <= 0) { break; }
            final IAEItemStack request = item.copy();
            request.setStackSize(fits);
            final IAEItemStack extracted = Platform.poweredExtraction(energy, inventory, request, source);
            if (extracted == null || extracted.getStackSize() <= 0) { break; }
            this.pending = extracted.createItemStack();
            final ItemStack rejected = destination.addItems(this.pending.copy());
            sent += this.pending.getCount() - (rejected.isEmpty() ? 0 : rejected.getCount());
            this.pending = rejected;
            if (!rejected.isEmpty()) {
                this.recover(inventory, source);
                break;
            }
        }
        return sent;
    }

    public void read(final NBTTagCompound tag) {
        this.pending = tag.hasKey("oreExportPending", 10) ? new ItemStack(tag.getCompoundTag("oreExportPending")) : ItemStack.EMPTY;
    }

    public void write(final NBTTagCompound tag) {
        if (!this.pending.isEmpty()) { tag.setTag("oreExportPending", this.pending.writeToNBT(new NBTTagCompound())); }
        else { tag.removeTag("oreExportPending"); }
    }
}
