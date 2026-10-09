package com.extrarawstyle.ae2us.compat;

import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 判断当前 HEI/JEI 的 RecipeTransferButton 要不要补回 init(Container, EntityPlayer)。
 * <p>
 * HEI 4.35.0 把这个方法改名成了 update（方法体逐条指令相同），而 AE2 v0.56.7 的
 * JEIMissingItem.showError 仍按旧名调用，悬停 JEI 传输按钮、AE2 要显示"缺物品"错误时抛
 * NoSuchMethodError 崩溃。补法是给目标类加一个 init 转发到 update——但目标类自己已经
 * 有 init 的旧版 HEI（4.32.0 及更早）再加就会方法冲突，所以必须先判断。
 * <p>
 * 判断读类文件字节码而不是比版本号：版本号只能反映某个具体分支，字节码才是决定调用
 * 能否解析的东西。也不用 Class.forName——那会在正被转换的目标类上触发循环类加载。
 * <p>
 * <b>这个类必须待在 mixin 包之外。</b>Mixin 会独占 mixins.ae2us*.json 里声明的包
 * （com.extrarawstyle.ae2us.mixin.*），包内除"声明过的 mixin 及其内部类"和插件类以外的
 * 类都不允许被普通代码直接引用，否则加载时抛 IllegalClassLoadError 直接崩游戏。
 * 配套的补丁类 {@code mixin.compat.RecipeTransferButtonCompatMixin} 才是声明过的 mixin。
 */
public final class RecipeTransferButtonCompat {

    public static final String TARGET_CLASS = "mezz/jei/gui/recipes/RecipeTransferButton";

    private static final String INIT = "init";
    private static final String UPDATE = "update";
    private static final String DESCRIPTOR =
            "(Lnet/minecraft/inventory/Container;Lnet/minecraft/entity/player/EntityPlayer;)V";

    public enum State {
        /** 目标类自带 init：AE2 的调用能正常解析，不要动它。 */
        NOT_NEEDED,
        /** 目标类没有 init 但有 update：需要补一个 init 转发过去。 */
        NEEDED,
        /** 两个都没有，或读不到字节码：不动它，避免给未知版本硬塞方法。 */
        UNSUPPORTED
    }

    private static volatile State cached;

    private RecipeTransferButtonCompat() {
    }

    /** 当前环境要不要兼容层。判定出来了就缓存；判定不出来时不缓存，留给下次（类加载器就绪后）再试。 */
    public static State state() {
        final State known = cached;
        if (known != null) {
            return known;
        }
        final byte[] bytes = readTargetClass();
        if (bytes == null) {
            return State.UNSUPPORTED;
        }
        final State detected = detect(bytes);
        cached = detected;
        return detected;
    }

    /** 纯字节码判断，自检直接拿 jar 里的类文件调它。 */
    public static State detect(final byte[] classBytes) {
        final boolean[] found = new boolean[2];
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                    final String signature, final String[] exceptions) {
                if (DESCRIPTOR.equals(descriptor)) {
                    if (INIT.equals(name)) {
                        found[0] = true;
                    } else if (UPDATE.equals(name)) {
                        found[1] = true;
                    }
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (found[0]) {
            return State.NOT_NEEDED;
        }
        return found[1] ? State.NEEDED : State.UNSUPPORTED;
    }

    private static byte[] readTargetClass() {
        try (InputStream in = RecipeTransferButtonCompat.class.getClassLoader()
                .getResourceAsStream(TARGET_CLASS + ".class")) {
            if (in == null) {
                LogManager.getLogger("AE2US")
                        .warn("类路径上找不到 {}，HEI 兼容层不生效（AE2 的 JEI 传输按钮仍会崩）", TARGET_CLASS);
                return null;
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
            final byte[] buffer = new byte[8192];
            int read = in.read(buffer);
            while (read != -1) {
                out.write(buffer, 0, read);
                read = in.read(buffer);
            }
            return out.toByteArray();
        } catch (final IOException e) {
            LogManager.getLogger("AE2US").warn("读取 {} 字节码失败，跳过 HEI 兼容层：{}", TARGET_CLASS, e.toString());
            return null;
        }
    }
}
