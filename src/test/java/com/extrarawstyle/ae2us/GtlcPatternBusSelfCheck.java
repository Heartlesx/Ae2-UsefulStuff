package com.extrarawstyle.ae2us;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * GTLCore"ME样板输入总线 / 总成"兼容的离线自检（1.3.10-dev1）。
 *
 * <p>两个机器的网格宿主是 GT 的机器壳（MetaTileEntityHolder），真正的接口宿主藏在
 * {@code PartMachineMECraftingPatternInputHatch.getPatternInterfaceHost()} 里，所以上传服务
 * 必须经"holder → MTE → host"这条桥才能发现它们。桥的每一环前提都在这里锁死：
 * 机器类是 final（类名闸门精确匹配就够）、方法签名、宿主确实实现 IInterfaceHost、
 * 36 槽的算式来自 {@code getInstalledUpgrades(PATTERN_EXPANSION)==3}（9*(1+3)=36）、
 * 以及我们的发现代码确实走了这条桥。GTLCore 换版本改机制先在这里红。</p>
 *
 * <p>样本 jar 取自目标整合包 mods 目录，放在工程 libs 下，路径由 build.gradle 用系统属性传进来。</p>
 */
public final class GtlcPatternBusSelfCheck {

    private static final String GTLC_JAR = "gtlitecore-1.0.0.jar";
    private static final String GT_JAR = "gregtech-1.12.2-master-2901.jar";
    private static final String HATCH_ENTRY =
            "gregtechlite/gtlitecore/common/metatileentity/part/PartMachineMECraftingPatternInputHatch.class";
    private static final String HOST_ENTRY =
            "gregtechlite/gtlitecore/common/metatileentity/part/PartMachineMECraftingPatternInputHatch$MECraftingPatternInterfaceHost.class";
    private static final String EXPOSE_ENTRY =
            "gregtechlite/gtlitecore/common/metatileentity/part/PartMachineMECraftingPatternInputHatch$PatternInventoryExpose.class";
    private static final String HOLDER_ENTRY = "gregtech/api/metatileentity/MetaTileEntityHolder.class";
    private static final String SERVICE_ENTRY = "com/extrarawstyle/ae2us/PatternUploadService.class";
    private static final String HATCH_OWNER =
            "gregtechlite/gtlitecore/common/metatileentity/part/PartMachineMECraftingPatternInputHatch";
    private static final String HATCH_NAME =
            "gregtechlite.gtlitecore.common.metatileentity.part.PartMachineMECraftingPatternInputHatch";
    private static final String HOST_DESC =
            "()Lgregtechlite/gtlitecore/common/metatileentity/part/PartMachineMECraftingPatternInputHatch$MECraftingPatternInterfaceHost;";

    public static void main(final String[] args) throws Exception {
        final Path libs = libsDirectory();
        final byte[] hatch = readEntry(libs.resolve(GTLC_JAR), HATCH_ENTRY);
        final byte[] host = readEntry(libs.resolve(GTLC_JAR), HOST_ENTRY);
        final byte[] expose = readEntry(libs.resolve(GTLC_JAR), EXPOSE_ENTRY);
        final byte[] holder = readEntry(libs.resolve(GT_JAR), HOLDER_ENTRY);

        // —— GTLCore 侧的前提事实 ——
        check(isFinalClass(hatch), "样板输入总线/总成机器类是 final（类名闸门精确匹配即可，无需处理子类）");
        check(hasMethod(hatch, "getPatternInterfaceHost", HOST_DESC),
                "机器类有 getPatternInterfaceHost() 可取到接口宿主");
        check(implementsInterface(host, "appeng/helpers/IInterfaceHost"),
                "取到的宿主实现 AE2 的 IInterfaceHost（复用双接口整套上传机制）");
        check(fieldConstant(hatch, "PATTERN_SLOT_COUNT") instanceof Integer
                        && ((Integer) fieldConstant(hatch, "PATTERN_SLOT_COUNT")) == 36,
                "样板槽数常量是 36（上传槽位上限依赖它）");
        check(methodHasOpcode(expose, "getInstalledUpgrades", Opcodes.ICONST_3),
                "getInstalledUpgrades(PATTERN_EXPANSION) 返回 3，9*(1+3)=36 与槽位数吻合");

        // —— GT 侧的前提事实（网格节点机器 = GT 机器壳）——
        check(implementsInterface(holder, "gregtech/api/metatileentity/interfaces/IGregTechTileEntity")
                        && implementsInterface(holder, "appeng/me/helpers/IGridProxyable"),
                "MetaTileEntityHolder 同时是 IGregTechTileEntity 与 AE2 网格宿主（holder → MTE 的桥头）");

        // —— 我们这边的接线 ——
        final byte[] service = readOwnClass(SERVICE_ENTRY);
        check(ldcString(service, HATCH_NAME),
                "上传服务里有机器类名闸门（机器不在场时整段桥不执行）");
        check(calls(service, "gregtech/api/metatileentity/interfaces/IGregTechTileEntity", "getMetaTileEntity",
                        "()Lgregtech/api/metatileentity/MetaTileEntity;"),
                "上传服务经 IGregTechTileEntity.getMetaTileEntity() 取机器本体");
        check(calls(service, HATCH_OWNER, "getPatternInterfaceHost", HOST_DESC),
                "上传服务调用 getPatternInterfaceHost() 拿宿主");
        check(methodHasInstanceOf(service, "customNameOf", "appeng/helpers/ICustomNameObject")
                        && methodHasInstanceOf(service, "applyName", "appeng/helpers/ICustomNameObject"),
                "customNameOf / applyName 都有 ICustomNameObject 的 instanceof 守卫"
                        + "（GTLCore 宿主不实现它，裸转型会 ClassCastException）");
        check(calls(service, "gregtech/api/metatileentity/MetaTileEntityHolder", "setCustomName",
                        "(Ljava/lang/String;)V"),
                "改名写到 GT 机器壳上（setCustomName，空串由 holder 按清除处理）");
        check(calls(service, "net/minecraft/world/IWorldNameable", "hasCustomName", "()Z")
                        && calls(service, "net/minecraft/world/IWorldNameable", "getName", "()Ljava/lang/String;"),
                "自定义名从机器壳读取（IWorldNameable：hasCustomName + getName）");

        System.out.println("GTLCore pattern bus checks passed "
                + "(holder bridge, host contract, 36-slot premise, rename guard)");
    }

