package com.extrarawstyle.ae2us.maintainer;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.extrarawstyle.ae2us.Ae2us;
import com.extrarawstyle.ae2us.maintainer.fluid.FluidMaintainerBridge;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.io.IOException;

/**
 * 维持器 GUI 同步：ID 22 客户端动作（服务端），ID 23 当前页状态（客户端）。
 * 服务端对每个动作都重新校验窗口、行号、权限与数据完整性，不信任客户端显示。
 */
public final class StockMaintainerNetwork {
    public static final int SET_TARGET_ITEM = 0, CLEAR = 1, SET_THRESHOLD = 2, SET_BATCH = 3,
            SET_ENABLED = 4, PAGE = 5, SET_TARGET_FLUID = 6, SET_SMART_PART_BATCH = 7;

    /**
     * 动作包里 {@code text} 字段的长度上限。写入与读取必须用同一个值：
     * 客户端写超长、服务端按小上限读，会让 Netty 直接抛 DecoderException 把连接踢掉。
     * 流体目标把整份 NBT 序列化成 JSON 塞在这个字段里，注册名长的流体轻松过百字符。
     */
    private static final int ACTION_TEXT_MAX = 4096;
    /** 界面行标题（目标显示名）的长度上限，同样读写一致。 */
    private static final int LABEL_MAX = 256;

    private static SimpleNetworkWrapper channel;

    private StockMaintainerNetwork() { }

    public static void register(final SimpleNetworkWrapper wrapper) {
        channel = wrapper;
        channel.registerMessage(ActionHandler.class, Action.class, 22, Side.SERVER);
        channel.registerMessage(StateHandler.class, State.class, 23, Side.CLIENT);
    }

    public static void action(final int window, final int operation, final int index, final String text,
                              final ItemStack stack) {
        // 超长文本不发出去：宁可这一次操作没生效，也不要把连接踢掉。
        // 服务端按 ACTION_TEXT_MAX 读取，写超了 Netty 会直接抛 DecoderException。
        if (text != null && text.length() > ACTION_TEXT_MAX) {
            net.minecraftforge.fml.common.FMLLog.log.warn(
                    "AE2US: 维持器动作 {} 的文本过长（{} > {}），本次操作已忽略",
                    operation, text.length(), ACTION_TEXT_MAX);
            return;
        }
        channel.sendToServer(new Action(window, operation, index, text, stack));
    }

    public static void sendState(final EntityPlayerMP player, final ContainerStockMaintainer container) {
        channel.sendTo(new State(container), player);
    }

    public static final class Action implements IMessage {
        private int window, operation, index;
        private String text = "";
        private ItemStack stack = ItemStack.EMPTY;

        public Action() { }

        private Action(final int window, final int operation, final int index, final String text, final ItemStack stack) {
            this.window = window;
            this.operation = operation;
            this.index = index;
            this.text = text;
            this.stack = stack;
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            this.window = packet.readInt();
            this.operation = packet.readUnsignedByte();
            this.index = packet.readUnsignedByte();
            this.text = packet.readString(ACTION_TEXT_MAX);
            try {
                this.stack = packet.readItemStack();
            } catch (final IOException truncated) {
                this.stack = ItemStack.EMPTY;
            }
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeInt(this.window);
            packet.writeByte(this.operation);
            packet.writeByte(this.index);
            packet.writeString(this.text == null ? "" : this.text);
            packet.writeItemStack(this.stack);
        }
    }

    public static final class ActionHandler implements IMessageHandler<Action, IMessage> {
        @Override
        public IMessage onMessage(final Action message, final MessageContext context) {
            final EntityPlayerMP player = context.getServerHandler().player;
            player.server.addScheduledTask(() -> handle(player, message));
            return null;
        }

