package com.extrarawstyle.ae2us.pickup;

import com.extrarawstyle.ae2us.FeatureConfig;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 中键拾取的离线自检。不启动游戏，不碰 AEApi 单例（那需要 FML 运行环境）。
 * <p>
 * 锁死的是各条决策的边界：只在快捷栏找空位、背包已有就不取物、
 * 最多一次一整堆、没有样板就什么都不做、开关默认开。
 */
public final class PickupSelfCheck {

    private static int failed;

    public static void main(final String[] args) throws Exception {
        Bootstrap.register();
        hotbarRules();
        amountRules();
        itemMatching();
        serviceShape();
        clientShape();
        configAndLang();
        if (failed > 0) {
            System.out.println(failed + " 项未通过");
            System.exit(1);
        }
        System.out.println("中键拾取自检全部通过");
    }

    // ---------------------------------------------------------------- 快捷栏

    private static void hotbarRules() {
        final InventoryPlayer inventory = new InventoryPlayer(null);

        // 全空的背包：第一个空位就是 0。
        check(PickupLogic.firstEmptyHotbarSlot(inventory) == 0, "空背包的第一个空位是 0");

        // 主背包第 20 格空着，但快捷栏满了 → 仍然算"没有空位"。
        // 这条是核心约束：绝不去主背包找位，也不挤掉已有的东西。
        for (int slot = 0; slot < PickupLogic.HOTBAR_SIZE; slot++) {
            inventory.mainInventory.set(slot, new ItemStack(Items.STICK, 64));
        }
        check(PickupLogic.firstEmptyHotbarSlot(inventory) == -1, "快捷栏满了就没有空位（不去主背包找）");
        check(inventory.mainInventory.get(20).isEmpty(), "主背包第 20 格确实是空的（前提成立）");

        // 腾出一格就立刻可用。
        inventory.mainInventory.set(4, ItemStack.EMPTY);
        check(PickupLogic.firstEmptyHotbarSlot(inventory) == 4, "腾出的第 4 格被认成空位");

        // 背包已有：返回所在槽位，中键不取物只切格。
        final InventoryPlayer held = new InventoryPlayer(null);
        held.mainInventory.set(6, new ItemStack(Items.CLAY_BALL, 3));
        final int found = PickupLogic.hotbarSlotOf(held, new ItemStack(Items.CLAY_BALL, 1));
        check(found == 6, "背包第 6 格有黏土球时返回 6（数量不同也算有）");
        check(PickupLogic.hotbarSlotOf(held, new ItemStack(Items.STICK)) == -1, "没有的物品返回 -1");

        // 主背包（快捷栏之外）里的不算"手边有"。
        final InventoryPlayer deep = new InventoryPlayer(null);
        deep.mainInventory.set(30, new ItemStack(Items.CLAY_BALL, 1));
        check(PickupLogic.hotbarSlotOf(deep, new ItemStack(Items.CLAY_BALL, 1)) == -1,
                "只在主背包里的不算手边有");
    }

    // ---------------------------------------------------------------- 数量

    private static void amountRules() {
        check(PickupLogic.TARGET_AMOUNT == 64, "一次的目标数量是一整堆 64");
        check(PickupLogic.amountToTake(1000) == 64, "网络很多时只取 64");
        check(PickupLogic.amountToTake(64) == 64, "刚好 64 时取 64");
        check(PickupLogic.amountToTake(30) == 30, "只有 30 时全给");
        check(PickupLogic.amountToTake(1) == 1, "只有 1 个时给 1 个");
        check(PickupLogic.amountToTake(0) == 0, "没有时不取");
        check(PickupLogic.amountToTake(-5) == 0, "负数按没有处理");
    }

    // ---------------------------------------------------------------- 物品比对

    private static void itemMatching() {
        check(PickupLogic.sameItem(new ItemStack(Items.CLAY_BALL), new ItemStack(Items.CLAY_BALL)), "同物品算相同");
        check(!PickupLogic.sameItem(new ItemStack(Items.CLAY_BALL), new ItemStack(Items.STICK)), "不同物品不算相同");
        check(PickupLogic.sameItem(ItemStack.EMPTY, ItemStack.EMPTY), "两个空算相同");
        check(!PickupLogic.sameItem(new ItemStack(Items.CLAY_BALL), ItemStack.EMPTY), "空与非空不算相同");

        // NBT 严格匹配：带自定义名的泥土 ≠ 普通泥土。
        final ItemStack named = new ItemStack(Items.CLAY_BALL);
        named.setStackDisplayName("特殊泥土");
        check(!PickupLogic.sameItem(named, new ItemStack(Items.CLAY_BALL)), "NBT 不同不算相同");
        check(PickupLogic.sameItem(named, named.copy()), "NBT 相同算相同");
    }