    private static boolean isFinalClass(final byte[] cls) {
        final boolean[] found = {false};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public void visit(final int version, final int access, final String name, final String signature,
                    final String superName, final String[] interfaces) {
                found[0] = (access & Opcodes.ACC_FINAL) != 0;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    private static boolean implementsInterface(final byte[] cls, final String iface) {
        final boolean[] found = {false};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public void visit(final int version, final int access, final String name, final String signature,
                    final String superName, final String[] interfaces) {
                found[0] = interfaces != null && Arrays.asList(interfaces).contains(iface);
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    private static boolean hasMethod(final byte[] cls, final String name, final String descriptor) {
        final boolean[] found = {false};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String methodName,
                    final String methodDescriptor, final String signature, final String[] exceptions) {
                if (name.equals(methodName) && descriptor.equals(methodDescriptor)) {
                    found[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    private static Object fieldConstant(final byte[] cls, final String fieldName) {
        final Object[] value = {null};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public FieldVisitor visitField(final int access, final String name, final String descriptor,
                    final String signature, final Object fieldValue) {
                if (fieldName.equals(name)) {
                    value[0] = fieldValue;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return value[0];
    }

    private static boolean methodHasOpcode(final byte[] cls, final String methodName, final int opcode) {
        final boolean[] found = {false};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                    final String signature, final String[] exceptions) {
                if (!methodName.equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitInsn(final int insn) {
                        if (insn == opcode) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return found[0];
    }

    private static boolean methodHasInstanceOf(final byte[] cls, final String methodName, final String type) {
        final boolean[] found = {false};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                    final String signature, final String[] exceptions) {
                if (!methodName.equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitTypeInsn(final int opcode, final String insnType) {
                        if (opcode == Opcodes.INSTANCEOF && type.equals(insnType)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return found[0];
    }

    private static boolean calls(final byte[] cls, final String owner, final String name, final String descriptor) {
        final boolean[] found = {false};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String methodName,
                    final String methodDescriptor, final String signature, final String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(final int opcode, final String callOwner,
                            final String callName, final String callDescriptor, final boolean isInterface) {
                        if (owner.equals(callOwner) && name.equals(callName) && descriptor.equals(callDescriptor)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return found[0];
    }

    private static boolean ldcString(final byte[] cls, final String value) {
        final boolean[] found = {false};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String methodName,
                    final String methodDescriptor, final String signature, final String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitLdcInsn(final Object constant) {
                        if (value.equals(constant)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return found[0];
    }

    private static Path libsDirectory() {
        final String configured = System.getProperty("ae2us.libs");
        final Path directory = configured == null ? Paths.get("libs") : Paths.get(configured);
        check(Files.isDirectory(directory), "libs 目录存在：" + directory.toAbsolutePath());
        return directory;
    }

    private static byte[] readOwnClass(final String entry) throws IOException {
        try (InputStream in = GtlcPatternBusSelfCheck.class.getClassLoader().getResourceAsStream(entry)) {
            if (in == null) {
                throw new AssertionError("类路径上找不到：" + entry);
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
