package com.extrarawstyle.ae2us;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.List;

public final class PatternUploadNetwork {

    private static SimpleNetworkWrapper channel;

    private PatternUploadNetwork() {
    }

    static void register(final SimpleNetworkWrapper wrapper) {
        channel = wrapper;
        channel.registerMessage(ListRequestHandler.class, ListRequest.class, 1, Side.SERVER);
        channel.registerMessage(TargetsHandler.class, TargetsMessage.class, 2, Side.CLIENT);
        channel.registerMessage(UploadHandler.class, UploadRequest.class, 3, Side.SERVER);
        channel.registerMessage(UndoHandler.class, UndoRequest.class, 4, Side.SERVER);
        channel.registerMessage(UndoStateHandler.class, UndoStateMessage.class, 5, Side.CLIENT);
        channel.registerMessage(UndoStateRequestHandler.class, UndoStateRequest.class, 6, Side.SERVER);
        channel.registerMessage(LocateHandler.class, LocateRequest.class, 7, Side.SERVER);
        channel.registerMessage(CategoryHandler.class, CategoryMessage.class, 8, Side.SERVER);
        channel.registerMessage(AutoUploadHandler.class, AutoUploadRequest.class, 9, Side.SERVER);
        channel.registerMessage(SettingsChangeHandler.class, SettingsChange.class, 10, Side.SERVER);
        channel.registerMessage(SettingsHandler.class, SettingsMessage.class, 11, Side.CLIENT);
        channel.registerMessage(AutoDoneHandler.class, AutoDone.class, 12, Side.CLIENT);
        channel.registerMessage(RenameHandler.class, RenameRequest.class, 21, Side.SERVER);
    }

    public static void requestTargets() {
        channel.sendToServer(new ListRequest());
    }

    public static void upload(final String key) {
        channel.sendToServer(new UploadRequest(key));
    }

    public static void autoUpload() {
        channel.sendToServer(new AutoUploadRequest());
    }

    public static void captureCategory(final String uid, final String title, final String mold, final String moldTitle) {
        if (uid != null && !uid.isEmpty() && uid.length() <= 128 && title != null && title.length() <= 128) {
            channel.sendToServer(new CategoryMessage(uid, title, mold, moldTitle));
        }
    }

    public static void saveMapping(final String previousUid, final String uid, final String title, final String name) {
        channel.sendToServer(new SettingsChange((byte) 1, true, previousUid, uid, title, name));
    }

    public static void removeMapping(final String uid) {
        channel.sendToServer(new SettingsChange((byte) 2, true, "", uid, "", ""));
    }

    public static void setAutoEnabled(final boolean enabled) {
        channel.sendToServer(new SettingsChange((byte) 0, enabled, "", "", "", ""));
    }

    public static void undo() {
        channel.sendToServer(new UndoRequest());
    }

    public static void requestUndoState() {
        channel.sendToServer(new UndoStateRequest());
    }

    public static void locate(final String key, final PatternUploadService.TargetInfo.MemberInfo member) {
        channel.sendToServer(new LocateRequest(key, member.dimension, member.pos, member.side));
    }

    public static void renameGroup(final String key, final String name) {
        channel.sendToServer(new RenameRequest(key, 0, BlockPos.ORIGIN, 0, name));
    }

    public static void renameMember(final int dimension, final BlockPos pos, final int side, final String name) {
        channel.sendToServer(new RenameRequest("", dimension, pos, side, name));
    }

    private static void sendUndoState(final EntityPlayerMP player) {
        channel.sendTo(new UndoStateMessage(PatternUploadService.hasUndo(player)), player);
    }

    private static void sendSettings(final EntityPlayerMP player) {
        final PatternUploadData data = PatternUploadData.get(player);
        channel.sendTo(new SettingsMessage(data.enabled(player.getUniqueID()),
                data.mappings(player.getUniqueID())), player);
    }

    private static void sendTargets(final EntityPlayerMP player) {
        final List<PatternUploadService.TargetInfo> targets = PatternUploadService.list(player);
        if (targets != null) {
            sendSettings(player);
            channel.sendTo(new TargetsMessage(targets), player);
        } else {
            channel.sendTo(new AutoDone(), player);
        }
    }

