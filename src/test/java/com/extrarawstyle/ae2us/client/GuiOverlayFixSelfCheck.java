package com.extrarawstyle.ae2us.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 两个 GUI 修复的离线自检（1.3.9-dev2）：
 *
 * <p>一、HEI 点击穿透守卫。HEI 4.35 的 InputHandler.onGuiMouseEvent 是 receiveCanceled = true 的
 * HIGHEST 订阅者——上传 / 定位覆盖层在 MouseInputEvent.Pre 里的 setCanceled 拦不住它，
 * 点定位按钮时若按钮底下是 HEI 物品，会顺带打开该物品的配方查询。补丁在 handleMouseEvent
 * 入口对覆盖层所在屏幕直接返回 false。这里锁死前提事实、补丁结构与配置注册；HEI 换版本改机制先在这里红。</p>
 *
 * <p>二、矿典存储总线"选择矿典名称"对话框。整屏压暗（0xcc202020）必须保持移除；
 * 对话框绘制前必须显式 RenderHelper.disableStandardItemLighting()——vanilla 在
 * GuiContainer.drawScreen 帧尾会为光标上的物品重新打开标准光照且不再关闭，连到对话框的
 * drawScreen 时仍是开的，会把面板/复选框/按钮整体乘暗（0xd6 渲染成 0x6d，新旧截图像素实锤）；
 * 标记物品图标必须走 AEBaseGui.drawItem（GUI 标准光照 + 白颜色），不许退回手搓
 * renderItemAndEffectIntoGUI——后者继承绘制前的遗留 GL 颜色与光照状态，图标会比槽位物品
 * 暗一截且随场景时暗时亮（用户实机上报）。</p>
 */
public final class GuiOverlayFixSelfCheck {

    private static final String HEI_JAR = "HadEnoughItems_1.12.2-4.35.0.jar";
    private static final String INPUT_HANDLER_ENTRY = "mezz/jei/input/InputHandler.class";
    private static final String MIXIN_ENTRY =
            "com/extrarawstyle/ae2us/mixin/client/InputHandlerMixin.class";
    private static final String MIXIN_NAME = "client.InputHandlerMixin";
    private static final String MIXIN_TARGET = "mezz/jei/input/InputHandler";
    private static final String BUS_ENTRY =
            "com/extrarawstyle/ae2us/client/GuiOreExportBus.class";
    private static final String SUBSCRIBE = "Lnet/minecraftforge/fml/common/eventhandler/SubscribeEvent;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final int DIM_COLOR = (int) 0xcc202020L;

    public static void main(final String[] args) throws Exception {
        final byte[] handler = readEntry(libsDirectory().resolve(HEI_JAR), INPUT_HANDLER_ENTRY);

        // 前提事实：4.35 的鼠标入口最先执行、且对已取消事件照常处理——只靠事件取消拦不住它。
        check("HIGHEST".equals(enumName(methodAnnotationValue(handler, "onGuiMouseEvent", SUBSCRIBE, "priority"))),
                "HEI 4.35 onGuiMouseEvent 优先级为 HIGHEST");
        check(Boolean.TRUE.equals(methodAnnotationValue(handler, "onGuiMouseEvent", SUBSCRIBE, "receiveCanceled")),
                "HEI 4.35 onGuiMouseEvent 带 receiveCanceled=true（我们的 setCanceled 拦不住它的前提）");
        check(hasMethod(handler, "handleMouseEvent", "(Lnet/minecraft/client/gui/GuiScreen;II)Z"),
                "HEI 4.35 InputHandler.handleMouseEvent 注入口存在");

        final byte[] mixin = readOwnClass(MIXIN_ENTRY);
        check(MIXIN_TARGET.equals(mixinTarget(mixin)), "补丁 @Mixin 指向 " + MIXIN_TARGET);
        check(Boolean.FALSE.equals(mixinRemap(mixin)), "补丁 @Mixin remap=false（本工程不生成 refmap）");
        check("handleMouseEvent".equals(methodAnnotationValue(mixin, null, INJECT, "method")),
                "补丁 @Inject 目标是 handleMouseEvent（入口而不是点击判定，滚轮与拖拽一并挡掉）");
        check("HEAD".equals(nestedAnnotationValue(mixin, INJECT, "at", "value")),
                "补丁 @Inject 在 HEAD 执行（先于 HEI 自己的任何处理）");
        check(Boolean.TRUE.equals(methodAnnotationValue(mixin, null, INJECT, "cancellable")),
                "补丁 @Inject 带 cancellable，能改写返回值");
        check(calls(mixin, "com/extrarawstyle/ae2us/client/PatternUploadClient", "blocksHeiMouseInput", null),
                "补丁的守卫条件取自 PatternUploadClient.blocksHeiMouseInput");

        checkRegistered();

        final byte[] bus = readOwnClass(BUS_ENTRY);
        check(!hasIntConstant(bus, DIM_COLOR),
                "标签选择页不再整屏压暗（0xcc202020 必须保持移除）");
        check(callsIn(bus, "drawScreen", "disableStandardItemLighting", "()V"),
                "对话框绘制前显式关掉标准光照（帧尾光照是开的，不关会把对话框整体乘暗）");
        check(callsIn(bus, "drawScreen", "drawItem", "(IILnet/minecraft/item/ItemStack;)V"),
                "对话框标题的物品图标也走 AEBaseGui.drawItem（绘制时自带 GUI 光照）");
        check(!calls(bus, "net/minecraft/client/renderer/RenderItem", "renderItemAndEffectIntoGUI", null),
                "总线 GUI 不再手搓物品绘制（遗留 GL 颜色会把图标压暗）");
        check(callsIn(bus, "drawFG", "drawItem", "(IILnet/minecraft/item/ItemStack;)V"),
                "标记物品图标走 AEBaseGui.drawItem（GUI 标准光照 + 白颜色）");

        System.out.println("GUI overlay fix checks passed "
                + "(HEI mouse guard wiring, ore bus dialog rendering)");
    }