        private static void handle(final EntityPlayerMP player, final Action message) {
            if (!(player.openContainer instanceof ContainerStockMaintainer)) { return; }
            final ContainerStockMaintainer container = (ContainerStockMaintainer) player.openContainer;
            if (container.windowId != message.window || !container.mayEdit(player)) { return; }
            if (message.index < 0 || message.index >= MaintainerBook.CAPACITY) { return; }
            final TileStockMaintainer tile = container.tile;
            switch (message.operation) {
                case SET_TARGET_ITEM: {
                    if (message.stack.isEmpty()) { return; }
                    final IAEItemStack target = AEItemStack.fromItemStack(message.stack);
                    if (target == null || target.getDefinition().isEmpty()) { return; }
                    tile.configure(message.index, target, MaintainerRule.MODE_ITEM);
                    break;
                }
                case SET_TARGET_FLUID: {
                    final NBTTagCompound tag = decodeFluidTag(message.text);
                    final IAEFluidStack target = tag == null ? null : FluidMaintainerBridge.readTarget(tag);
                    if (target == null) { return; }
                    tile.configure(message.index, target, MaintainerRule.MODE_FLUID);
                    break;
                }
                case CLEAR:
                    tile.configure(message.index, null, MaintainerRule.MODE_ITEM);
                    break;
                case SET_THRESHOLD: {
                    final Long value = MaintainerAmount.parse(message.text);
                    if (value == null) { return; }
                    tile.configureThreshold(message.index, value);
                    break;
                }
                case SET_BATCH: {
                    final Long value = MaintainerAmount.parse(message.text);
                    if (value == null) { return; }
                    tile.configureBatch(message.index, value);
                    break;
                }
                case SET_ENABLED:
                    tile.configureEnabled(message.index, !message.text.isEmpty() && message.text.charAt(0) == '1');
                    break;
                case SET_SMART_PART_BATCH:
                    tile.configureSmartPartBatch(message.index,
                            !message.text.isEmpty() && message.text.charAt(0) == '1');
                    break;
                case PAGE:
                    container.page = Math.max(0, Math.min(message.index, MaintainerBook.PAGES - 1));
                    container.refresh();
                    return;
                default:
                    return;
            }
            container.refresh();
            container.detectAndSendChanges();
        }

        /** 流体目标用一份 NBT 文本传输：注册名 + NBT，不把数量当类型键。 */
        private static NBTTagCompound decodeFluidTag(final String text) {
            if (text.isEmpty() || text.length() > 4096) { return null; }
            try {
                return net.minecraft.nbt.JsonToNBT.getTagFromJson(text);
            } catch (final net.minecraft.nbt.NBTException malformed) {
                return null;
            }
        }
    }

    /** 当前页的 8 行数据。库存、阈值、批量、订单量都用 long。 */
    public static final class State implements IMessage {
        public int window, page, status;
        public boolean fluidReady;
        public final int[] modes = new int[MaintainerBook.ROWS_PER_PAGE];
        public final int[] statuses = new int[MaintainerBook.ROWS_PER_PAGE];
        public final long[] thresholds = new long[MaintainerBook.ROWS_PER_PAGE];
        public final long[] batches = new long[MaintainerBook.ROWS_PER_PAGE];
        public final long[] counts = new long[MaintainerBook.ROWS_PER_PAGE];
        /** 当前这单实际下了多少：减量下单时比批量小，界面直接显示它。 */
        public final long[] orderBatches = new long[MaintainerBook.ROWS_PER_PAGE];
        public final boolean[] configured = new boolean[MaintainerBook.ROWS_PER_PAGE];
        public final boolean[] countsValid = new boolean[MaintainerBook.ROWS_PER_PAGE];
        public final boolean[] enabled = new boolean[MaintainerBook.ROWS_PER_PAGE];
        /** 逐条：材料不够整批时按能做出来的量下单。 */
        public final boolean[] smartPartBatches = new boolean[MaintainerBook.ROWS_PER_PAGE];
        public final boolean[] hasOrder = new boolean[MaintainerBook.ROWS_PER_PAGE];
        public final String[] labels = new String[MaintainerBook.ROWS_PER_PAGE];
        public final ItemStack[] icons = new ItemStack[MaintainerBook.ROWS_PER_PAGE];
        public final String[] targets = new String[MaintainerBook.ROWS_PER_PAGE];

        public State() {
            for (int i = 0; i < MaintainerBook.ROWS_PER_PAGE; i++) {
                this.labels[i] = "";
                this.targets[i] = "";
                this.icons[i] = ItemStack.EMPTY;
            }
        }

