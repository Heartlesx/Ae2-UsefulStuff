package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.api.ISmartBlocking;
import com.extrarawstyle.ae2us.api.ISmartBlockingContainer;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.container.implementations.ContainerInterface;
import appeng.helpers.DualityInterface;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** 智能阻挡开关的网络处理；样板上传消息复用同一通道。 */
public final class NetworkHandler {

    private static SimpleNetworkWrapper channel;

    private NetworkHandler() {
    }

    static void register() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(Ae2us.MODID);
        channel.registerMessage(ToggleHandler.class, ToggleMessage.class, 0, Side.SERVER);
        PatternUploadNetwork.register(channel);
        PatternRefillNetwork.register(channel);
        com.extrarawstyle.ae2us.orebus.OreBusNetwork.register(channel);
        com.extrarawstyle.ae2us.alarm.AlarmNetwork.register(channel);
        com.extrarawstyle.ae2us.craftpin.CraftedItemPinNetwork.register(channel);
        com.extrarawstyle.ae2us.maintainer.StockMaintainerNetwork.register(channel);
    }

    public static void sendToggle() {
        channel.sendToServer(new ToggleMessage());
    }

    // 切换开关时顺手打开原版阻挡：智能阻挡本来就是原版阻挡开启后的放行规则，单开没有意义。
    private static void toggle(final EntityPlayerMP player) {
        if (!FeatureConfig.smartBlockingEnabled() || !(player.openContainer instanceof ContainerInterface)) {
            return;
        }

        final DualityInterface duality = ((ISmartBlockingContainer) player.openContainer).ae2us$getInterfaceDuality();
        final ISmartBlocking smart = (ISmartBlocking) duality;

        final boolean enabled = !smart.ae2us$isSmartBlocking();
        smart.ae2us$setSmartBlocking(enabled);
        if (enabled) {
            duality.getConfigManager().putSetting(Settings.BLOCK, YesNo.YES);
        }
        duality.saveChanges();
    }

    public static class ToggleMessage implements IMessage {

        @Override
        public void fromBytes(final ByteBuf buffer) {
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
        }
    }

    public static class ToggleHandler implements IMessageHandler<ToggleMessage, IMessage> {

        @Override
        public IMessage onMessage(final ToggleMessage message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            if (player != null) {
                player.server.addScheduledTask(() -> toggle(player));
            }
            return null;
        }
    }
}
