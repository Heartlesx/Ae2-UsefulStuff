package com.extrarawstyle.ae2us;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

public final class LocateCommand extends CommandBase {

    @Override
    public String getName() {
        return "ae2us_locate";
    }

    @Override
    public String getUsage(final ICommandSender sender) {
        return "/ae2us_locate <link>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(final MinecraftServer server, final ICommandSender sender, final String[] args)
            throws CommandException {
        if (args.length != 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        if (sender instanceof EntityPlayerMP) {
            PatternUploadService.teleport((EntityPlayerMP) sender, args[0]);
        }
    }
}
