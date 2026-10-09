package com.extrarawstyle.ae2us;

import appeng.api.implementations.items.IStorageCell;
import net.minecraft.item.ItemStack;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 2048M 存储元件（物品 + 流体）的结构校验：
 * 1. 两件物品挂在自研公共基类上，基类实现 AE2 的存储元件接口；
 * 2. 容量 / 类型数 / 耗电常量在基类字节码里（2,147,483,647 / 256 / 31.0）；
 * 3. 注册类带两个注册名；模型 JSON、16x16 贴图、双语语言条目齐全；
 * 4. 类型上限补丁：mixin 文件存在、目标与注入点写对、已注册进早期配置；
 * 5. 上游 AE2UEL 形状没变（AbstractCellInventory 的 &lt;init&gt;/&lt;clinit&gt; 里各 3 处 63 常量、
 *    MAX_ITEM_TYPES 字段仍是 63）——变了先在这里报警，再核对补丁。
 */
public final class StorageCellSelfCheck {
    private static final String STORAGE_PACKAGE = "com/extrarawstyle/ae2us/storage/";
    private static final String ITEM_CLASS = STORAGE_PACKAGE + "StorageCell2048M.class";
    private static final String FLUID_CLASS = STORAGE_PACKAGE + "FluidStorageCell2048M.class";
    private static final String BASE_CLASS = STORAGE_PACKAGE + "AbstractSuperStorageCell.class";
    private static final String REGISTRATION_CLASS = STORAGE_PACKAGE + "StorageCellRegistration.class";
    private static final String MIXIN_CLASS = "com/extrarawstyle/ae2us/mixin/CellTypeLimitMixin.class";
    private static final String UPSTREAM_CLASS = "appeng/me/storage/AbstractCellInventory.class";

    public static void main(final String[] args) throws Exception {
        // 1) 类结构：两件物品 -> 公共基类 -> AE2 接口。
        check(BASE_CLASS_NAME.equals(superName(ITEM_CLASS)), "item cell extends the shared super cell");
        check(BASE_CLASS_NAME.equals(superName(FLUID_CLASS)), "fluid cell extends the shared super cell");
        check(interfaces(BASE_CLASS).contains("appeng/api/implementations/items/IStorageCell"),
                "super cell implements AE2 IStorageCell");
        final Set<Integer> baseInts = intConstants(BASE_CLASS);
        check(baseInts.contains(2147483647), "cell capacity constant is 2,147,483,647 bytes");
        check(baseInts.contains(256), "cell type limit constant is 256");
        check(doubleConstants(BASE_CLASS).contains(31.0), "cell idle drain constant is 31.0");
        check(methodNames(ITEM_CLASS).contains("getChannel") && methodNames(FLUID_CLASS).contains("getChannel"),
                "both cells wire their storage channel");
        check(text(ITEM_CLASS).contains("appeng/api/storage/channels/IItemStorageChannel"),
                "item cell uses the item channel");
        check(text(FLUID_CLASS).contains("appeng/api/storage/channels/IFluidStorageChannel"),
                "fluid cell uses the fluid channel");
        check(text(FLUID_CLASS).contains("appeng/fluids/helper/FluidCellConfig"),
                "fluid cell uses the fluid cell config inventory");

        // 2) 注册：两件物品都被登记、注册名各就各位。
        final String registration = text(REGISTRATION_CLASS);
        check(registration.contains("StorageCell2048M") && registration.contains("FluidStorageCell2048M"),
                "registration instantiates both cell classes");
        check(text(ITEM_CLASS).contains("storage_cell_2048m")
                        && text(FLUID_CLASS).contains("fluid_storage_cell_2048m"),
                "both cells carry their registry names");

        // 3) 资源：模型、贴图、双语名称。
        check(text("assets/ae2us/models/item/storage_cell_2048m.json").contains("ae2us:items/storage_cell_2048m"),
                "item cell model points at its texture");
        check(text("assets/ae2us/models/item/fluid_storage_cell_2048m.json")
                        .contains("ae2us:items/fluid_storage_cell_2048m"),
                "fluid cell model points at its texture");
        check(texture("assets/ae2us/textures/items/storage_cell_2048m.png"), "item cell texture is a 16x16 png");
        check(texture("assets/ae2us/textures/items/fluid_storage_cell_2048m.png"), "fluid cell texture is a 16x16 png");
        final String zh = text("assets/ae2us/lang/zh_cn.lang");
        final String en = text("assets/ae2us/lang/en_us.lang");
        check(langLine(zh, "item.ae2us.storage_cell_2048m.name").contains("2048M")
                        && langLine(zh, "item.ae2us.storage_cell_2048m.name").contains("存储元件"),
                "zh item cell name");
        check(langLine(zh, "item.ae2us.fluid_storage_cell_2048m.name").contains("流体存储元件"),
                "zh fluid cell name");
        check(langLine(en, "item.ae2us.storage_cell_2048m.name").contains("ME Storage Cell"),
                "en item cell name");
        check(langLine(en, "item.ae2us.fluid_storage_cell_2048m.name").contains("ME Fluid Storage Cell"),
                "en fluid cell name");

        // 4) 类型上限补丁：目标、注入点、放开的数值、配置注册。
        final AnnotationValues mixin = annotationValues(MIXIN_CLASS);
        check(mixin.strings.contains("appeng/me/storage/AbstractCellInventory"),
                "type limit mixin targets AbstractCellInventory");
        check(mixin.strings.contains("<init>") && mixin.strings.contains("<clinit>"),
                "type limit mixin patches constructor and static init");
        check(mixin.ints.contains(63), "type limit mixin matches the upstream 63 constant");
        check(intConstants(MIXIN_CLASS).contains(256), "type limit mixin raises the 63 constant to 256");
        check(registered("mixins.ae2us.json", "CellTypeLimitMixin"),
                "CellTypeLimitMixin is registered in mixins.ae2us.json");

        // 5) 上游形状：AE2UEL 换版本先在这里红。
        final Map<String, Integer> upstream = countIntConstants(UPSTREAM_CLASS, 63);
        check(upstream.getOrDefault("<init>", 0) == 3, "AE2UEL constructor still has 3 int-63 constants");
        check(upstream.getOrDefault("<clinit>", 0) == 3, "AE2UEL static init still has 3 int-63 constants");
        final Field maxTypes = Class.forName("appeng.me.storage.AbstractCellInventory")
                .getDeclaredField("MAX_ITEM_TYPES");
        maxTypes.setAccessible(true);
        check(maxTypes.getInt(null) == 63, "AE2UEL MAX_ITEM_TYPES is still 63");
        check(IStorageCell.class.getMethod("getBytes", ItemStack.class).getReturnType() == int.class,
                "IStorageCell.getBytes still returns int");
        check(IStorageCell.class.getMethod("getTotalTypes", ItemStack.class).getReturnType() == int.class,
                "IStorageCell.getTotalTypes still returns int");

        System.out.println("Storage cell checks passed (two cells, limits, resources, mixin, upstream shape)");
    }

