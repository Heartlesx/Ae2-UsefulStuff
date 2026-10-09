package com.extrarawstyle.ae2us.craftpin;

import com.extrarawstyle.ae2us.Ae2us;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：当前该置顶的物品定义。
 * <p>
 * 只发物品定义（不下发数量/库存），客户端拿它去和终端列表里的条目比"是不是同一个物品"。
 * 带上容器 windowId，客户端会核对当前打开的容器，避免切终端时的过期数据被用上。
 */
public final class CraftedItemPinNetwork {

    private static final int MAX_ITEMS = 64;

    private static SimpleNetworkWrapper channel;

    private CraftedItemPinNetwork() {
    }

    public static void register(final SimpleNetworkWrapper wrapper) {
        channel = wrapper;
        channel.registerMessage(PinnedHandler.class, Pinned.class, 19, Side.CLIENT);
        channel.registerMessage(ClearHandler.class, Clear.class, 20, Side.SERVER);
    }

    public static void sendTo(final EntityPlayerMP player, final int window, final List<ItemStack> pinned) {
        if (channel != null) {
            channel.sendTo(new Pinned(window, pinned), player);
        }
    }

    /** 客户端点"清除合成置顶"按钮时发的空包。 */
    public static void sendClear() {
        if (channel != null) {
            channel.sendToServer(new Clear());
        }
    }

    public static class Clear implements IMessage {

        @Override
        public void fromBytes(final ByteBuf buffer) {
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
        }
    }

    public static class ClearHandler implements IMessageHandler<Clear, IMessage> {

        @Override
        public IMessage onMessage(final Clear message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            if (player != null) {
                player.server.addScheduledTask(() -> CraftedItemPinTracker.requestClear(player.getUniqueID()));
            }
            return null;
        }
    }

    public static class Pinned implements IMessage {

        private int window;
        private List<ItemStack> items = new ArrayList<>();

        public Pinned() {
        }

        Pinned(final int window, final List<ItemStack> items) {
            this.window = window;
            this.items = items;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            this.window = buffer.readInt();
            // 先转成无符号再夹取：坏包/超长包不能让 readByte 的负值变成非法容量。
            final int size = Math.min(buffer.readByte() & 0xFF, MAX_ITEMS);
            this.items = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                this.items.add(ByteBufUtils.readItemStack(buffer));
            }
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            buffer.writeInt(this.window);
            final int size = Math.min(this.items.size(), MAX_ITEMS);
            buffer.writeByte(size);
            for (int i = 0; i < size; i++) {
                ByteBufUtils.writeItemStack(buffer, this.items.get(i));
            }
        }
    }

    public static class PinnedHandler implements IMessageHandler<Pinned, IMessage> {

        @Override
        public IMessage onMessage(final Pinned message, final MessageContext context) {
            Ae2us.PROXY.pinnedItems(message.window, message.items);
            return null;
        }
    }
}