    // ---------------------------------------------------------------- 服务端结构

    private static void serviceShape() throws Exception {
        // 网络没货时：先查样板，有样板才下单，没样板直接返回。
        final Set<String> handle = methodRefs(PickupService.class, "handle");
        check(contains(handle, "com/extrarawstyle/ae2us/pickup/PickupService.hasPatternFor"),
                "没货时先查样板");
        check(contains(handle, "com/extrarawstyle/ae2us/pickup/PickupTerminals.openCraftingAmount"),
                "有样板才打开下单界面");
        check(contains(handle, "com/extrarawstyle/ae2us/FeatureConfig.middleClickPickupEnabled"),
                "服务端也检查功能开关");

        // 开关关掉就什么都不做。
        check(contains(handle, "com/extrarawstyle/ae2us/pickup/PickupTerminals.find"), "服务端自己找终端");

        // 有没有货不能查网络清单，必须直接问"能抽多少"（SIMULATE）。
        // 清单与真实可抽取量并不总一致：无限元件这类存储在清单里可能给出数量 0、
        // 或者根本不列出来，但 extractItems 是拿得到的（实机验过：terminal 里看得见
        // 的线缆在清单元数据里查不到）。
        check(contains(handle, "com/extrarawstyle/ae2us/pickup/PickupTerminals$Found.simulateExtract"),
                "用 simulateExtract 探测库存");
        final Set<String> sim = methodRefs(PickupTerminals.Found.class, "simulateExtract");
        check(contains(sim, "appeng/api/storage/data/IAEItemStack.copy"),
                "探测是复制一份再做，不动原对象");
        check(contains(sim, "appeng/api/config/Actionable.SIMULATE"),
                "探测用 SIMULATE，不动库存");

        // extractItems 的返回值是"实际抽走的数量"，不是剩余量。
        // 按剩余量去减（amount - 返回值）会永远算出 0，表现就是网络里有货却什么都取不到。
        final byte[] extractBytes = readClass("/com/extrarawstyle/ae2us/pickup/PickupService.class");
        // 101 = LSUB（long 减法）。按剩余量反算必然出现这一步减法。
        check(!bodyOf(extractBytes, "extract").contains("op:101"),
                "取物不再按'剩余量'做减法反算");
        check(bodyOf(extractBytes, "extract").contains("min;"),
                "取到的数量直接受请求量封顶");

        // 下单界面用 AE2 原生的那个，不是自造的。
        final Set<String> open = methodRefs(PickupTerminals.class, "openCraftingAmount");
        check(contains(open, "appeng/util/Platform.openGUI"), "用 AE2 的 openGUI");
        check(open.contains("appeng/core/sync/GuiBridge.GUI_CRAFTING_AMOUNT"
                        + "Lappeng/core/sync/GuiBridge;"),
                "打开的是 AE2 原生的合成数量界面");
        check(contains(open, "appeng/container/implementations/ContainerCraftAmount.setItemToCraft"),
                "把目标物品填进下单界面");

        // 取物走终端自己的 extractItems（它内部判过网络是否连上）。
        final Set<String> extract = methodRefs(PickupTerminals.Found.class, "extract");
        check(contains(extract, "appeng/helpers/WirelessTerminalGuiObject.extractItems"),
                "取物走终端自己的 extractItems");

        // 终端识别走 AE2 的注册表，不写死任何模组的类名。
        final Set<String> probe = methodRefs(PickupTerminals.class, "probe");
        check(contains(probe, "appeng/api/features/IWirelessTermRegistry.getWirelessTerminalHandler"),
                "终端识别走 IWirelessTermHandler 注册表");
        check(contains(probe, "appeng/api/features/IWirelessTermHandler.hasPower"),
                "没电的终端不算可用");
    }

    // ---------------------------------------------------------------- 客户端结构

