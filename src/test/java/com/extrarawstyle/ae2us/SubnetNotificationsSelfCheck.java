package com.extrarawstyle.ae2us;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import com.extrarawstyle.ae2us.mixin.NetworkMonitorRefreshAccessor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/** Runs AE2's real event/cache code with only the FML-owned API replaced in an isolated loader. */
public final class SubnetNotificationsSelfCheck {
    public static void verify() {
        try {
            final ClassLoader loader = new ClassLoader(SubnetNotificationsSelfCheck.class.getClassLoader()) {
                @Override
                protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
                    if (!name.startsWith("appeng.") && !name.startsWith(SubnetNotificationsSelfCheck.class.getName())
                            && !name.equals("com.extrarawstyle.ae2us.SubnetRefresh")) {
                        return super.loadClass(name, resolve);
                    }
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) {
                            try (InputStream input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                                if (input == null) { throw new ClassNotFoundException(name); }
                                final ClassReader reader = new ClassReader(input);
                                final ClassWriter writer = new ClassWriter(0);
                                reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
                                    @Override
                                    public MethodVisitor visitMethod(final int access, final String method,
                                            final String desc, final String signature, final String[] exceptions) {
                                        return new MethodVisitor(Opcodes.ASM5,
                                                super.visitMethod(access, method, desc, signature, exceptions)) {
                                            @Override
                                            public void visitMethodInsn(final int opcode, final String owner,
                                                    final String called, final String descriptor, final boolean itf) {
                                                super.visitMethodInsn(opcode,
                                                        owner.equals("appeng/api/AEApi") ?
                                                                "com/extrarawstyle/ae2us/SubnetNotificationsSelfCheck" : owner,
                                                        owner.equals("appeng/api/AEApi") ? "api" : called,
                                                        descriptor, itf);
                                            }
                                        };
                                    }
                                }, 0);
                                final byte[] bytes = patched(name, writer.toByteArray());
                                type = defineClass(name, bytes, 0, bytes.length);
                            } catch (final java.io.IOException error) {
                                throw new ClassNotFoundException(name, error);
                            }
                        }
                        if (resolve) { resolveClass(type); }
                        return type;
                    }
                }
            };
            loader.loadClass(SubnetNotificationsSelfCheck.class.getName()).getMethod("run").invoke(null);
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError("subnet notification regression", error);
        }
    }

    private static byte[] patched(final String name, final byte[] bytes) throws java.io.IOException {
        if (!name.equals("appeng.me.cache.NetworkMonitor")) { return bytes; }
        final ClassNode target = new ClassNode();
        new ClassReader(bytes).accept(target, 0);
        final String resource = "com/extrarawstyle/ae2us/mixin/NetworkMonitorSubnetMixin.class";
        final ClassNode mixin = new ClassNode();
        try (InputStream input = SubnetNotificationsSelfCheck.class.getClassLoader().getResourceAsStream(resource)) {
            new ClassReader(input).accept(mixin, 0);
        }
        MethodNode hook = null;
        for (final MethodNode method : mixin.methods) {
            if (method.name.equals("ae2us$refreshSkippedPath")) { hook = method; }
        }
        check(hook != null, "notification hook exists");
        boolean injected = false;
        for (final MethodNode method : target.methods) {
            if (!method.name.equals("postChange")) { continue; }
            for (final AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    final MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.owner.equals("java/util/Set") && call.name.equals("add")) {
                        final InsnList invoke = new InsnList();
                        invoke.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        invoke.add(new VarInsnNode(Opcodes.ILOAD, 1));
                        invoke.add(new VarInsnNode(Opcodes.ALOAD, 2));
                        invoke.add(new VarInsnNode(Opcodes.ALOAD, 3));
                        invoke.add(new InsnNode(Opcodes.ACONST_NULL));
                        invoke.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, target.name, hook.name, hook.desc, false));
                        method.instructions.insertBefore(call, invoke);
                        injected = true;
                    }
                }
            }
        }
        check(injected, "real AE2 duplicate-event branch found");
        hook.visibleAnnotations = null;
        hook.invisibleAnnotations = null;
        target.methods.add(hook);
        target.interfaces.add("com/extrarawstyle/ae2us/mixin/NetworkMonitorRefreshAccessor");
        final MethodNode refresh = new MethodNode(Opcodes.ACC_PUBLIC, "ae2us$refreshStorage", "()V", null, null);
        refresh.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        refresh.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, target.name, "forceUpdate", "()V", false));
        refresh.instructions.add(new InsnNode(Opcodes.RETURN));
        target.methods.add(refresh);
        final ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        target.accept(output);
        return output.toByteArray();
    }

    private static final appeng.api.storage.channels.IItemStorageChannel CHANNEL =
            new appeng.api.storage.channels.IItemStorageChannel() {
                @Override public IItemList<IAEItemStack> createList() { return list(); }
                @Override public IAEItemStack createStack(final Object value) { throw new AssertionError(); }
                @Override public IAEItemStack readFromPacket(final io.netty.buffer.ByteBuf value) { throw new AssertionError(); }
                @Override public IAEItemStack createFromNBT(final net.minecraft.nbt.NBTTagCompound value) { throw new AssertionError(); }
            };
    private static final appeng.api.storage.IStorageHelper STORAGE = (appeng.api.storage.IStorageHelper)
            Proxy.newProxyInstance(SubnetNotificationsSelfCheck.class.getClassLoader(),
                    new Class<?>[]{appeng.api.storage.IStorageHelper.class}, (proxy, method, args) -> {
                        if (method.getName().equals("storageChannels")) { return Collections.singletonList(CHANNEL); }
                        if (method.getName().equals("getStorageChannel")) { return CHANNEL; }
                        throw new AssertionError("unexpected storage call: " + method);
                    });

    @SuppressWarnings("unchecked")
    private static IItemList<IAEItemStack> list() {
        final IAEItemStack[] entry = {null};
        return (IItemList<IAEItemStack>) Proxy.newProxyInstance(SubnetNotificationsSelfCheck.class.getClassLoader(),
                new Class<?>[]{IItemList.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "add": case "addStorage":
                            if (args[0] != null) {
                                if (entry[0] == null) { entry[0] = ((IAEItemStack) args[0]).copy(); }
                                else { entry[0].incStackSize(((IAEItemStack) args[0]).getStackSize()); }
                            }
                            return null;
                        case "findPrecise": case "getFirstItem": return entry[0];
                        case "iterator": return entry[0] != null && entry[0].getStackSize() > 0
                                ? Collections.singletonList(entry[0]).iterator() : Collections.emptyIterator();
                        case "resetStatus": if (entry[0] != null) { entry[0].reset(); } return null;
                        case "isEmpty": return entry[0] == null || entry[0].getStackSize() <= 0;
                        case "size": return entry[0] == null ? 0 : 1;
                        default: throw new AssertionError("unexpected list call: " + method);
                    }
                });
    }

    public static appeng.api.IAppEngApi api() {
        return (appeng.api.IAppEngApi) Proxy.newProxyInstance(SubnetNotificationsSelfCheck.class.getClassLoader(),
                new Class<?>[]{appeng.api.IAppEngApi.class}, (proxy, method, args) -> {
                    if (method.getName().equals("storage")) { return STORAGE; }
                    throw new AssertionError("unexpected API call: " + method);
                });
    }

    public static void run() throws Exception {
        final Fixture child = new Fixture();
        final Fixture parent = new Fixture();
        child.monitor.addListener(new IMEMonitorHandlerReceiver<IAEItemStack>() {
            @Override public boolean isValid(final Object token) { return true; }
            @Override public void postChange(final appeng.api.networking.storage.IBaseMonitor<IAEItemStack> monitor,
                    final Iterable<IAEItemStack> changes, final IActionSource source) {
                parent.monitor.change(Collections.emptyList(), source);
                parent.monitor.change(changes, source);
            }
            @Override public void onListUpdate() { }
        }, parent);
        parent.monitor.stock = child.monitor.stock;
        parent.monitor.getStorageList().addStorage(stack(160000));
        child.monitor.stock[0] = 160000;
        final IActionSource source = new appeng.me.helpers.BaseActionSource();
        for (int i = 0; i < 8; i++) {
            final long change = i < 4 ? 60000 : -60000;
            child.monitor.stock[0] += change;
            child.monitor.change(Collections.singletonList(stack(change)), source);
            child.tick();
            parent.tick();
            SubnetRefresh.onServerTick(new net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent(
                    net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END));
            // Legacy logic only refreshes on a change of nesting state; subsequent events stay lost.
            check(count(parent.monitor) == child.monitor.stock[0],
                    "main network cache must follow every filtered subnet event: expected "
                            + child.monitor.stock[0] + ", got " + count(parent.monitor));
        }
        listUpdateChecks();
        hookChecks();
        System.out.println("Subnet notification checks passed (real AE2 dispatch, stock changes, list updates, coalescing and cycles)");
    }

    private static void listUpdateChecks() {
        final Fixture child = new Fixture();
        final Fixture parent = new Fixture();
        final Fixture top = new Fixture();
        parent.monitor.stock = child.monitor.stock;
        top.monitor.stock = child.monitor.stock;
        child.monitor.addListener(new Path(parent), parent);
        parent.monitor.addListener(new Path(top), top);
        top.monitor.addListener(new Path(child), child);
        child.monitor.stock[0] = 400000;
        for (int i = 0; i < 100; i++) { SubnetRefresh.request(child.monitor); }
        SubnetRefresh.onServerTick(new net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent(
                net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START));
        check(child.monitor.scans == 0, "refresh is deferred until the end of the server tick");
        drain();
        check(count(parent.monitor) == 400000 && count(top.monitor) == 400000,
                "whole-list changes propagate through multiple subnet levels");
        check(child.monitor.scans == 1 && parent.monitor.scans == 1 && top.monitor.scans == 1,
                "a refresh wave coalesces requests and visits cyclic networks only once");
        drain();
        check(child.monitor.scans == 1 && parent.monitor.scans == 1 && top.monitor.scans == 1,
                "cycles do not keep refreshing on idle ticks");
        child.monitor.stock[0] = 0;
        SubnetRefresh.request(child.monitor);
        drain();
        check(count(parent.monitor) == 0 && count(top.monitor) == 0,
                "whole-list refresh removes exhausted stock from every parent");
        child.monitor.stock[0] = 12345;
        SubnetRefresh.request(child.monitor);
        drain();
        check(count(top.monitor) == 12345, "previously absent stock becomes visible without reconnecting");
    }

    private static void drain() {
        SubnetRefresh.onServerTick(new net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent(
                net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END));
    }

    private static void hookChecks() throws Exception {
        final String[] hooks = {"NetworkMonitorSubnetMixin", "NetworkMonitorRefreshAccessor", "StorageBusSubnetMixin"};
        final String config;
        try (java.io.Reader input = new java.io.InputStreamReader(SubnetNotificationsSelfCheck.class
                .getResourceAsStream("/mixins.ae2us.json"), java.nio.charset.StandardCharsets.UTF_8)) {
            config = com.google.common.io.CharStreams.toString(input);
        }
        for (final String hook : hooks) { check(config.contains("\"" + hook + "\""), "registered mixin: " + hook); }
        final ClassNode busMixin = new ClassNode();
        try (InputStream input = SubnetNotificationsSelfCheck.class.getResourceAsStream(
                "/com/extrarawstyle/ae2us/mixin/StorageBusSubnetMixin.class")) {
            new ClassReader(input).accept(busMixin, 0);
        }
        final Set<String> calls = new HashSet<>();
        for (final MethodNode method : busMixin.methods) {
            for (final AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    final MethodInsnNode call = (MethodInsnNode) instruction;
                    calls.add(call.owner + "." + call.name);
                }
            }
        }
        check(calls.contains("appeng/me/helpers/AENetworkProxy.isActive")
                        && calls.contains("appeng/api/config/AccessRestriction.hasPermission")
                        && calls.contains("appeng/me/helpers/AENetworkProxy.getStorage")
                        && calls.contains("com/extrarawstyle/ae2us/SubnetRefresh.request"),
                "storage bus forwards list updates to its active readable network");
    }

    private static final class Path implements IMEMonitorHandlerReceiver<IAEItemStack> {
        private final Fixture parent;
        private Path(final Fixture parent) { this.parent = parent; }
        @Override public boolean isValid(final Object token) { return true; }
        @Override public void postChange(final appeng.api.networking.storage.IBaseMonitor<IAEItemStack> monitor,
                final Iterable<IAEItemStack> changes, final IActionSource source) {
            parent.monitor.change(changes, source);
        }
        @Override public void onListUpdate() { SubnetRefresh.request(parent.monitor); }
    }

    private static final class Fixture {
        private final Monitor monitor;
        private final GridStorageCache cache;
        private Fixture() {
            final IGrid grid = (IGrid) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IGrid.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("postEvent")) { return args[0]; }
                        if (method.getName().equals("getCache")) { return null; }
                        throw new AssertionError("unexpected grid call: " + method);
                    });
            cache = new GridStorageCache(grid);
            monitor = new Monitor(cache);
        }
        private void tick() throws Exception {
            final Method tick = NetworkMonitor.class.getDeclaredMethod("onTick");
            tick.setAccessible(true);
            tick.invoke(monitor);
        }
    }

    private static final class Monitor extends NetworkMonitor<IAEItemStack> {
        private long[] stock = {0};
        private int scans;
        private Monitor(final GridStorageCache cache) {
            super(cache, STORAGE.getStorageChannel(appeng.api.storage.channels.IItemStorageChannel.class));
        }
        private void change(final Iterable<IAEItemStack> changes, final IActionSource source) {
            super.postChange(true, changes, source);
        }
        @Override public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out) {
            scans++;
            out.addStorage(stack(stock[0]));
            return out;
        }
    }

    private static IAEItemStack stack(final long size) {
        return (IAEItemStack) Proxy.newProxyInstance(SubnetNotificationsSelfCheck.class.getClassLoader(),
                new Class<?>[]{IAEItemStack.class}, new java.lang.reflect.InvocationHandler() {
                    private long count = size;
                    @Override public Object invoke(final Object proxy, final Method method, final Object[] args) {
                        switch (method.getName()) {
                            case "getStackSize": return count;
                            case "setStackSize": count = (Long) args[0]; return proxy;
                            case "incStackSize": count += (Long) args[0]; return null;
                            case "decStackSize": count -= (Long) args[0]; return null;
                            case "getCountRequestable": return 0L;
                            case "setCountRequestable": case "setCraftable": return proxy;
                            case "isCraftable": return false;
                            case "isMeaningful": return count != 0;
                            case "isItem": return true;
                            case "isFluid": return false;
                            case "copy": return stack(count);
                            case "empty": return stack(0);
                            case "reset": count = 0; return proxy;
                            case "add": count += ((IAEItemStack) args[0]).getStackSize(); return null;
                            case "hashCode": return 1;
                            case "equals": return args[0] instanceof IAEItemStack;
                            case "toString": return "uranium=" + count;
                            default: throw new AssertionError("unexpected stack call: " + method);
                        }
                    }
                });
    }

    private static long count(final Monitor monitor) {
        final IAEItemStack found = monitor.getStorageList().findPrecise(stack(1));
        return found == null ? 0 : found.getStackSize();
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
