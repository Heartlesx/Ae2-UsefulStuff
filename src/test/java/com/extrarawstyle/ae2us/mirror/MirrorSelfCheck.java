package com.extrarawstyle.ae2us.mirror;

import net.minecraft.nbt.NBTTagCompound;
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
 * 镜像样板供应器的结构自检。离线跑，不启动游戏，不碰 AEApi 单例。
 * <p>
 * 锁死的是"这条路为什么能成立"的前提：样板槽可扩、镜像用原生 DualityInterface、
 * 只推安装面、样板不进存档、绑定能往返、镜像不能当源（防环）、mixin 都挂在配置里。
 */
public final class MirrorSelfCheck {

    private static int failed;

    public static void main(final String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        providerShape();
        bindingStorage();
        mixinWiring();
        resources();
        if (failed > 0) {
            System.out.println(failed + " 项未通过");
            System.exit(1);
        }
        System.out.println("镜像样板供应器自检全部通过");
    }

    // ---------------------------------------------------------------- 部件形态

    private static void providerShape() throws Exception {
        check(PartMirrorProvider.PATTERN_SLOTS == 288, "镜像样板槽 288");
        check(PartMirrorProvider.SOURCE_PATTERN_SLOTS == 36, "源接口样板槽 36（AE2 固定值）");
        check(PartMirrorProvider.PATTERN_SLOTS % PartMirrorProvider.SOURCE_PATTERN_SLOTS == 0,
                "288 是源接口槽数 36 的整数倍");

        // 关键前提：PartModel 构造参数里的每个名字，AE2 都会单独烘焙并在渲染时逐个查表。
        // 出现没注册的名字 → 区块渲染直接抛 IllegalStateException（实机崩溃过一次）。
        // 共用底板只能走 JSON 的 parent，不能进这个列表。
        final Set<String> registered = new LinkedHashSet<>();
        for (final appeng.api.parts.IPartModel model : PartMirrorProvider.MODELS) {
            for (final net.minecraft.util.ResourceLocation location : model.getModels()) {
                registered.add(location.toString());
            }
        }
        check(registered.size() == PartMirrorProvider.MODELS.length,
                "每个状态模型只带一个模型文件（其余靠 JSON parent 继承）");
        for (final String name : registered) {
            check(!name.endsWith("mirror_provider_base"),
                    "底板模型不能进 PartModel 列表：" + name);
            final String path = "/assets/" + name.replace(":", "/models/") + ".json";
            check(resourceExists(path), "PartModel 里的模型文件真实存在：" + name);
        }
        check(registered.contains("ae2us:part/mirror_provider_off")
                        && registered.contains("ae2us:part/mirror_provider_on")
                        && registered.contains("ae2us:part/mirror_provider_channel"),
                "三种状态模型都在列表里");

        // CPU 的候选发现、接口终端、AE2FC 的二合一判断都靠 IInterfaceHost。
        check(appeng.helpers.IInterfaceHost.class.isAssignableFrom(PartMirrorProvider.class),
                "镜像是 IInterfaceHost");

        // 只推安装面，跟 AE2 接口面板一致。
        final Set<String> targets = methodRefs(PartMirrorProvider.class, "getTargets");
        check(contains(targets, "appeng/api/util/AEPartLocation.getFacing()Lnet/minecraft/util/EnumFacing;"),
                "getTargets 取安装面");
        check(!contains(targets, "java/util/EnumSet.allOf(Ljava/lang/Class;)Ljava/util/EnumSet;"),
                "getTargets 不是六面全推");

        // 样板不落盘：写档时删掉 patterns 段，并写绑定列表。
        final Set<String> write = methodRefs(PartMirrorProvider.class, "writeToNBT");
        check(contains(write, "net/minecraft/nbt/NBTTagCompound.removeTag(Ljava/lang/String;)V"),
                "writeToNBT 会删段");
        check(containsString(write, "patterns"), "writeToNBT 删的是 patterns 段");
        check(contains(write, "com/extrarawstyle/ae2us/mirror/MirrorBindings.write"), "writeToNBT 写入绑定");
        final Set<String> read = methodRefs(PartMirrorProvider.class, "readFromNBT");
        check(contains(read, "com/extrarawstyle/ae2us/mirror/MirrorBindings.read"), "readFromNBT 读回绑定");

        // 心跳必须常醒：休眠之后源接口换样板就叫不醒了。
        final Set<String> ticking = methodRefs(PartMirrorProvider.class, "getTickingRequest");
        check(contains(ticking, "type:appeng/api/networking/ticking/TickingRequest"), "自己构造 TickingRequest");
        check(!contains(ticking, "appeng/helpers/DualityInterface.getTickingRequest"),
                "不用源接口的休眠策略");

        // 一旦返回 SLEEP，AE2 会把这个节点移出待 tick 队列，只有 alertDevice/wakeDevice 能叫回来；
        // 镜像没有任何一处调这两个，所以睡一次就是永久停摆（实机表现为 TOP 一直 0/288，
        // 重新绑定也没用，只有区块/网格重载能恢复）。
        final Set<String> tickBody = methodRefs(PartMirrorProvider.class, "tickingRequest");
        check(!tickBody.contains("appeng/api/networking/ticking/TickRateModulation.SLEEP"
                        + "Lappeng/api/networking/ticking/TickRateModulation;"),
                "tickingRequest 本身不产生 SLEEP（只允许经 keepAwake 转换）");
        check(contains(tickBody, "com/extrarawstyle/ae2us/mirror/PartMirrorProvider.keepAwake"),
                "tickingRequest 的返回值经 keepAwake 兜底");
        check(tickBody.contains("appeng/api/networking/ticking/TickRateModulation.SLOWER"
                        + "Lappeng/api/networking/ticking/TickRateModulation;"),
                "断掉激活时退回 SLOWER 而不是睡眠");

        // 运行时验：无论 AE2 那边回什么，镜像的出口都不能是 SLEEP。
        check(PartMirrorProvider.keepAwake(appeng.api.networking.ticking.TickRateModulation.SLEEP)
                        == appeng.api.networking.ticking.TickRateModulation.SLOWER,
                "keepAwake 把 SLEEP 换成 SLOWER");
        for (final appeng.api.networking.ticking.TickRateModulation mode
                : appeng.api.networking.ticking.TickRateModulation.values()) {
            check(PartMirrorProvider.keepAwake(mode)
                            != appeng.api.networking.ticking.TickRateModulation.SLEEP,
                    "keepAwake 出参不会有 SLEEP：" + mode);
        }

        // 事件订阅注解不能漏：总线按注解扫 getMethods()，注解没 @Inherited，
        // 重写父类方法会把父类那两条注解挡掉（漏了就等于收不到任何网络事件）。
        check(hasAnnotation(PartMirrorProvider.class, "chanRender",
                        "appeng.api.networking.events.MENetworkChannelsChanged"),
                "chanRender 带 @MENetworkEventSubscribe");
        check(hasAnnotation(PartMirrorProvider.class, "powerRender",
                        "appeng.api.networking.events.MENetworkPowerStatusChange"),
                "powerRender 带 @MENetworkEventSubscribe");

        // 解绑只解选中的那一条。
        final Set<String> unbind = methodRefs(PartMirrorProvider.class, "unbind");
        check(contains(unbind, "java/util/List.remove(I)Ljava/lang/Object;"), "unbind 只移除命中的一条");
        check(!contains(unbind, "java/util/List.clear()V"), "unbind 不清空整个列表");

        // 绑定不能被外部随手塞进来：样板只读，对外拿到的是空库存。
        final Set<String> byName = methodRefs(PartMirrorProvider.class, "getInventoryByName");
        check(containsString(byName, "patterns"), "getInventoryByName 特判 patterns");
    }