    public static final class ListRequest implements IMessage {
        @Override
        public void fromBytes(final ByteBuf buffer) {
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
        }
    }

    public static final class ListRequestHandler implements IMessageHandler<ListRequest, IMessage> {
        @Override
        public IMessage onMessage(final ListRequest message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> sendTargets(player));
            return null;
        }
    }

    public static final class CategoryMessage implements IMessage {
        private String uid;
        private String title;
        private String mold;
        private String moldTitle;

        public CategoryMessage() {
        }

        private CategoryMessage(final String uid, final String title, final String mold, final String moldTitle) {
            this.uid = uid;
            this.title = title;
            this.mold = mold;
            this.moldTitle = moldTitle;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            uid = packet.readString(128);
            title = packet.readString(128);
            mold = packet.readString(128);
            moldTitle = packet.readString(128);
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeString(uid);
            packet.writeString(title);
            packet.writeString(mold);
            packet.writeString(moldTitle);
        }
    }

    public static final class CategoryHandler implements IMessageHandler<CategoryMessage, IMessage> {
        @Override
        public IMessage onMessage(final CategoryMessage message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> {
                if (player.openContainer instanceof appeng.container.implementations.ContainerPatternEncoder
                        && player.openContainer.canInteractWith(player) && !message.uid.trim().isEmpty()) {
                    final PatternCategoryCapture capture = (PatternCategoryCapture) player.openContainer;
                    capture.ae2us$pendingCategory(message.uid, message.title.isEmpty() ? message.uid : message.title,
                            message.mold, message.moldTitle);
                    capture.ae2us$captureTransferredRecipe();
                }
            });
            return null;
        }
    }

    public static final class AutoUploadRequest implements IMessage {
        @Override
        public void fromBytes(final ByteBuf buffer) {
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
        }
    }

    public static final class AutoUploadHandler implements IMessageHandler<AutoUploadRequest, IMessage> {
        @Override
        public IMessage onMessage(final AutoUploadRequest message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> {
                if (PatternUploadService.autoUpload(player)) {
                    sendTargets(player);
                } else {
                    channel.sendTo(new AutoDone(), player);
                }
                sendUndoState(player);
            });
            return null;
        }
    }

    public static final class SettingsChange implements IMessage {
        private byte action;
        private boolean enabled;
        private String previousUid;
        private String uid;
        private String title;
        private String name;

        public SettingsChange() {
        }

        private SettingsChange(final byte action, final boolean enabled, final String previousUid,
                final String uid, final String title, final String name) {
            this.action = action;
            this.enabled = enabled;
            this.previousUid = previousUid;
            this.uid = uid;
            this.title = title;
            this.name = name;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            action = packet.readByte();
            enabled = packet.readBoolean();
            previousUid = packet.readString(128);
            uid = packet.readString(128);
            title = packet.readString(128);
            name = packet.readString(256);
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeByte(action);
            packet.writeBoolean(enabled);
            packet.writeString(previousUid);
            packet.writeString(uid);
            packet.writeString(title);
            packet.writeString(name);
        }
    }

    public static final class SettingsChangeHandler implements IMessageHandler<SettingsChange, IMessage> {
        @Override
        public IMessage onMessage(final SettingsChange message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> {
                if (!(player.openContainer instanceof appeng.container.implementations.ContainerPatternEncoder)
                        || !player.openContainer.canInteractWith(player)) {
                    return;
                }
                final PatternUploadData data = PatternUploadData.get(player);
                if (message.action == 0) {
                    data.setEnabled(player.getUniqueID(), message.enabled);
                } else if (message.action == 1) {
                    if (!data.save(player.getUniqueID(), message.previousUid, message.uid,
                            message.title, message.name)) {
                        player.sendStatusMessage(new TextComponentTranslation("ae2us.mapping.invalid"), true);
                    } else {
                        player.sendStatusMessage(new TextComponentTranslation("ae2us.mapping.saved"), true);
                    }
                } else if (message.action == 2) {
                    data.remove(player.getUniqueID(), message.uid);
                    player.sendStatusMessage(new TextComponentTranslation("ae2us.mapping.removed"), true);
                }
                sendSettings(player);
            });
            return null;
        }
    }

    public static final class SettingsMessage implements IMessage {
        private boolean enabled;
        private List<PatternUploadData.Mapping> mappings = new ArrayList<>();

        public SettingsMessage() {
        }

        private SettingsMessage(final boolean enabled, final List<PatternUploadData.Mapping> mappings) {
            this.enabled = enabled;
            this.mappings = mappings;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            enabled = packet.readBoolean();
            final int count = packet.readVarInt();
            if (count < 0 || count > 256) {
                throw new IllegalArgumentException("Invalid upload mapping count: " + count);
            }
            mappings = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                mappings.add(new PatternUploadData.Mapping(packet.readString(128), packet.readString(128),
                        packet.readString(256)));
            }
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeBoolean(enabled);
            packet.writeVarInt(mappings.size());
            for (final PatternUploadData.Mapping mapping : mappings) {
                packet.writeString(mapping.uid);
                packet.writeString(mapping.title);
                packet.writeString(mapping.name);
            }
        }
    }

    public static final class SettingsHandler implements IMessageHandler<SettingsMessage, IMessage> {
        @Override
        public IMessage onMessage(final SettingsMessage message, final MessageContext context) {
            Ae2us.PROXY.updateUploadSettings(message.enabled, message.mappings);
            return null;
        }
    }

    public static final class AutoDone implements IMessage {
        @Override
        public void fromBytes(final ByteBuf buffer) {
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
        }
    }

    public static final class AutoDoneHandler implements IMessageHandler<AutoDone, IMessage> {
        @Override
        public IMessage onMessage(final AutoDone message, final MessageContext context) {
            Ae2us.PROXY.finishAutoUpload();
            return null;
        }
    }

    public static final class UploadRequest implements IMessage {
        private String key;

        public UploadRequest() {
        }

        private UploadRequest(final String key) {
            this.key = key;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            key = new PacketBuffer(buffer).readString(256);
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            new PacketBuffer(buffer).writeString(key);
        }
    }

    public static final class UploadHandler implements IMessageHandler<UploadRequest, IMessage> {
        @Override
        public IMessage onMessage(final UploadRequest message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> {
                PatternUploadService.upload(player, message.key);
                sendUndoState(player);
            });
            return null;
        }
    }

    public static final class UndoRequest implements IMessage {
        @Override
        public void fromBytes(final ByteBuf buffer) {
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
        }
    }

    public static final class UndoHandler implements IMessageHandler<UndoRequest, IMessage> {
        @Override
        public IMessage onMessage(final UndoRequest message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> {
                PatternUploadService.undo(player);
                sendUndoState(player);
            });
            return null;
        }
    }

    public static final class UndoStateRequest implements IMessage {
        @Override
        public void fromBytes(final ByteBuf buffer) {
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
        }
    }

    public static final class UndoStateRequestHandler implements IMessageHandler<UndoStateRequest, IMessage> {
        @Override
        public IMessage onMessage(final UndoStateRequest message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> sendUndoState(player));
            return null;
        }
    }

    public static final class UndoStateMessage implements IMessage {
        private boolean available;

        public UndoStateMessage() {
        }

        private UndoStateMessage(final boolean available) {
            this.available = available;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            available = buffer.readBoolean();
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            buffer.writeBoolean(available);
        }
    }

    public static final class UndoStateHandler implements IMessageHandler<UndoStateMessage, IMessage> {
        @Override
        public IMessage onMessage(final UndoStateMessage message, final MessageContext context) {
            Ae2us.PROXY.setUndoAvailable(message.available);
            return null;
        }
    }

    public static final class TargetsMessage implements IMessage {
        private List<PatternUploadService.TargetInfo> targets = new ArrayList<>();

        public TargetsMessage() {
        }

        private TargetsMessage(final List<PatternUploadService.TargetInfo> targets) {
            this.targets = targets;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            final int count = packet.readVarInt();
            if (count < 0 || count > 4096) {
                throw new IllegalArgumentException("Invalid upload target count: " + count);
            }
            targets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                final String key = packet.readString(256);
                final String name = packet.readString(256);
                final String location = packet.readString(128);
                final int memberCount = packet.readVarInt();
                final int freeSlots = packet.readVarInt();
                final int size = packet.readVarInt();
                if (size < 0 || size > 4096) {
                    throw new IllegalArgumentException("Invalid interface member count: " + size);
                }
                final List<PatternUploadService.TargetInfo.MemberInfo> members = new ArrayList<>(size);
                for (int j = 0; j < size; j++) {
                    members.add(new PatternUploadService.TargetInfo.MemberInfo(packet.readInt(),
                            BlockPos.fromLong(packet.readLong()), packet.readUnsignedByte(),
                            packet.readString(128), packet.readVarInt(), packet.readBoolean()));
                }
                targets.add(new PatternUploadService.TargetInfo(key, name, location, memberCount, freeSlots, members));
            }
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeVarInt(targets.size());
            for (final PatternUploadService.TargetInfo target : targets) {
                packet.writeString(target.key);
                packet.writeString(target.name);
                packet.writeString(target.location);
                packet.writeVarInt(target.count);
                packet.writeVarInt(target.freeSlots);
                packet.writeVarInt(target.members.size());
                for (final PatternUploadService.TargetInfo.MemberInfo member : target.members) {
                    packet.writeInt(member.dimension);
                    packet.writeLong(member.pos.toLong());
                    packet.writeByte(member.side);
                    packet.writeString(member.location);
                    packet.writeVarInt(member.freeSlots);
                    packet.writeBoolean(member.custom);
                }
            }
        }
    }

    public static final class LocateRequest implements IMessage {
        private String key;
        private int dimension;
        private BlockPos pos;
        private int side;

        public LocateRequest() {
        }

        private LocateRequest(final String key, final int dimension, final BlockPos pos, final int side) {
            this.key = key;
            this.dimension = dimension;
            this.pos = pos;
            this.side = side;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            key = packet.readString(256);
            dimension = packet.readInt();
            pos = BlockPos.fromLong(packet.readLong());
            side = packet.readUnsignedByte();
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeString(key);
            packet.writeInt(dimension);
            packet.writeLong(pos.toLong());
            packet.writeByte(side);
        }
    }

    public static final class LocateHandler implements IMessageHandler<LocateRequest, IMessage> {
        @Override
        public IMessage onMessage(final LocateRequest message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> PatternUploadService.locate(player,
                    message.key, message.dimension, message.pos, message.side));
            return null;
        }
    }

    public static final class RenameRequest implements IMessage {
        private String key;
        private int dimension;
        private BlockPos pos;
        private int side;
        private String name;

        public RenameRequest() {
        }

        private RenameRequest(final String key, final int dimension, final BlockPos pos, final int side,
                final String name) {
            this.key = key;
            this.dimension = dimension;
            this.pos = pos;
            this.side = side;
            this.name = name;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            key = packet.readString(256);
            dimension = packet.readInt();
            pos = BlockPos.fromLong(packet.readLong());
            side = packet.readUnsignedByte();
            name = packet.readString(256);
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeString(key);
            packet.writeInt(dimension);
            packet.writeLong(pos.toLong());
            packet.writeByte(side);
            packet.writeString(name);
        }
    }

    public static final class RenameHandler implements IMessageHandler<RenameRequest, IMessage> {
        @Override
        public IMessage onMessage(final RenameRequest message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> {
                if (message.key.isEmpty()) {
                    PatternUploadService.renameMember(player, message.dimension, message.pos, message.side,
                            message.name);
                } else {
                    PatternUploadService.rename(player, message.key, message.name);
                }
                sendTargets(player);
            });
            return null;
        }
    }

    public static final class TargetsHandler implements IMessageHandler<TargetsMessage, IMessage> {
        @Override
        public IMessage onMessage(final TargetsMessage message, final MessageContext context) {
            Ae2us.PROXY.showUploadTargets(message.targets);
            return null;
        }
    }
}