    private static void clientShape() throws Exception {
        final Set<String> mouse = methodRefs(ItemPickHandler.class, "onMouse");
        final Set<String> tick = methodRefs(ItemPickHandler.class, "onClientTick");

        check(contains(mouse, "com/extrarawstyle/ae2us/FeatureConfig.middleClickPickupEnabled"),
                "客户端检查功能开关");
        check(contains(mouse, "com/extrarawstyle/ae2us/pickup/PickupTerminals.clientUsable"),
                "终端不可用时完全不干预");
        // 真正的处理放在逻辑 tick 里：鼠标事件按帧触发，帧内插值数据还没稳定，
        // 在帧里做射线会打偏（实机表现就是"跑起来要按好几下"）。
        check(contains(tick, "com/extrarawstyle/ae2us/pickup/PickupLogic.firstEmptyHotbarSlot"),
                "逻辑 tick 里判断快捷栏有没有位");
        check(contains(tick, "com/extrarawstyle/ae2us/pickup/PickupNetwork.request"), "发请求给服务端");
        check(containsMethod(mouse, "cancel"), "鼠标事件里取消原版中键");

        // 创造模式保持原版：读 capabilities.isCreativeMode 之后要提前返回。
        check(contains(mouse, "net/minecraft/entity/player/PlayerCapabilities.isCreativeMode")
                        || contains(mouse, "net/minecraft/entity/player/PlayerCapabilities"),
                "客户端判创造模式");

        // 只在没开界面时接管。
        check(contains(mouse, "net/minecraft/client/Minecraft.currentScreen")
                        || contains(mouse, "type:net/minecraft/client/gui/GuiScreen"),
                "开着界面时不接管");

        // 视线必须当场重算：objectMouseOver 是上一逻辑 tick 的缓存，移动时会失效。
        final Set<String> pick = methodRefs(ItemPickHandler.class, "pickStack");
        check(contains(pick, "com/extrarawstyle/ae2us/pickup/ItemPickHandler.rayTrace"),
                "pickStack 走当场重算的视线");
        final Set<String> trace = methodRefs(ItemPickHandler.class, "rayTrace");
        check(containsMethod(trace, "rayTraceBlocks"), "当场做射线检测");
        check(containsMethod(trace, "getPositionEyes"), "用真实的眼位");
        check(containsMethod(trace, "getLook"), "用真实的朝向");
        check(!contains(mouse, "Minecraft.objectMouseOver"), "不再复用缓存的视线结果");

        // 网络包 ID 24 用在服务端方向。
        final Set<String> register = methodRefs(PickupNetwork.class, "register");
        check(contains(register, "net/minecraftforge/fml/common/network/simpleimpl/SimpleNetworkWrapper.registerMessage"),
                "注册网络包");

        mouseEventIsCancelable();
    }

    /**
     * 中键处理必须挂在可取消的 MouseEvent 上。
     * <p>
     * 这里锁的是实机崩过的那个坑：InputEvent.MouseInputEvent 没有 @Cancelable，
     * 对它调 setCanceled 直接抛 UnsupportedOperationException 打崩客户端；
     * 而且它在原版处理之后才广播，拦不住原版中键。
     */
    private static void mouseEventIsCancelable() throws Exception {
        // 必须监听 MouseEvent，不能是 InputEvent。
        boolean listensMouseEvent = false;
        for (final java.lang.reflect.Method method : ItemPickHandler.class.getDeclaredMethods()) {
            if (!method.getName().equals("onMouse")) {
                continue;
            }
            final Class<?>[] params = method.getParameterTypes();
            if (params.length != 1) {
                continue;
            }
            check(!params[0].getName().equals("net.minecraftforge.fml.common.gameevent.InputEvent$MouseInputEvent")
                            && !params[0].getName().equals("net.minecraftforge.fml.common.gameevent.InputEvent"),
                    "监听的不是不可取消的 InputEvent");
            if (params[0].getName().equals("net.minecraftforge.client.event.MouseEvent")) {
                listensMouseEvent = true;
                // 直接验这个事件类确实标了 @Cancelable——这一条能真正抓住"换回不可取消事件"的改动。
                // 不 new 实例：MouseEvent 的构造器会去读 LWJGL 的鼠标状态，离线环境没有。
                check(params[0].isAnnotationPresent(net.minecraftforge.fml.common.eventhandler.Cancelable.class),
                        "MouseEvent 标了 @Cancelable（可安全 setCanceled）");
            }
        }
        check(listensMouseEvent, "监听的是 net.minecraftforge.client.event.MouseEvent");

        // 取消前必须判 isCancelable，避免同样的崩溃再犯。
        // 注意字节码里的调用点是 MouseEvent.xxx（继承方法按接收者类型解析），不是 Event.xxx。
        final Set<String> cancel = methodRefs(ItemPickHandler.class, "cancel");
        check(contains(cancel, "net/minecraftforge/client/event/MouseEvent.isCancelable"),
                "setCanceled 之前检查了 isCancelable");
        check(contains(cancel, "net/minecraftforge/client/event/MouseEvent.setCanceled"),
                "确实会取消事件");
    }

    // ---------------------------------------------------------------- 开关与文案

