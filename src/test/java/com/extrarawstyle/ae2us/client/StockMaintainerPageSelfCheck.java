package com.extrarawstyle.ae2us.client;

import com.extrarawstyle.ae2us.maintainer.ContainerStockMaintainer;
import com.extrarawstyle.ae2us.maintainer.MaintainerBook;
import com.extrarawstyle.ae2us.maintainer.MaintainerRule;
import com.extrarawstyle.ae2us.maintainer.MaintainerStatus;
import com.extrarawstyle.ae2us.maintainer.StockMaintainerNetwork;
import com.extrarawstyle.ae2us.maintainer.TileStockMaintainer;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.WorldInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 库存维持器翻页与待定模式的回归自检：驱动真实 GUI 的状态应用路径
 * （{@link GuiStockMaintainer#updateScreen()} → {@code syncFromState()} → {@code modeOf()}），
 * 而不是把数组逻辑抄一份到测试里。
 *
 * <p>实机现象：第 2 页有流体行，翻到全新的第 3 页后，同位置的空行仍显示「流体」。
 * 原因是已配置行的服务端模式被写进了 8 个待定模式槽，翻页时没有任何地方把它归零，
 * 于是旧页的模式按位置串到了新页的空行上。</p>
 *
 * <p>页面切换只认服务端确认过的 {@code state.page}：翻页按钮/滚轮/上一页/下一页最终都只是
 * 发一个请求，真正换页发生在服务端回来的状态里。所以这里按「依次喂入服务端状态」驱动，
 * 与三种输入路径共用同一个应用点。</p>
 */
public final class StockMaintainerPageSelfCheck {

    private static final int ROWS = MaintainerBook.ROWS_PER_PAGE;

    private static final Method MODE_OF;
    private static final Method SYNC_FROM_STATE;
    private static final int MODE_X;
    private static final int TOP;
    private static final int ROW_H;

    static Minecraft client;
    static EntityPlayerSP clientPlayer;

    static {
        try {
            MODE_OF = GuiStockMaintainer.class.getDeclaredMethod(
                    "modeOf", StockMaintainerNetwork.State.class, int.class);
            MODE_OF.setAccessible(true);
            SYNC_FROM_STATE = GuiStockMaintainer.class.getDeclaredMethod("syncFromState");
            SYNC_FROM_STATE.setAccessible(true);
            MODE_X = (Integer) staticInt("MODE_X");
            TOP = (Integer) staticInt("TOP");
            ROW_H = (Integer) staticInt("ROW_H");
        } catch (final ReflectiveOperationException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    private StockMaintainerPageSelfCheck() {
    }

    public static void main(final String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        bootClient();

        firstStateChecks();
        pageSwitchChecks();
        manualChoiceChecks();
        multiPageChecks();
        returnToConfiguredPageChecks();
        unchangedPageChecks();
        reopenChecks();
        identicalNumbersChecks();
        commitBeforePageChangeChecks();

        System.out.println("Stock maintainer page checks passed (fresh page defaults to item, "
                + "manual fluid survives same-page refresh, configured rows follow the server, "
                + "page changes are detected by confirmed page number)");
    }

    // ---------------------------------------------------------------- 用例

    /** 首次收到状态：空行是物品，已配置行按服务端类型显示。 */
    private static void firstStateChecks() throws Exception {
        final Fixture fixture = new Fixture();
        final StockMaintainerNetwork.State state = page(0);
        configure(state, 0, MaintainerRule.MODE_FLUID, "water", 4096, 16384, true, false);
        configure(state, 3, MaintainerRule.MODE_ITEM, "iron", 500, 100, true, true);
        fixture.apply(state);

        check(fixture.mode(0) == MaintainerRule.MODE_FLUID, "configured fluid row shows fluid on the first state");
        check(fixture.mode(3) == MaintainerRule.MODE_ITEM, "configured item row shows item on the first state");
        for (int row = 0; row < ROWS; row++) {
            if (row == 0 || row == 3) { continue; }
            check(fixture.mode(row) == MaintainerRule.MODE_ITEM,
                    "empty row " + row + " starts as item on the first state");
        }
    }

    /** 有流体行的页 → 全新空页：8 行必须全是物品。 */
    private static void pageSwitchChecks() throws Exception {
        final Fixture fixture = new Fixture();
        final StockMaintainerNetwork.State second = page(1);
        configure(second, 6, MaintainerRule.MODE_FLUID, "lava", 1000, 1000, true, false);
        fixture.apply(second);
        check(fixture.mode(6) == MaintainerRule.MODE_FLUID, "row 6 is fluid on the page that configures it");

        // 全新第 3 页：服务端这 8 行一个目标都没有。
        fixture.apply(page(2));
        for (int row = 0; row < ROWS; row++) {
            check(fixture.mode(row) == MaintainerRule.MODE_ITEM,
                    "row " + row + " must be item on a brand new empty page");
        }
    }

    /** 新页手动切成流体后，普通刷新不能把它切回物品。 */
    private static void manualChoiceChecks() throws Exception {
        final Fixture fixture = new Fixture();
        fixture.apply(page(2));

        fixture.clickModeChip(1);
        check(fixture.mode(1) == MaintainerRule.MODE_FLUID, "clicking the mode chip switches the empty row to fluid");

        // 同一页的普通刷新（库存/状态变化会重发状态包），不能复位玩家的选择。
        fixture.apply(page(2));
        fixture.apply(page(2));
        check(fixture.mode(1) == MaintainerRule.MODE_FLUID, "a same-page refresh keeps the manual fluid choice");
        for (int row = 0; row < ROWS; row++) {
            if (row == 1) { continue; }
            check(fixture.mode(row) == MaintainerRule.MODE_ITEM, "other empty rows are still item");
        }

        // 标记成功后的状态包：这一行变成服务端配置的流体，模式随之锁定为服务端值。
        final StockMaintainerNetwork.State marked = page(2);
        configure(marked, 1, MaintainerRule.MODE_FLUID, "lava", 0, 0, false, false);
        fixture.apply(marked);
        check(fixture.mode(1) == MaintainerRule.MODE_FLUID, "the configured row keeps the server fluid mode");
        fixture.clickModeChip(1);
        check(fixture.mode(1) == MaintainerRule.MODE_FLUID, "a configured row's mode cannot be switched locally");
    }

    /** 连续翻过多个空页：没有任何一行按位置继承上一页的模式。 */
    private static void multiPageChecks() throws Exception {
        final Fixture fixture = new Fixture();
        final StockMaintainerNetwork.State first = page(0);
        configure(first, 2, MaintainerRule.MODE_FLUID, "lava", 1000, 1000, true, false);
        configure(first, 5, MaintainerRule.MODE_FLUID, "water", 1000, 1000, true, false);
        fixture.apply(first);

        for (int page = 1; page < MaintainerBook.PAGES; page++) {
            fixture.apply(page(page));
            for (int row = 0; row < ROWS; row++) {
                check(fixture.mode(row) == MaintainerRule.MODE_ITEM,
                        "row " + row + " on page " + (page + 1) + " must not inherit the previous page's mode");
            }
        }
    }

    /** 翻回有配置的页：原有目标类型按服务端值恢复。 */
    private static void returnToConfiguredPageChecks() throws Exception {
        final Fixture fixture = new Fixture();
        final StockMaintainerNetwork.State configured = page(1);
        configure(configured, 0, MaintainerRule.MODE_ITEM, "iron", 500, 100, true, true);
        configure(configured, 4, MaintainerRule.MODE_FLUID, "water", 4096, 16384, false, false);
        fixture.apply(configured);
        fixture.apply(page(2));
        check(fixture.mode(4) == MaintainerRule.MODE_ITEM, "the empty page forgot the old fluid row");

        fixture.apply(configured);
        check(fixture.mode(0) == MaintainerRule.MODE_ITEM, "the item row comes back as item");
        check(fixture.mode(4) == MaintainerRule.MODE_FLUID, "the fluid row comes back as fluid");
        check(fixture.container.state.thresholds[4] == 4096, "the fluid row's threshold comes back");
        check(fixture.container.state.batches[4] == 16384, "the fluid row's batch comes back");
        check(!fixture.container.state.enabled[4], "the fluid row's enable state comes back");
        check(fixture.container.state.smartPartBatches[0], "the smart switch comes back per row");
    }

    /** 到末页继续向外翻：页码没变时不清掉本页手动选择。 */
    private static void unchangedPageChecks() throws Exception {
        final Fixture fixture = new Fixture();
        fixture.apply(page(MaintainerBook.PAGES - 1));
        fixture.clickModeChip(7);
        check(fixture.mode(7) == MaintainerRule.MODE_FLUID, "the last page accepts a manual fluid choice");

        // 服务端确认仍然是同一页（页码没变）：不能当成翻页。
        fixture.apply(page(MaintainerBook.PAGES - 1));
        check(fixture.mode(7) == MaintainerRule.MODE_FLUID,
                "a refresh that keeps the page number must not clear the manual choice");
    }

    /** 关闭再打开：空行重新默认物品。 */
    private static void reopenChecks() throws Exception {
        final Fixture fixture = new Fixture();
        fixture.apply(page(2));
        fixture.clickModeChip(3);
        check(fixture.mode(3) == MaintainerRule.MODE_FLUID, "the row was switched to fluid before closing");

        final GuiStockMaintainer reopened = new GuiStockMaintainer(fixture.container);
        reopened.mc = client;
        reopened.width = 427;
        reopened.height = 240;
        reopened.initGui();
        for (int row = 0; row < ROWS; row++) {
            check(modeOf(reopened, fixture.container.state, row) == MaintainerRule.MODE_ITEM,
                    "reopening the GUI resets row " + row + " to item");
        }
    }

    /**
     * 相邻两页同位置的数值完全一样时也必须认得出换页。
     * 用数值变化推断页码是错的；这里把两页填成一模一样的数字，只让页码不同。
     */
    private static void identicalNumbersChecks() throws Exception {
        final Fixture fixture = new Fixture();
        final StockMaintainerNetwork.State second = page(1);
        configure(second, 2, MaintainerRule.MODE_FLUID, "lava", 1000, 1000, true, false);
        fixture.apply(second);

        final StockMaintainerNetwork.State third = page(2);
        for (int row = 0; row < ROWS; row++) {
            third.thresholds[row] = second.thresholds[row];
            third.batches[row] = second.batches[row];
            third.counts[row] = second.counts[row];
            third.countsValid[row] = second.countsValid[row];
        }
        fixture.apply(third);
        check(fixture.mode(2) == MaintainerRule.MODE_ITEM,
                "a page change is detected by the page number, not by the numbers on the rows");
    }

    /**
     * 翻页前的数值提交：合法值照常发出去、非法值回退且什么都不发。
     * 相邻两页同位置数值相同也要能换页——换页只看服务端确认的页码。
     */
    private static void commitBeforePageChangeChecks() throws Exception {
        final Fixture fixture = new Fixture();
        final StockMaintainerNetwork.State second = page(1);
        configure(second, 0, MaintainerRule.MODE_FLUID, "lava", 1000, 1000, true, false);
        fixture.apply(second);

        final RecordingChannel channel = RecordingChannel.install();

        // 合法值：发出 SET_THRESHOLD，行号按当前页换算。
        channel.clear();
        fixture.setThresholdText(0, "2000");
        fixture.commitThreshold(0);
        check(channel.count(StockMaintainerNetwork.SET_THRESHOLD) == 1, "a legal edit is submitted");
        check(channel.lastIndex(StockMaintainerNetwork.SET_THRESHOLD) == MaintainerBook.ROWS_PER_PAGE,
                "the submit carries the row index of the current page");

        // 非法值：不发包，输入框退回上一次的有效值。
        channel.clear();
        fixture.setThresholdText(0, "abc");
        fixture.commitThreshold(0);
        check(channel.count(StockMaintainerNetwork.SET_THRESHOLD) == 0, "an illegal edit sends nothing");
        check("2000".equals(fixture.thresholdText(0)), "an illegal edit falls back to the last accepted value");

        // 点翻页按钮：先提交本页合法编辑，再发翻页请求；待定模式在服务端确认前不动。
        fixture.clickModeChip(3);
        check(fixture.mode(3) == MaintainerRule.MODE_FLUID, "row 3 was switched to fluid before paging");
        channel.clear();
        fixture.setThresholdText(1, "777");
        fixture.clickPage(1);
        check(channel.count(StockMaintainerNetwork.SET_THRESHOLD) == 1, "paging submits the pending legal edit first");
        check(channel.count(StockMaintainerNetwork.PAGE) == 1, "paging sends exactly one page request");
        check(fixture.mode(3) == MaintainerRule.MODE_FLUID,
                "the manual mode survives until the server confirms the new page");
        check(fixture.container.page == 1, "sending a page request does not move the container by itself");
    }

    // ---------------------------------------------------------------- 夹具

    /**
     * 一份干净的界面夹具：真实瓦片 + 真实容器 + 真实 GUI，走真实的 initGui/updateScreen。
     * {@code apply} 复刻 ClientProxy.stockMaintainerState 的两步（换状态、对齐页码），
     * 之后的每帧刷新也由真实的 updateScreen 触发。
     */
    private static final class Fixture {
        final ContainerStockMaintainer container;
        final GuiStockMaintainer gui;

        Fixture() throws Exception {
            this.container = new ContainerStockMaintainer(clientPlayer.inventory, new TileStockMaintainer());
            this.gui = new GuiStockMaintainer(this.container);
            this.gui.mc = client;
            this.gui.width = 427;
            this.gui.height = 240;
            this.gui.initGui();
        }

        void apply(final StockMaintainerNetwork.State state) throws Exception {
            this.container.state = state;
            this.container.page = state.page;
            this.gui.updateScreen();
        }

        int mode(final int row) throws Exception {
            return modeOf(this.gui, this.container.state, row);
        }

        void clickModeChip(final int row) throws Exception {
            // 命中区与 mouseClicked 里的判断一致：模式格是 rowY+4 起、高 9。
            this.gui.mouseClicked(guiLeft(this.gui) + MODE_X + 2,
                    guiTop(this.gui) + TOP + row * ROW_H + 6, 0);
        }

        /** 点翻页按钮：走真实的 actionPerformed（提交本页编辑 → 发页码请求）。 */
        void clickPage(final int buttonId) throws Exception {
            final Method actionPerformed = GuiStockMaintainer.class.getDeclaredMethod(
                    "actionPerformed", net.minecraft.client.gui.GuiButton.class);
            actionPerformed.setAccessible(true);
            actionPerformed.invoke(this.gui, new net.minecraft.client.gui.GuiButton(buttonId, 0, 0,
                    buttonId == 0 ? "<" : ">"));
        }

        void setThresholdText(final int row, final String text) throws Exception {
            field(this.gui, "thresholds", GuiTextField[].class)[row].setText(text);
        }

        String thresholdText(final int row) throws Exception {
            return field(this.gui, "thresholds", GuiTextField[].class)[row].getText();
        }

        void commitThreshold(final int row) throws Exception {
            final Method commit = GuiStockMaintainer.class.getDeclaredMethod("commitThreshold", int.class);
            commit.setAccessible(true);
            commit.invoke(this.gui, row);
        }
    }

    /**
     * 记录型网络通道：把 GUI 真的发出的动作包留下来。
     *
     * <p>离线自检里 {@code new SimpleNetworkWrapper(...)} 会 NPE（它要 FML 的 NetworkRegistry），
     * 所以用 Unsafe 跳过构造，只留一个覆写了 {@code sendToServer} 的空壳实例塞进静态通道字段。
     * 这样 {@code StockMaintainerNetwork.action()} 这条真实发送路径照常跑，只是不外发。</p>
     */
    private static final class RecordingChannel
            extends net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper {
        private final java.util.List<Object> sent = new java.util.ArrayList<>();

        private RecordingChannel() {
            super("ae2us-self-check");
        }

        @Override
        public void sendToServer(final net.minecraftforge.fml.common.network.simpleimpl.IMessage message) {
            this.sent.add(message);
        }

        static RecordingChannel install() throws Exception {
            // Unsafe 跳过构造也跳过字段初始化，sent 要自己补上。
            final RecordingChannel channel = allocate(RecordingChannel.class);
            setField(channel, RecordingChannel.class, "sent", new java.util.ArrayList<>());
            setField(null, StockMaintainerNetwork.class, "channel", channel);
            return channel;
        }

        void clear() {
            this.sent.clear();
        }

        int count(final int operation) throws Exception {
            int total = 0;
            for (final Object message : this.sent) {
                if (operation(message) == operation) { total++; }
            }
            return total;
        }

        int lastIndex(final int operation) throws Exception {
            int index = -1;
            for (final Object message : this.sent) {
                if (operation(message) == operation) { index = (Integer) readPrivate(message, "index"); }
            }
            return index;
        }

        private static int operation(final Object message) throws Exception {
            return (Integer) readPrivate(message, "operation");
        }

        private static Object readPrivate(final Object owner, final String name) throws Exception {
            final Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        }
    }

    /**
     * 真实的客户端玩家 + 空壳 Minecraft。
     * 不能只塞一个空壳玩家：{@code GuiContainer.updateScreen()} 会读 {@code mc.player.isEntityAlive()}，
     * 那是 final 的 getHealth()，空壳没有 dataManager 会直接 NPE。所以走真正的 EntityPlayerSP 构造，
     * 只把连接里的 GameProfile 预先塞好。
     *
     * <p>点模式格会走到真实的 {@code tr()} → {@code I18n.format()}，而 I18n 的语言表只有客户端资源加载器
     * 才会填。这里装一个语言表为空的 Locale（查不到的键原样返回），让真实点击路径能跑完。</p>
     */
    private static void bootClient() throws Exception {
        final net.minecraft.client.network.NetHandlerPlayClient connection =
                allocate(net.minecraft.client.network.NetHandlerPlayClient.class);
        setField(connection, net.minecraft.client.network.NetHandlerPlayClient.class, "profile",
                new GameProfile(UUID.randomUUID(), "self-check"));
        client = allocate(Minecraft.class);
        clientPlayer = new EntityPlayerSP(client, new FakeWorld(), connection,
                new net.minecraft.stats.StatisticsManager(), new net.minecraft.stats.RecipeBook());
        client.player = clientPlayer;

        final net.minecraft.client.resources.Locale locale = new net.minecraft.client.resources.Locale();
        setField(locale, net.minecraft.client.resources.Locale.class, "properties", new java.util.HashMap<>());
        setField(null, net.minecraft.client.resources.I18n.class, "i18nLocale", locale);
    }

    private static final class FakeWorld extends World {
        FakeWorld() {
            super(null, new WorldInfo(new net.minecraft.nbt.NBTTagCompound()),
                    new WorldProviderSurface(), new Profiler(), true);
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected boolean isChunkLoaded(final int x, final int z, final boolean allowEmpty) {
            return false;
        }

        @Override
        public BlockPos getSpawnPoint() {
            return BlockPos.ORIGIN;
        }
    }

    // ---------------------------------------------------------------- 状态构造

    private static StockMaintainerNetwork.State page(final int page) {
        final StockMaintainerNetwork.State state = new StockMaintainerNetwork.State();
        state.page = page;
        state.status = TileStockMaintainer.STATUS_ACTIVE;
        state.fluidReady = true;
        return state;
    }

    private static void configure(final StockMaintainerNetwork.State state, final int row, final int mode,
                                  final String label, final long threshold, final long batch,
                                  final boolean enabled, final boolean smart) {
        state.configured[row] = true;
        state.modes[row] = mode;
        state.labels[row] = label;
        state.thresholds[row] = threshold;
        state.batches[row] = batch;
        state.enabled[row] = enabled;
        state.smartPartBatches[row] = smart;
        state.statuses[row] = MaintainerStatus.STOCKED;
        state.countsValid[row] = true;
        state.counts[row] = threshold;
    }

    // ---------------------------------------------------------------- 反射与断言

    private static int modeOf(final GuiStockMaintainer gui, final StockMaintainerNetwork.State state,
                              final int row) throws Exception {
        return (Integer) MODE_OF.invoke(gui, state, row);
    }

    private static Object staticInt(final String name) throws ReflectiveOperationException {
        final Field field = GuiStockMaintainer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static int guiLeft(final GuiStockMaintainer gui) throws ReflectiveOperationException {
        final Field field = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField("guiLeft");
        field.setAccessible(true);
        return field.getInt(gui);
    }

    private static int guiTop(final GuiStockMaintainer gui) throws ReflectiveOperationException {
        final Field field = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField("guiTop");
        field.setAccessible(true);
        return field.getInt(gui);
    }

    private static void setField(final Object owner, final Class<?> type, final String name, final Object value)
            throws ReflectiveOperationException {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(final Object owner, final String name, final Class<T> type) throws Exception {
        final Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(owner);
    }

    private static sun.misc.Unsafe unsafe;

    @SuppressWarnings("unchecked")
    private static <T> T allocate(final Class<T> type) throws ReflectiveOperationException {
        if (unsafe == null) {
            final Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (sun.misc.Unsafe) field.get(null);
        }
        // 这里只需要一个能装字段的空壳：GUI 初始化只读 mc.player 和它的背包。
        return (T) unsafe.allocateInstance(type);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
