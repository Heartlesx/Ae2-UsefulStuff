package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.compat.RecipeTransferButtonCompat;
import com.extrarawstyle.ae2us.mixin.FeatureMixinPlugin;
import com.extrarawstyle.ae2us.mixin.compat.RecipeTransferButtonCompatMixin;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.Shadow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * HEI 兼容层的离线自检：AE2 v0.56.7 调用的 RecipeTransferButton.init 在 HEI 4.35.0 里
 * 被改名成 update，这里补一个 init 转发回去。补错了会直接把游戏打崩（方法冲突或调用解析不了），
 * 而这条路径没法在无游戏环境里跑，所以把能离线验证的部分全验了：
 * <ol>
 *     <li>判定函数对真实的 4.32.0 / 4.35.0 类文件给出的结论正确；</li>
 *     <li>补丁类本身结构对：abstract、@Mixin 指向目标类且 remap=false、补的是
 *         public 实例方法 init(Container, EntityPlayer)、update 是 @Shadow 抽象声明；</li>
 *     <li>补丁确实注册进了 mixins.ae2us.jei.json 的 client 段（漏注册等于没修）。</li>
 * </ol>
 * 样本 jar 取自目标整合包 mods 目录，放在工程 libs 下，路径由 build.gradle 用系统属性传进来。
 */
public final class HeiCompatSelfCheck {

    private static final String TARGET_ENTRY = "mezz/jei/gui/recipes/RecipeTransferButton.class";
    private static final String OLD_HEI = "HadEnoughItems_1.12.2-4.32.0.jar";
    private static final String NEW_HEI = "HadEnoughItems_1.12.2-4.35.0.jar";
    private static final String MIXIN_NAME = "compat.RecipeTransferButtonCompatMixin";
    private static final String MIXIN_ENTRY =
            "com/extrarawstyle/ae2us/mixin/compat/RecipeTransferButtonCompatMixin.class";

    public static void main(final String[] args) throws Exception {
        final Path libs = libsDirectory();

        check(RecipeTransferButtonCompat.detect(readEntry(libs.resolve(OLD_HEI), TARGET_ENTRY))
                        == RecipeTransferButtonCompat.State.NOT_NEEDED,
                "4.32.0 自带 init，判定为不需要兼容层");
        check(RecipeTransferButtonCompat.detect(readEntry(libs.resolve(NEW_HEI), TARGET_ENTRY))
                        == RecipeTransferButtonCompat.State.NEEDED,
                "4.35.0 没有 init 但有 update，判定为需要补 init");
        check(RecipeTransferButtonCompat.detect(readEntry(libs.resolve(NEW_HEI),
                        "mezz/jei/gui/recipes/RecipeLayout.class")) == RecipeTransferButtonCompat.State.UNSUPPORTED,
                "两个方法都没有的类判定为不支持，不会被硬塞方法");
        // 测试类路径上挂的是 libs 里的 4.32.0，这条走的是游戏里真正用的资源读取路径。
        check(RecipeTransferButtonCompat.state() == RecipeTransferButtonCompat.State.NOT_NEEDED,
                "按类路径上的 HEI 4.32.0 走资源读取路径，同样判定为不需要兼容层");

        checkPatchClass();
        checkRegistered();
        checkMixinPackageHygiene();

        System.out.println("HEI compat checks passed (4.32.0/4.35.0 detection, patch structure, config registration, mixin package hygiene)");
    }

    private static void checkPatchClass() throws IOException {
        final Class<?> patch = RecipeTransferButtonCompatMixin.class;
        check(Modifier.isAbstract(patch.getModifiers()) && !Modifier.isFinal(patch.getModifiers()),
                "补丁类是 abstract 且非 final（@Shadow 抽象方法的前提）");

        // @Mixin 是 CLASS 保留级注解，反射看不到，只能读字节码。
        final String[] target = new String[1];
        final Boolean[] remap = new Boolean[1];
        readMixinAnnotation(readOwnClass(), target, remap);
        check(RecipeTransferButtonCompat.TARGET_CLASS.equals(target[0]),
                "补丁类的 @Mixin 指向 " + RecipeTransferButtonCompat.TARGET_CLASS + "，实际是 " + target[0]);
        check(Boolean.FALSE.equals(remap[0]),
                "@Mixin remap=false：本工程不生成 refmap，名字必须按运行期原名用");

        final Method init = findMethod(patch, "init");
        check(init != null && Modifier.isPublic(init.getModifiers()) && !Modifier.isAbstract(init.getModifiers())
                        && !Modifier.isStatic(init.getModifiers()) && init.getReturnType() == void.class,
                "补上的是 public 实例方法 init(Container, EntityPlayer)：AE2 用 invokevirtual 调它");

        final Method update = findMethod(patch, "update");
        check(update != null && Modifier.isAbstract(update.getModifiers()) && update.isAnnotationPresent(Shadow.class),
                "update 是 @Shadow 抽象声明：编译期对着 4.32.0，那里还没有这个方法");
    }

