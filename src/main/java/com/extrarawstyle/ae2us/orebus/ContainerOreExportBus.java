package com.extrarawstyle.ae2us.orebus;

import appeng.api.config.SecurityPermissions;
import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotRestrictedInput;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.items.IItemHandler;

public final class ContainerOreExportBus extends AEBaseContainer {
    public final PartOreExportBus bus;
    public final PartOreStorageBus storage;
    public final appeng.parts.automation.PartUpgradeable part;
    public final java.util.List<String> partitionNames = new java.util.ArrayList<>();
    public boolean partitionReady;
    public OreBusNetwork.State state;
    boolean black;
    int offset;
    private int lastRevision = -1;
    private int lastSpeed = -1;
    private int lastRedstone = -1;
    private int lastRedstoneMode = -1;
    private boolean dirty = true;
    private String lastStorageSettings = "";

    public ContainerOreExportBus(final InventoryPlayer inventory, final PartOreExportBus bus) {
        this(inventory, (appeng.parts.automation.PartUpgradeable) bus);
    }

    public ContainerOreExportBus(final InventoryPlayer inventory, final PartOreStorageBus bus) {
        this(inventory, (appeng.parts.automation.PartUpgradeable) bus);
    }

    private ContainerOreExportBus(final InventoryPlayer inventory, final appeng.parts.automation.PartUpgradeable part) {
        super(inventory, part);
        this.part = part;
        this.bus = part instanceof PartOreExportBus ? (PartOreExportBus) part : null;
        this.storage = part instanceof PartOreStorageBus ? (PartOreStorageBus) part : null;
        final IItemHandler upgrades = part.getInventoryByName("upgrades");
        for (int i = 0; i < upgrades.getSlots(); i++) {
            this.addSlotToContainer(new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES,
                    upgrades, i, 205 + i * 18, 154, inventory));
        }
        this.bindPlayerInventory(inventory, 0, 154);
    }

    public OreExportRules rules() { return this.storage == null ? this.bus.rules() : this.storage.rules(); }

    public void changed() {
        if (this.storage == null) { this.bus.changed(); } else { this.storage.changed(); }
    }

    @Override
    public boolean canInteractWith(final EntityPlayer player) {
        return super.canInteractWith(player) && player.getDistanceSq(this.part.getTile().getPos()) <= 64
                && OreBusRegistration.findPart(player.world, this.part.getSide().ordinal(), this.part.getTile().getPos()) == this.part;
    }

    public boolean mayEdit(final EntityPlayer player) {
        return this.canInteractWith(player) && this.hasAccess(SecurityPermissions.BUILD, false);
    }

    public void refresh() { this.dirty = true; }

    @Override
    public void detectAndSendChanges() {
        if (!this.getPlayerInv().player.world.isRemote) {
            this.verifyPermissions(SecurityPermissions.BUILD, false);
            final int speed = this.part.getInstalledUpgrades(appeng.api.config.Upgrades.SPEED);
            final int redstone = this.part.getInstalledUpgrades(appeng.api.config.Upgrades.REDSTONE);
            final int redstoneMode = this.bus == null ? 0 : this.bus.getRSMode().ordinal();
            final String settings = this.storage == null ? "" : this.storage.getPriority() + ":"
                    + this.storage.getConfigManager().getSetting(appeng.api.config.Settings.ACCESS) + ":"
                    + this.storage.getConfigManager().getSetting(appeng.api.config.Settings.STORAGE_FILTER) + ":"
                    + this.storage.getInstalledUpgrades(appeng.api.config.Upgrades.STICKY);
            if (this.dirty || this.lastRevision != this.rules().revision() || this.lastSpeed != speed
                    || this.lastRedstone != redstone || this.lastRedstoneMode != redstoneMode
                    || !this.lastStorageSettings.equals(settings)) {
                this.lastStorageSettings = settings;
                this.lastRevision = this.rules().revision();
                this.lastSpeed = speed;
                this.lastRedstone = redstone;
                this.lastRedstoneMode = redstoneMode;
                this.offset = Math.max(0, Math.min(this.offset, Math.max(0, this.rules().list(this.black).size() - 8)));
                OreBusNetwork.sendState((EntityPlayerMP) this.getPlayerInv().player, this);
                this.dirty = false;
            }
        }
        super.detectAndSendChanges();
    }
}