    // ---------------------------------------------------------------- 绑定读写

    private static void bindingStorage() {
        final NBTTagCompound empty = new NBTTagCompound();
        check(MirrorBindings.read(empty).isEmpty(), "空白 NBT 读回零绑定");

        final ArrayList<MirrorBindings.Binding> list = new ArrayList<>();
        list.add(new MirrorBindings.Binding(0, new BlockPos(10, 64, -20)));
        list.add(new MirrorBindings.Binding(-1, new BlockPos(-3, 5, 7)));

        final NBTTagCompound written = new NBTTagCompound();
        MirrorBindings.write(written, list);
        final List<MirrorBindings.Binding> back = MirrorBindings.read(written);
        check(back.size() == 2, "两个绑定往返后仍是两个");
        check(back.get(0).dimension == 0 && back.get(0).pos.equals(new BlockPos(10, 64, -20)),
                "正维度坐标保真");
        check(back.get(1).dimension == -1 && back.get(1).pos.equals(new BlockPos(-3, 5, 7)),
                "负维度与负坐标保真");

        final MirrorBindings.Binding single = back.get(0);
        check(single.matches(0, new BlockPos(10, 64, -20)), "同维度同坐标匹配");
        check(!single.matches(1, new BlockPos(10, 64, -20)), "跨维度不匹配");
        check(!single.matches(0, new BlockPos(11, 64, -20)), "错位坐标不匹配");
    }