    private static final String BASE_CLASS_NAME = "com/extrarawstyle/ae2us/storage/AbstractSuperStorageCell";

    private static byte[] bytes(final String resource) throws Exception {
        try (InputStream in = StorageCellSelfCheck.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new AssertionError("resource not found on classpath: " + resource);
            }
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            final byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static String text(final String resource) throws Exception {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }

    private static String superName(final String resource) throws Exception {
        return new ClassReader(bytes(resource)).getSuperName();
    }

    private static Set<String> interfaces(final String resource) throws Exception {
        return new HashSet<>(Arrays.asList(new ClassReader(bytes(resource)).getInterfaces()));
    }

    private static Set<String> methodNames(final String resource) throws Exception {
        final Set<String> names = new HashSet<>();
        new ClassReader(bytes(resource)).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String desc,
                    final String signature, final String[] exceptions) {
                names.add(name);
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return names;
    }

    private static Set<Integer> intConstants(final String resource) throws Exception {
        final Set<Integer> ints = new HashSet<>();
        collectConstants(resource, ints, null);
        return ints;
    }

    private static Set<Double> doubleConstants(final String resource) throws Exception {
        final Set<Double> doubles = new HashSet<>();
        collectConstants(resource, null, doubles);
        return doubles;
    }

    /** 统计某方法里等于目标值的整型常量出现次数（bipush/sipush/ldc）。 */
    private static Map<String, Integer> countIntConstants(final String resource, final int target) throws Exception {
        final Map<String, Integer> counts = new HashMap<>();
        new ClassReader(bytes(resource)).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String desc,
                    final String signature, final String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitIntInsn(final int opcode, final int operand) {
                        if ((opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) && operand == target) {
                            counts.merge(name, 1, Integer::sum);
                        }
                    }

                    @Override
                    public void visitLdcInsn(final Object value) {
                        if (value instanceof Integer && (Integer) value == target) {
                            counts.merge(name, 1, Integer::sum);
                        }
                    }
                };
            }
        }, 0);
        return counts;
    }

    private static void collectConstants(final String resource, final Set<Integer> ints, final Set<Double> doubles)
            throws Exception {
        new ClassReader(bytes(resource)).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String desc,
                    final String signature, final String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitIntInsn(final int opcode, final int operand) {
                        if (ints != null && (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH)) {
                            ints.add(operand);
                        }
                    }

                    @Override
                    public void visitLdcInsn(final Object value) {
                        if (ints != null && value instanceof Integer) {
                            ints.add((Integer) value);
                        } else if (doubles != null && value instanceof Double) {
                            doubles.add((Double) value);
                        }
                    }
                };
            }
        }, 0);
    }

    /** 收集某个类上所有注解的字符串/类名/整数参数（含方法级）。 */
    private static AnnotationValues annotationValues(final String resource) throws Exception {
        final AnnotationValues values = new AnnotationValues();
        new ClassReader(bytes(resource)).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                return values;
            }

            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String desc,
                    final String signature, final String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public AnnotationVisitor visitAnnotation(final String seen, final boolean visible) {
                        return values;
                    }
                };
            }
        }, ClassReader.SKIP_CODE);
        return values;
    }

    private static boolean texture(final String resource) throws Exception {
        try (InputStream in = StorageCellSelfCheck.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            final BufferedImage image = ImageIO.read(in);
            return image != null && image.getWidth() == 16 && image.getHeight() == 16;
        }
    }

    private static String langLine(final String text, final String key) {
        for (final String line : text.split("\n")) {
            if (line.startsWith(key + "=")) {
                return line.substring(key.length() + 1);
            }
        }
        throw new AssertionError("lang key not found: " + key);
    }

    private static boolean registered(final String configName, final String mixinName) throws Exception {
        return text(configName).contains("\"" + mixinName + "\"");
    }

    private static final class AnnotationValues extends AnnotationVisitor {
        final Set<String> strings = new HashSet<>();
        final Set<Integer> ints = new HashSet<>();

        private AnnotationValues() {
            super(Opcodes.ASM5);
        }

        @Override
        public void visit(final String name, final Object value) {
            if (value instanceof String) {
                this.strings.add((String) value);
            } else if (value instanceof Type) {
                this.strings.add(((Type) value).getInternalName());
            } else if (value instanceof Integer) {
                this.ints.add((Integer) value);
            }
        }

        @Override
        public AnnotationVisitor visitArray(final String name) {
            return this;
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String name, final String desc) {
            return this;
        }
    }

    private static void check(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
