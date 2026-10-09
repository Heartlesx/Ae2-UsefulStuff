package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.api.IPatternRefillContainer;

import appeng.container.implementations.ContainerPatternEncoder;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** 样板终端"空白样板自动补充"开关的网络处理。 */
public final class PatternRefillNetwork {

    private static SimpleNetworkWrapper channel;

    private PatternRefillNetwork() {
    }

    public static void register(final SimpleNetworkWrapper wrapper) {
        channel = wrapper;
        channel.registerMessage(ToggleHandler.class, Toggle.class, 18, Side.SERVER);
    }

    public static void sendToggle() {
        channel.sendToServer(new Toggle());
    }

    public static class Toggle implements IMessage {

        @Override
        public void fromBytes(final ByteBuf buffer) {
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
        }
    }

    public static class ToggleHandler implements IMessageHandler<Toggle, IMessage> {

        @Override
        public IMessage onMessage(final Toggle message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            if (player != null) {
                player.server.addScheduledTask(() -> {
                    if (!FeatureConfig.patternRefillEnabled()) {
                        return;
                    }
                    if (player.openContainer instanceof ContainerPatternEncoder) {
                        ((IPatternRefillContainer) player.openContainer).ae2us$togglePatternRefill();
                    }
                });
            }
            return null;
        }
    }
}