    // ---------------------------------------------------------------- mixin 挂载

    private static void mixinWiring() throws Exception {
        final String config = readResource("/mixins.ae2us.json");
        check(config.contains("\"DualityInterfacePatternMixin\""), "扩槽 mixin 在配置里");
        check(config.contains("\"MirrorDualityInvoker\""), "配方表刷新 invoker 在配置里");

        final Class<?> patternMixin = Class.forName("com.extrarawstyle.ae2us.mixin.DualityInterfacePatternMixin");
        check(com.extrarawstyle.ae2us.api.IMirrorPatternSlots.class.isAssignableFrom(patternMixin),
                "扩槽 mixin 实现 IMirrorPatternSlots");
        check(hasMethod(patternMixin, "ae2us$resizePatternSlots", "(I)V"), "扩槽入口签名正确");

        // 部件构造时必须调用扩槽入口，否则还是 36 槽。
        final Set<String> ctor = methodRefs(PartMirrorProvider.class, "<init>");
        check(contains(ctor, "com/extrarawstyle/ae2us/api/IMirrorPatternSlots.ae2us$resizePatternSlots(I)V"),
                "部件构造时把样板槽扩到 288");

        // 阻挡跟随：读源接口的阻挡设置，跟随智能阻挡开关，取最大优先级。
        final Set<String> blocking = methodRefs(PartMirrorProvider.class, "syncBlocking");
        check(contains(blocking, "appeng/api/util/IConfigManager.getSetting"), "syncBlocking 读源接口设置");
        check(contains(blocking, "com/extrarawstyle/ae2us/api/ISmartBlocking.ae2us$isSmartBlocking()Z"),
                "syncBlocking 跟随智能阻挡");
        final Set<String> priority = methodRefs(PartMirrorProvider.class, "syncPriority");
        check(contains(priority, "appeng/helpers/DualityInterface.getPriority()I"), "syncPriority 读源优先级");
        check(contains(priority, "appeng/helpers/DualityInterface.setPriority(I)V"), "syncPriority 写自己优先级");

        // 去重：同一样板只留一份。
        final Set<String> patterns = methodRefs(PartMirrorProvider.class, "syncPatterns");
        check(contains(patterns, "com/extrarawstyle/ae2us/mirror/PartMirrorProvider.containsSame"),
                "syncPatterns 去重");
        check(contains(patterns, "appeng/tile/inventory/AppEngInternalInventory.setTileEntity"),
                "syncPatterns 批量写入前摘掉回调，避免反复重建配方表");

        // 源接口解析拒绝镜像，防环。
        final Set<String> findSource = methodRefs(MirrorBindings.class, "findSource");
        check(contains(findSource, "type:com/extrarawstyle/ae2us/mirror/PartMirrorProvider"),
                "findSource 判掉镜像，避免绑成环");
    }

    // ---------------------------------------------------------------- 资源

