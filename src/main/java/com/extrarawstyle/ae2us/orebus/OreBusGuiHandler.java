package com.extrarawstyle.ae2us.orebus;

import com.extrarawstyle.ae2us.Ae2us;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

public final class OreBusGuiHandler implements IGuiHandler {
    @Override
    public Object getServerGuiElement(final int id, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        if (id == com.extrarawstyle.ae2us.alarm.AlarmRegistration.GUI_ID) {
            final net.minecraft.tileentity.TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
            return tile instanceof com.extrarawstyle.ae2us.alarm.TileStorageAlarm
                    ? new com.extrarawstyle.ae2us.alarm.ContainerStorageAlarm(player.inventory,
                            (com.extrarawstyle.ae2us.alarm.TileStorageAlarm) tile) : null;
        }
        final appeng.parts.automation.PartUpgradeable bus = OreBusRegistration.findPart(world, id, new BlockPos(x, y, z));
        if (bus instanceof PartOreStorageBus) { return new ContainerOreExportBus(player.inventory, (PartOreStorageBus) bus); }
        return bus == null ? null : new ContainerOreExportBus(player.inventory, (PartOreExportBus) bus);
    }

    @Override
    public Object getClientGuiElement(final int id, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        if (id == com.extrarawstyle.ae2us.alarm.AlarmRegistration.GUI_ID) {
            final net.minecraft.tileentity.TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
            return tile instanceof com.extrarawstyle.ae2us.alarm.TileStorageAlarm
                    ? Ae2us.PROXY.storageAlarmGui(player.inventory, (com.extrarawstyle.ae2us.alarm.TileStorageAlarm) tile) : null;
        }
        final appeng.parts.automation.PartUpgradeable bus = OreBusRegistration.findPart(world, id, new BlockPos(x, y, z));
        return bus == null ? null : Ae2us.PROXY.oreBusGui(player.inventory, bus);
    }
}