    private static void configAndLang() throws Exception {
        // 开关必须真实存在且默认为开（读字段初值；没加载过配置时就是默认值）。
        check(readDefault(new String[]{"enableMiddleClickPickup"}), "功能开关 enableMiddleClickPickup 存在且默认为开");
        check(FeatureConfig.middleClickPickupEnabled() == FeatureConfig.enableMiddleClickPickup,
                "middleClickPickupEnabled() 直接反映开关（不受其它兼容逻辑影响）");

        final List<String> zh = langKeys("/assets/ae2us/lang/zh_cn.lang");
        final List<String> en = langKeys("/assets/ae2us/lang/en_us.lang");
        check(zh.equals(en), "中英语言文件逐键对应");
        check(zh.contains("ae2us.pickup.no_hotbar_space"), "zh_cn 有快捷栏提示");
        check(en.contains("ae2us.pickup.no_hotbar_space"), "en_us 有快捷栏提示");
    }

    /** 字段是不是 boolean 且当前为 true（自检进程没加载过 cfg，拿到的就是声明默认值）。 */
    private static boolean readDefault(final String[] fieldNames) {
        try {
            final java.lang.reflect.Field field = FeatureConfig.class.getDeclaredField(fieldNames[0]);
            return field.getType() == boolean.class && field.getBoolean(null);
        } catch (final Throwable missing) {
            return false;
        }
    }

    // ---------------------------------------------------------------- 工具

    private static Set<String> methodRefs(final Class<?> type, final String methodName) throws Exception {
        final byte[] classBytes = readClass("/" + type.getName().replace('.', '/') + ".class");
        final Set<String> refs = new LinkedHashSet<>();
        final boolean[] matched = new boolean[1];
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                    final String signature, final String[] exceptions) {
                if (!name.equals(methodName)) {
                    return null;
                }
                matched[0] = true;
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(final int opcode, final String owner, final String name2,
                            final String desc, final boolean itf) {
                        refs.add(owner + "." + name2 + desc);
                    }

                    @Override
                    public void visitFieldInsn(final int opcode, final String owner, final String name2,
                            final String desc) {
                        refs.add(owner + "." + name2 + desc);
                    }

                    @Override
                    public void visitTypeInsn(final int opcode, final String type) {
                        refs.add("type:" + type);
                    }

                    @Override
                    public void visitLdcInsn(final Object value) {
                        if (value instanceof String) {
                            refs.add("str:" + value);
                        }
                    }
                };
            }
        }, 0);
        if (!matched[0]) {
            throw new AssertionError("找不到方法：" + type.getName() + "." + methodName);
        }
        return refs;
    }

    private static boolean contains(final Set<String> refs, final String needle) {
        for (final String ref : refs) {
            if (ref.startsWith(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsString(final Set<String> refs, final String text) {
        return refs.contains("str:" + text);
    }

    /**
     * 把某个方法的完整字节码转成可搜索的文本（含操作码助记符）。
     * 用来验"方法体里有没有做减法"这类控制流性质，符号表看不出来。
     */
    private static String bodyOf(final byte[] classBytes, final String methodName) {
        final StringBuilder out = new StringBuilder();
        final boolean[] matched = new boolean[1];
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                    final String signature, final String[] exceptions) {
                if (!name.equals(methodName)) {
                    return null;
                }
                matched[0] = true;
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitInsn(final int opcode) {
                        out.append("op:").append(opcode).append(';');
                    }

                    @Override
                    public void visitMethodInsn(final int opcode, final String owner, final String name2,
                            final String desc, final boolean itf) {
                        out.append(name2).append(';');
                    }
                };
            }
        }, 0);
        if (!matched[0]) {
            throw new AssertionError("找不到方法：" + methodName);
        }
        return out.toString();
    }

    /**
     * 按方法名匹配，不管接收者类型。
     * 继承来的方法在字节码里按<b>接收者</b>类型解析（例如 entity.getLook() 的 owner 是
     * EntityPlayerSP 而不是 Entity），写死声明类会让断言假红。
     */
    private static boolean containsMethod(final Set<String> refs, final String methodName) {
        for (final String ref : refs) {
            final int paren = ref.indexOf('(');
            if (paren < 0) {
                continue;
            }
            final int dot = ref.lastIndexOf('.', paren);
            if (dot >= 0 && ref.substring(dot + 1, paren).equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readClass(final String resource) throws Exception {
        try (InputStream stream = PickupSelfCheck.class.getResourceAsStream(resource)) {
            if (stream == null) { throw new AssertionError("类不在测试类路径上：" + resource); }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String readResource(final String path) throws Exception {
        try (InputStream stream = PickupSelfCheck.class.getResourceAsStream(path)) {
            if (stream == null) { throw new AssertionError("资源不存在：" + path); }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static List<String> langKeys(final String path) throws Exception {
        final List<String> keys = new ArrayList<>();
        for (final String line : readResource(path).split("\n")) {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            final int eq = trimmed.indexOf('=');
            keys.add(eq < 0 ? trimmed : trimmed.substring(0, eq));
        }
        return keys;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) {
            failed++;
            System.out.println("  ✗ " + message);
        }
    }
}
