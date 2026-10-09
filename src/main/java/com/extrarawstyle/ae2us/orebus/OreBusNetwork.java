package com.extrarawstyle.ae2us.orebus;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import com.extrarawstyle.ae2us.Ae2us;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.List;

public final class OreBusNetwork {
    public static final int PAGE = 0, ADD = 1, REMOVE = 2, UP = 3, DOWN = 4, MODE = 5, REDSTONE = 6, REPLACE = 7;
    public static final int ACCESS = 8, DISPLAY = 9, PRIORITY = 10, PARTITION = 11;
    private static SimpleNetworkWrapper channel;

    private OreBusNetwork() { }

    public static void register(final SimpleNetworkWrapper wrapper) {
        channel = wrapper;
        channel.registerMessage(ActionHandler.class, Action.class, 13, Side.SERVER);
        channel.registerMessage(StateHandler.class, State.class, 14, Side.CLIENT);
        channel.registerMessage(PartitionHandler.class, Partition.class, 15, Side.CLIENT);
    }

    public static void action(final int window, final int operation, final boolean black, final int index, final String text) {
        if (operation == ADD && text.length() > 8000) {
            final StringBuilder batch = new StringBuilder();
            for (final String line : text.split("\\r?\\n")) {
                if (line.length() > 256) { continue; }
                if (batch.length() + line.length() + 1 > 8000) {
                    channel.sendToServer(new Action(window, operation, black, index, batch.toString()));
                    batch.setLength(0);
                }
                batch.append(line).append('\n');
            }
            if (batch.length() > 0) { channel.sendToServer(new Action(window, operation, black, index, batch.toString())); }
        } else {
            channel.sendToServer(new Action(window, operation, black, index, text));
        }
    }

    public static void sendState(final EntityPlayerMP player, final ContainerOreExportBus container) {
        channel.sendTo(new State(container), player);
    }

    public static final class Action implements IMessage {
        private int window, operation, index;
        private boolean black;
        private String text = "";