    private static void checkRegistered() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                GuiOverlayFixSelfCheck.class.getClassLoader().getResourceAsStream("mixins.ae2us.jei.json"),
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

    /** 取"某个带指定注解的方法"上的注解标量值（数组形态取第一个元素，如 @Inject.method 是 String[]）。 */
    private static Object methodAnnotationValue(final byte[] cls, final String methodName,
            final String annotationDesc, final String key) {
        final Object[] result = {null};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                    final String signature, final String[] exceptions) {
                if (methodName != null && !methodName.equals(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                        if (!annotationDesc.equals(desc)) {
                            return null;
                        }
                        return new AnnotationVisitor(Opcodes.ASM5) {
                            @Override
                            public void visit(final String annotationKey, final Object value) {
                                if (key.equals(annotationKey)) {
                                    result[0] = value;
                                }
                            }

                            @Override
                            public void visitEnum(final String annotationKey, final String descriptor,
                                    final String value) {
                                if (key.equals(annotationKey)) {
                                    result[0] = new String[]{descriptor, value};
                                }
                            }

                            @Override
                            public AnnotationVisitor visitArray(final String annotationKey) {
                                if (!key.equals(annotationKey)) {
                                    return null;
                                }
                                return new AnnotationVisitor(Opcodes.ASM5) {
                                    @Override
                                    public void visit(final String ignored, final Object value) {
                                        if (result[0] == null) {
                                            result[0] = value;
                                        }
                                    }

                                    @Override
                                    public void visitEnum(final String ignored, final String descriptor,
                                            final String value) {
                                        if (result[0] == null) {
                                            result[0] = new String[]{descriptor, value};
                                        }
                                    }
                                };
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result[0];
    }

    /** 取"注解里数组形态的嵌套注解"的一个字符串字段，如 @Inject.at[] 里第一个 At 的 value（HEAD/RETURN）。 */
    private static String nestedAnnotationValue(final byte[] cls, final String annotationDesc,
            final String nestedKey, final String key) {
        final String[] result = {null};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                    final String signature, final String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                        if (!annotationDesc.equals(desc)) {
                            return null;
                        }
                        return new AnnotationVisitor(Opcodes.ASM5) {
                            @Override
                            public AnnotationVisitor visitArray(final String annotationKey) {
                                if (!nestedKey.equals(annotationKey)) {
                                    return null;
                                }
                                return new AnnotationVisitor(Opcodes.ASM5) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(final String ignored,
                                            final String nestedDesc) {
                                        return new AnnotationVisitor(Opcodes.ASM5) {
                                            @Override
                                            public void visit(final String innerKey, final Object value) {
                                                if (key.equals(innerKey) && value instanceof String) {
                                                    result[0] = (String) value;
                                                }
                                            }
                                        };
                                    }
                                };
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result[0];
    }

    private static String mixinTarget(final byte[] mixin) {
        final String[] target = {null};
        new ClassReader(mixin).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                if (!MIXIN.equals(descriptor)) {
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
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return target[0];
    }

    private static Boolean mixinRemap(final byte[] mixin) {
        final Boolean[] remap = {null};
        new ClassReader(mixin).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                if (!MIXIN.equals(descriptor)) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM5) {
                    @Override
                    public void visit(final String name, final Object value) {
                        if ("remap".equals(name) && value instanceof Boolean) {
                            remap[0] = (Boolean) value;
                        }
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return remap[0];
    }

    private static String enumName(final Object annotationValue) {
        return annotationValue instanceof String[] && ((String[]) annotationValue).length == 2
                ? ((String[]) annotationValue)[1] : null;
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

    private static boolean hasIntConstant(final byte[] cls, final int value) {
        final boolean[] found = {false};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                    final String signature, final String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitLdcInsn(final Object constant) {
                        if (constant instanceof Integer && ((Integer) constant) == value) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return found[0];
    }

    /** 扫全类，看有没有调用 owner.name（owner 非空时须精确匹配；desc 为 null 时只按名字）。 */
    private static boolean calls(final byte[] cls, final String owner, final String name, final String descriptor) {
        return calls(cls, null, owner, name, descriptor);
    }

    private static boolean callsIn(final byte[] cls, final String onlyMethod, final String name,
            final String descriptor) {
        return calls(cls, onlyMethod, null, name, descriptor);
    }

    private static boolean calls(final byte[] cls, final String onlyMethod, final String owner,
            final String name, final String descriptor) {
        final boolean[] found = {false};
        new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String methodName,
                    final String methodDescriptor, final String signature, final String[] exceptions) {
                if (onlyMethod != null && !onlyMethod.equals(methodName)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(final int opcode, final String callOwner,
                            final String callName, final String callDescriptor, final boolean isInterface) {
                        if ((owner == null || owner.equals(callOwner)) && name.equals(callName)
                                && (descriptor == null || descriptor.equals(callDescriptor))) {
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
        try (InputStream in = GuiOverlayFixSelfCheck.class.getClassLoader().getResourceAsStream(entry)) {
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