        private State(final ContainerStockMaintainer container) {
            this();
            this.window = container.windowId;
            this.page = container.page;
            this.status = container.tile.statusOrdinal();
            this.fluidReady = container.tile.fluidEnvironmentReady();
            final int base = this.page * MaintainerBook.ROWS_PER_PAGE;
            for (int i = 0; i < MaintainerBook.ROWS_PER_PAGE; i++) {
                final MaintainerRule rule = container.tile.book().rule(base + i);
                if (rule == null) { continue; }
                this.configured[i] = rule.target != null;
                this.modes[i] = rule.mode;
                this.statuses[i] = rule.status;
                this.thresholds[i] = rule.threshold;
                this.batches[i] = rule.batch;
                this.counts[i] = rule.observed;
                this.countsValid[i] = rule.observedValid;
                this.enabled[i] = rule.enabled;
                this.smartPartBatches[i] = rule.smartPartBatch;
                this.hasOrder[i] = rule.craftId != null;
                this.orderBatches[i] = rule.craftId == null ? 0L : rule.orderBatch;
                if (rule.target == null) { continue; }
                if (rule.mode == MaintainerRule.MODE_FLUID) {
                    final ItemStack display = FluidMaintainerBridge.displayStack(rule.target);
                    this.icons[i] = display == null ? ItemStack.EMPTY : display;
                    this.labels[i] = FluidMaintainerBridge.displayName(rule.target);
                    final NBTTagCompound tag = new NBTTagCompound();
                    if (FluidMaintainerBridge.writeTarget(rule.target, tag)) {
                        this.targets[i] = tag.toString();
                    }
                } else {
                    final IAEItemStack item = (IAEItemStack) rule.target;
                    this.icons[i] = item.getDefinition();
                    this.labels[i] = this.icons[i].getDisplayName();
                }
            }
        }

        @Override
        public void fromBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            this.window = packet.readInt();
            this.page = packet.readUnsignedByte();
            this.status = packet.readUnsignedByte();
            this.fluidReady = packet.readBoolean();
            for (int i = 0; i < MaintainerBook.ROWS_PER_PAGE; i++) {
                this.configured[i] = packet.readBoolean();
                this.modes[i] = packet.readUnsignedByte();
                this.statuses[i] = packet.readUnsignedByte();
                this.enabled[i] = packet.readBoolean();
                this.smartPartBatches[i] = packet.readBoolean();
                this.countsValid[i] = packet.readBoolean();
                this.hasOrder[i] = packet.readBoolean();
                this.thresholds[i] = packet.readLong();
                this.batches[i] = packet.readLong();
                this.counts[i] = packet.readLong();
                this.orderBatches[i] = packet.readLong();
                this.labels[i] = packet.readString(LABEL_MAX);
                this.targets[i] = packet.readString(ACTION_TEXT_MAX);
                try {
                    this.icons[i] = packet.readItemStack();
                } catch (final IOException truncated) {
                    this.icons[i] = ItemStack.EMPTY;
                }
            }
        }

        @Override
        public void toBytes(final ByteBuf buffer) {
            final PacketBuffer packet = new PacketBuffer(buffer);
            packet.writeInt(this.window);
            packet.writeByte(this.page);
            packet.writeByte(this.status);
            packet.writeBoolean(this.fluidReady);
            for (int i = 0; i < MaintainerBook.ROWS_PER_PAGE; i++) {
                packet.writeBoolean(this.configured[i]);
                packet.writeByte(this.modes[i]);
                packet.writeByte(this.statuses[i]);
                packet.writeBoolean(this.enabled[i]);
                packet.writeBoolean(this.smartPartBatches[i]);
                packet.writeBoolean(this.countsValid[i]);
                packet.writeBoolean(this.hasOrder[i]);
                packet.writeLong(this.thresholds[i]);
                packet.writeLong(this.batches[i]);
                packet.writeLong(this.counts[i]);
                packet.writeLong(this.orderBatches[i]);
                packet.writeString(clip(this.labels[i], LABEL_MAX));
                packet.writeString(this.targets[i] == null ? "" : this.targets[i]);
                packet.writeItemStack(this.icons[i] == null ? ItemStack.EMPTY : this.icons[i]);
            }
        }

        /** 行标题只用来显示，超长就截断——读侧上限与这里必须一致。 */
        private static String clip(final String text, final int max) {
            if (text == null) { return ""; }
            return text.length() <= max ? text : text.substring(0, max);
        }
    }

    public static final class StateHandler implements IMessageHandler<State, IMessage> {
        @Override
        public IMessage onMessage(final State message, final MessageContext context) {
            Ae2us.PROXY.stockMaintainerState(message);
            return null;
        }
    }
}