        public Action() { }
        private Action(final int window, final int operation, final boolean black, final int index, final String text) {
            this.window = window; this.operation = operation; this.black = black; this.index = index; this.text = text;
        }
        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            this.window = packet.readInt(); this.operation = packet.readUnsignedByte();
            this.black = packet.readBoolean(); this.index = packet.readInt(); this.text = packet.readString(8000);
        }
        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeInt(this.window); packet.writeByte(this.operation); packet.writeBoolean(this.black);
            packet.writeInt(this.index); packet.writeString(this.text);
        }
    }

    public static final class ActionHandler implements IMessageHandler<Action, IMessage> {
        @Override
        public IMessage onMessage(final Action message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> {
                if (!(player.openContainer instanceof ContainerOreExportBus) || player.openContainer.windowId != message.window) { return; }
                final ContainerOreExportBus container = (ContainerOreExportBus) player.openContainer;
                if (!container.mayEdit(player)) { return; }
                final OreExportRules rules = container.rules();
                switch (message.operation) {
                    case PAGE: container.black = message.black; container.offset = message.index; break;
                    case ADD: rules.add(message.black, message.text); break;
                    case REMOVE: rules.remove(message.black, message.index); break;
                    case UP: rules.move(message.black, message.index, -1); break;
                    case DOWN: rules.move(message.black, message.index, 1); break;
                    case REPLACE: rules.replace(message.black, message.index, message.text.trim()); break;
                    case MODE:
                        if (container.bus != null && message.index >= 0 && message.index < OreExportRules.Mode.values().length) {
                            rules.setMode(OreExportRules.Mode.values()[message.index]);
                        }
                        break;
                    case REDSTONE:
                        if (container.bus != null && container.bus.getInstalledUpgrades(Upgrades.REDSTONE) > 0
                                && message.index >= 0 && message.index < RedstoneMode.values().length) {
                            container.bus.getConfigManager().putSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.values()[message.index]);
                        }
                        break;
                    case ACCESS:
                        if (container.storage == null) { return; }
                        final appeng.api.config.AccessRestriction[] access = {
                                appeng.api.config.AccessRestriction.READ_WRITE, appeng.api.config.AccessRestriction.READ,
                                appeng.api.config.AccessRestriction.WRITE};
                        if (message.index < 0 || message.index >= access.length) { return; }
                        container.storage.getConfigManager().putSetting(Settings.ACCESS, access[message.index]);
                        break;
                    case DISPLAY:
                        if (container.storage == null || message.index < 0 || message.index > 1) { return; }
                        container.storage.getConfigManager().putSetting(Settings.STORAGE_FILTER, message.index == 0
                                ? appeng.api.config.StorageFilter.EXTRACTABLE_ONLY : appeng.api.config.StorageFilter.NONE);
                        break;
                    case PRIORITY:
                        if (container.storage == null) { return; }
                        try { container.storage.setPriority(Integer.parseInt(message.text.trim())); }
                        catch (final NumberFormatException ignored) { return; }
                        break;
                    case PARTITION:
                        if (container.storage == null) { return; }
                        final List<String> candidates = container.storage.partitionCandidates();
                        for (int start = 0; start < Math.max(1, candidates.size()); start += 24) {
                            channel.sendTo(new Partition(container.windowId, start == 0, start + 24 >= candidates.size(),
                                    candidates.subList(start, Math.min(start + 24, candidates.size()))), player);
                        }
                        return;
                    default: return;
                }
                if (message.operation != PAGE) { container.changed(); }
                container.refresh();
                container.detectAndSendChanges();
            });
            return null;
        }
    }

    public static final class State implements IMessage {
        public int window, offset, total, whiteCount, blackCount, budget;
        public boolean black, redstoneCard;
        public int mode, redstone;
        public boolean storage, sticky;
        public int access, display, priority;
        public final List<String> names = new ArrayList<>();

        public State() { }
        private State(final ContainerOreExportBus container) {
            this.window = container.windowId; this.black = container.black; this.offset = container.offset;
            final List<String> rules = container.rules().list(this.black);
            this.total = rules.size(); this.whiteCount = container.rules().list(false).size();
            this.blackCount = container.rules().list(true).size(); this.mode = container.rules().mode().ordinal();
            this.storage = container.storage != null;
            if (this.storage) {
                final Enum rw = container.storage.getConfigManager().getSetting(Settings.ACCESS);
                this.access = rw == appeng.api.config.AccessRestriction.READ ? 1 : rw == appeng.api.config.AccessRestriction.WRITE ? 2 : 0;
                this.display = container.storage.getConfigManager().getSetting(Settings.STORAGE_FILTER)
                        == appeng.api.config.StorageFilter.NONE ? 1 : 0;
                this.priority = container.storage.getPriority();
                this.sticky = container.storage.getInstalledUpgrades(Upgrades.STICKY) > 0;
            } else {
                this.redstone = container.bus.getRSMode().ordinal();
                this.redstoneCard = container.bus.getInstalledUpgrades(Upgrades.REDSTONE) > 0;
                this.budget = OreExportScheduler.budget(container.bus.getInstalledUpgrades(Upgrades.SPEED));
            }
            this.names.addAll(rules.subList(this.offset, Math.min(this.total, this.offset + 8)));
        }
        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            this.window = packet.readInt(); this.black = packet.readBoolean(); this.offset = packet.readInt();
            this.total = packet.readInt(); this.whiteCount = packet.readInt(); this.blackCount = packet.readInt();
            this.mode = packet.readUnsignedByte(); this.redstone = packet.readUnsignedByte();
            this.redstoneCard = packet.readBoolean(); this.budget = packet.readInt();
            final int count = packet.readUnsignedByte();
            if (count > 8) { throw new IllegalArgumentException("Invalid ore rule page"); }
            for (int i = 0; i < count; i++) { this.names.add(packet.readString(256)); }
            this.storage = packet.readBoolean(); this.sticky = packet.readBoolean();
            this.access = packet.readUnsignedByte(); this.display = packet.readUnsignedByte(); this.priority = packet.readInt();
        }
        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeInt(this.window); packet.writeBoolean(this.black); packet.writeInt(this.offset);
            packet.writeInt(this.total); packet.writeInt(this.whiteCount); packet.writeInt(this.blackCount);
            packet.writeByte(this.mode); packet.writeByte(this.redstone); packet.writeBoolean(this.redstoneCard);
            packet.writeInt(this.budget); packet.writeByte(this.names.size());
            for (final String name : this.names) { packet.writeString(name); }
            packet.writeBoolean(this.storage); packet.writeBoolean(this.sticky);
            packet.writeByte(this.access); packet.writeByte(this.display); packet.writeInt(this.priority);
        }
    }

    public static final class Partition implements IMessage {
        public int window;
        public boolean first, last;
        public final List<String> names = new ArrayList<>();

        public Partition() { }
        private Partition(final int window, final boolean first, final boolean last, final List<String> names) {
            this.window = window; this.first = first; this.last = last; this.names.addAll(names);
        }
        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            this.window = packet.readInt(); this.first = packet.readBoolean(); this.last = packet.readBoolean();
            final int count = packet.readUnsignedByte();
            if (count > 24) { throw new IllegalArgumentException("Invalid partition page"); }
            for (int i = 0; i < count; i++) { this.names.add(packet.readString(256)); }
        }
        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeInt(this.window); packet.writeBoolean(this.first); packet.writeBoolean(this.last);
            packet.writeByte(this.names.size());
            for (final String name : this.names) { packet.writeString(name); }
        }
    }

    public static final class PartitionHandler implements IMessageHandler<Partition, IMessage> {
        @Override
        public IMessage onMessage(final Partition message, final MessageContext context) {
            Ae2us.PROXY.oreBusPartition(message);
            return null;
        }
    }

    public static final class StateHandler implements IMessageHandler<State, IMessage> {
        @Override
        public IMessage onMessage(final State message, final MessageContext context) {
            Ae2us.PROXY.oreBusState(message);
            return null;
        }
    }
}
