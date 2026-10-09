package com.extrarawstyle.ae2us.alarm;

import com.extrarawstyle.ae2us.Ae2us;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.io.IOException;

/** 库存报警器 GUI 同步：ID 16 客户端动作（服务端），ID 17 配置状态（客户端）。 */
public final class AlarmNetwork {
    public static final int SET_ITEM = 0, CLEAR = 1, SET_THRESHOLD = 2, PAGE = 3;
    private static SimpleNetworkWrapper channel;

    private AlarmNetwork() { }

    public static void register(final SimpleNetworkWrapper wrapper) {
        channel = wrapper;
        channel.registerMessage(ActionHandler.class, Action.class, 16, Side.SERVER);
        channel.registerMessage(StateHandler.class, State.class, 17, Side.CLIENT);
    }

    public static void action(final int window, final int operation, final int index, final String text, final ItemStack stack) {
        channel.sendToServer(new Action(window, operation, index, text, stack));
    }

    public static void sendState(final EntityPlayerMP player, final ContainerStorageAlarm container) {
        channel.sendTo(new State(container), player);
    }

    public static final class Action implements IMessage {
        private int window, operation, index;
        private String text = "";
        private ItemStack stack = ItemStack.EMPTY;

        public Action() { }

        private Action(final int window, final int operation, final int index, final String text, final ItemStack stack) {
            this.window = window; this.operation = operation; this.index = index; this.text = text; this.stack = stack;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            this.window = packet.readInt(); this.operation = packet.readUnsignedByte(); this.index = packet.readUnsignedByte();
            this.text = packet.readString(32);
            try { this.stack = packet.readItemStack(); } catch (final IOException ignored) { this.stack = ItemStack.EMPTY; }
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeInt(this.window); packet.writeByte(this.operation); packet.writeByte(this.index);
            packet.writeString(this.text); packet.writeItemStack(this.stack);
        }
    }

    public static final class ActionHandler implements IMessageHandler<Action, IMessage> {
        @Override
        public IMessage onMessage(final Action message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> {
                if (!(player.openContainer instanceof ContainerStorageAlarm)
                        || player.openContainer.windowId != message.window) { return; }
                final ContainerStorageAlarm container = (ContainerStorageAlarm) player.openContainer;
                if (!container.mayEdit(player)) { return; }
                final TileStorageAlarm tile = container.tile;
                switch (message.operation) {
                    case SET_ITEM:
                        if (message.index >= 0 && message.index < TileStorageAlarm.CAPACITY && !message.stack.isEmpty()) {
                            tile.setItem(message.index, message.stack);
                        }
                        break;
                    case CLEAR:
                        if (message.index >= 0 && message.index < TileStorageAlarm.CAPACITY) {
                            tile.setItem(message.index, ItemStack.EMPTY);
                        }
                        break;
                    case SET_THRESHOLD: {
                        if (message.index < 0 || message.index >= TileStorageAlarm.CAPACITY) { return; }
                        final Long value = ThresholdText.parse(message.text);
                        if (value == null) { return; }
                        tile.setThreshold(message.index, value);
                        break;
                    }
                    case PAGE:
                        container.page = Math.max(0, Math.min(message.index, TileStorageAlarm.CAPACITY / 8 - 1));
                        container.refresh();
                        return;
                    default:
                        return;
                }
                container.refresh();
                container.detectAndSendChanges();
            });
            return null;
        }
    }

    public static final class State implements IMessage {
        public int window, page, status;
        public final boolean[] below = new boolean[8];
        public final long[] thresholds = new long[8];
        public final long[] counts = new long[8];
        public final ItemStack[] items = new ItemStack[8];

        public State() { }

        private State(final ContainerStorageAlarm container) {
            this.window = container.windowId;
            this.page = container.page;
            this.status = container.tile.statusOrdinal();
            final int base = this.page * 8;
            for (int i = 0; i < 8; i++) {
                final TileStorageAlarm.Entry entry = container.tile.entry(base + i);
                this.items[i] = entry.item.isEmpty() ? ItemStack.EMPTY : entry.item;
                this.thresholds[i] = entry.threshold;
                this.counts[i] = entry.count;
                this.below[i] = entry.below;
            }
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            this.window = packet.readInt(); this.page = packet.readUnsignedByte(); this.status = packet.readUnsignedByte();
            for (int i = 0; i < 8; i++) {
                this.below[i] = packet.readBoolean(); this.thresholds[i] = packet.readLong(); this.counts[i] = packet.readLong();
                try { this.items[i] = packet.readItemStack(); } catch (final IOException ignored) { this.items[i] = ItemStack.EMPTY; }
            }
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeInt(this.window); packet.writeByte(this.page); packet.writeByte(this.status);
            for (int i = 0; i < 8; i++) {
                packet.writeBoolean(this.below[i]); packet.writeLong(this.thresholds[i]); packet.writeLong(this.counts[i]);
                packet.writeItemStack(this.items[i]);
            }
        }
    }

    public static final class StateHandler implements IMessageHandler<State, IMessage> {
        @Override
        public IMessage onMessage(final State message, final MessageContext context) {
            Ae2us.PROXY.alarmState(message);
            return null;
        }
    }
}
