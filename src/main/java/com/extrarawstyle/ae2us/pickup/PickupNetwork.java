package com.extrarawstyle.ae2us.pickup;

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

/**
 * 中键拾取：客户端把"我想拿这个"的请求发给服务端，服务端自己重算一遍再执行。
 * <p>
 * 客户端发来的只是意图，不是结论——服务端不采信它给的任何判断，
 * 只把它当作"玩家对着这个物品按了中键"这一个事实。
 */
public final class PickupNetwork {

    private static SimpleNetworkWrapper channel;

    private PickupNetwork() {
    }

    public static void register(final SimpleNetworkWrapper wrapper) {
        channel = wrapper;
        channel.registerMessage(RequestHandler.class, Request.class, 24, Side.SERVER);
    }

    /** 客户端：我要这个东西。noRoom 只用来决定失败时怎么提示，服务端会自己再判一次。 */
    public static void request(final ItemStack wanted, final boolean noRoom) {
        channel.sendToServer(new Request(wanted, noRoom));
    }

    public static final class Request implements IMessage {
        private ItemStack wanted = ItemStack.EMPTY;
        private boolean noRoom;

        public Request() { }

        private Request(final ItemStack wanted, final boolean noRoom) {
            this.wanted = wanted;
            this.noRoom = noRoom;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            this.noRoom = packet.readBoolean();
            try {
                this.wanted = packet.readItemStack();
            } catch (final IOException truncated) {
                this.wanted = ItemStack.EMPTY;
            }
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeBoolean(this.noRoom);
            packet.writeItemStack(this.wanted);
        }
    }

    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {
        @Override
        public IMessage onMessage(final Request message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            if (player != null) {
                final ItemStack wanted = message.wanted;
                final boolean noRoom = message.noRoom;
                player.server.addScheduledTask(() -> PickupService.handle(player, wanted, noRoom));
            }
            return null;
        }
    }
}