    private static void resources() throws Exception {
        for (final String state : new String[]{"off", "on", "channel"}) {
            check(resourceExists("/assets/ae2us/models/part/mirror_provider_" + state + ".json"),
                    "部件模型存在：" + state);
            check(resourceExists("/assets/ae2us/textures/part/mirror_provider_front_" + state + ".png"),
                    "正面贴图存在：" + state);
            check(resourceExists("/assets/ae2us/textures/part/mirror_provider_status_" + state + ".png"),
                    "状态贴图存在：" + state);
            check(readResource("/assets/ae2us/models/part/mirror_provider_" + state + ".json")
                    .contains("\"ae2us:part/mirror_provider_base\""), state + " 模型继承底板");
        }
        check(resourceExists("/assets/ae2us/models/part/mirror_provider_base.json"), "部件底板模型存在");
        check(resourceExists("/assets/ae2us/textures/part/mirror_provider_sides.png"), "侧面贴图存在");
        check(resourceExists("/assets/ae2us/textures/part/mirror_provider_back.png"), "背面贴图存在");
        check(resourceExists("/assets/ae2us/models/item/mirror_pattern_provider.json"), "部件物品模型存在");
        check(resourceExists("/assets/ae2us/models/item/mirror_binder.json"), "绑定器物品模型存在");
        check(resourceExists("/assets/ae2us/textures/items/mirror_binder.png"), "绑定器贴图存在");

        final String base = readResource("/assets/ae2us/models/part/mirror_provider_base.json");
        check(base.contains("ae2us:part/mirror_provider_sides"), "底板引用侧面贴图");
        check(base.contains("ae2us:part/mirror_provider_back"), "底板引用背面贴图");

        final List<String> zh = langKeys("/assets/ae2us/lang/zh_cn.lang");
        final List<String> en = langKeys("/assets/ae2us/lang/en_us.lang");
        check(zh.equals(en), "中英语言文件逐键对应");
        for (final String key : new String[]{
                "item.ae2us.mirror_pattern_provider.name", "item.ae2us.mirror_binder.name",
                "ae2us.mirror.status.empty", "ae2us.mirror.status.bound", "ae2us.mirror.status.offline",
                "ae2us.mirror.status.truncated", "ae2us.mirror.binder.selected",
                "ae2us.mirror.binder.no_selection", "ae2us.mirror.binder.unbind_no_selection",
                "ae2us.mirror.binder.wrong_dimension", "ae2us.mirror.binder.mirror_as_source",
                "ae2us.mirror.binder.not_interface", "ae2us.mirror.binder.already_bound",
                "ae2us.mirror.binder.bound", "ae2us.mirror.binder.unbound",
                "ae2us.mirror.binder.not_bound", "ae2us.mirror.binder.cleared",
                "ae2us.mirror.binder.source_gone"}) {
            check(zh.contains(key), "zh_cn 有 " + key);
            check(en.contains(key), "en_us 有 " + key);
        }
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 收集某个方法里引用到的所有符号：方法调用、类型、字符串常量。
     * 只按方法收窄，别的同名方法不会混进来。
     */
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

    private static boolean hasMethod(final Class<?> type, final String name, final String desc) {
        for (final java.lang.reflect.Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && describe(method).equals(desc)) {
                return true;
            }
        }
        return false;
    }

    /** 这个类自己声明的方法上有没有挂 @MENetworkEventSubscribe（参数类型用来挑重载）。 */
    private static boolean hasAnnotation(final Class<?> type, final String methodName, final String eventType) {
        for (final java.lang.reflect.Method method : type.getDeclaredMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            final Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !params[0].getName().equals(eventType)) {
                continue;
            }
            if (method.isAnnotationPresent(appeng.api.networking.events.MENetworkEventSubscribe.class)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(final java.lang.reflect.Method method) {
        final StringBuilder out = new StringBuilder("(");
        for (final Class<?> param : method.getParameterTypes()) {
            out.append(typeDesc(param));
        }
        return out.append(')').append(typeDesc(method.getReturnType())).toString();
    }

    private static String typeDesc(final Class<?> type) {
        if (type == void.class) { return "V"; }
        if (type == int.class) { return "I"; }
        if (type == boolean.class) { return "Z"; }
        if (type == long.class) { return "J"; }
        if (type.isArray()) { return "[" + typeDesc(type.getComponentType()); }
        return "L" + type.getName().replace('.', '/') + ";";
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

    private static boolean resourceExists(final String path) {
        return MirrorSelfCheck.class.getResource(path) != null;
    }

    private static byte[] readClass(final String resource) throws Exception {
        try (InputStream stream = MirrorSelfCheck.class.getResourceAsStream(resource)) {
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
        try (InputStream stream = MirrorSelfCheck.class.getResourceAsStream(path)) {
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