    private static void readMixinAnnotation(final byte[] mixinClassBytes, final String[] target,
            final Boolean[] remap) {
        new ClassReader(mixinClassBytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                if (!"Lorg/spongepowered/asm/mixin/Mixin;".equals(descriptor)) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM5) {
                    @Override
                    public AnnotationVisitor visitArray(final String name) {
                        return new AnnotationVisitor(Opcodes.ASM5) {
                            @Override
                            public void visit(final String ignored, final Object value) {
                                if (value instanceof Type && target[0] == null) {
                                    target[0] = ((Type) value).getInternalName();
                                }
                            }
                        };
                    }

                    @Override
                    public void visit(final String name, final Object value) {
                        if ("remap".equals(name) && value instanceof Boolean) {
                            remap[0] = (Boolean) value;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static void checkRegistered() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                HeiCompatSelfCheck.class.getClassLoader().getResourceAsStream("mixins.ae2us.jei.json"),
                StandardCharsets.UTF_8)) {
            final JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            boolean found = false;
            for (final JsonElement element : json.getAsJsonArray("client")) {
                if (MIXIN_NAME.equals(element.getAsString())) {
                    found = true;
                }
            }
            check(found, "补丁注册在 mixins.ae2us.jei.json 的 client 段（漏注册等于没修）");
        }
    }

    /**
     * Mixin 独占 mixins.ae2us*.json 里声明的包（com.extrarawstyle.ae2us.mixin.*）：包里除"声明过的
     * mixin（含其内部类）"和插件类以外，其它类一律不允许被普通代码直接引用，否则加载时抛
     * IllegalClassLoadError 直接崩游戏。1.3.5-dev1 就是把判定辅助类放进了这个包，
     * 启动阶段崩在 FeatureMixinPlugin 引用它的时候——这条检查专门防它再犯。
     */
    private static void checkMixinPackageHygiene() throws Exception {
        final Set<String> allowed = new HashSet<String>();
        allowed.add(FeatureMixinPlugin.class.getName());
        for (final String resource : new String[]{"mixins.ae2us.json", "mixins.ae2us.jei.json",
                "mixins.ae2us.ccl.json"}) {
            allowed.addAll(declaredMixins(resource));
        }

        final List<String> found = ownMixinPackageClasses();
        check(!found.isEmpty(), "能枚举到自己的 mixin 包类，说明检查本身没失效");
        for (final String name : found) {
            final int nested = name.indexOf('$');
            final String outer = nested < 0 ? name : name.substring(0, nested);
            check(allowed.contains(outer), name + " 在 mixin 包里却不是声明过的 mixin 或插件类："
                    + "Mixin 独占该包，它一旦被引用就会在启动阶段抛 IllegalClassLoadError");
        }
    }

    private static Set<String> declaredMixins(final String resource) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                HeiCompatSelfCheck.class.getClassLoader().getResourceAsStream(resource), StandardCharsets.UTF_8)) {
            final JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            final String prefix = json.get("package").getAsString();
            final Set<String> names = new HashSet<String>();
            for (final String section : new String[]{"mixins", "client", "server"}) {
                final JsonArray array = json.getAsJsonArray(section);
                if (array == null) {
                    continue;
                }
                for (final JsonElement element : array) {
                    names.add(prefix + "." + element.getAsString());
                }
            }
            return names;
        }
    }

    /** 枚举自己编译出来、位于 mixin 包下的类；源码目录与 jar 两种形态都支持。 */
    private static List<String> ownMixinPackageClasses() throws Exception {
        final Path root = Paths.get(Ae2us.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final String prefix = "com/extrarawstyle/ae2us/mixin/";
        final List<String> names = new ArrayList<String>();
        if (Files.isDirectory(root)) {
            final Path base = root.resolve(prefix);
            if (Files.isDirectory(base)) {
                try (Stream<Path> stream = Files.walk(base)) {
                    for (final Path path : stream.filter(Files::isRegularFile).collect(Collectors.toList())) {
                        final String relative = root.relativize(path).toString().replace('\\', '/');
                        if (relative.endsWith(".class")) {
                            names.add(relative.substring(0, relative.length() - ".class".length()).replace('/', '.'));
                        }
                    }
                }
            }
        } else {
            try (ZipFile zip = new ZipFile(root.toFile())) {
                final Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    final String entry = entries.nextElement().getName();
                    if (entry.startsWith(prefix) && entry.endsWith(".class")) {
                        names.add(entry.substring(0, entry.length() - ".class".length()).replace('/', '.'));
                    }
                }
            }
        }
        return names;
    }

    private static Method findMethod(final Class<?> type, final String name) {
        for (final Method method : type.getDeclaredMethods()) {
            if (name.equals(method.getName()) && method.getParameterCount() == 2
                    && Container.class.getName().equals(method.getParameterTypes()[0].getName())
                    && EntityPlayer.class.getName().equals(method.getParameterTypes()[1].getName())) {
                return method;
            }
        }
        return null;
    }

    private static Path libsDirectory() {
        final String configured = System.getProperty("ae2us.libs");
        final Path directory = configured == null ? Paths.get("libs") : Paths.get(configured);
        check(Files.isDirectory(directory), "libs 目录存在：" + directory.toAbsolutePath());
        return directory;
    }

    private static byte[] readOwnClass() throws IOException {
        try (InputStream in = HeiCompatSelfCheck.class.getClassLoader().getResourceAsStream(MIXIN_ENTRY)) {
            if (in == null) {
                throw new AssertionError("类路径上找不到补丁类：" + MIXIN_ENTRY);
            }
            return readAll(in);
        }
    }

    private static byte[] readEntry(final Path jar, final String entry) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            final ZipEntry found = zip.getEntry(entry);
            if (found == null) {
                throw new AssertionError("样本 jar 里没有 " + entry + "：" + jar);
            }
            try (InputStream in = zip.getInputStream(found)) {
                return readAll(in);
            }
        }
    }

    private static byte[] readAll(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
        final byte[] buffer = new byte[8192];
        int read = in.read(buffer);
        while (read != -1) {
            out.write(buffer, 0, read);
            read = in.read(buffer);
        }
        return out.toByteArray();
    }

    private static void check(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
