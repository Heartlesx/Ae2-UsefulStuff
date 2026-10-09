package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.parallel.ParallelTier;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 高级并行处理单元的离线自检：
 * 1. 档位表本身（数值、命名、颜色、差额）；
 * 2. 资源与语言文件（blockstates / 方块与物品模型 / 中英两个语言文件逐行对应）；
 * 3. AE2 侧结构：我们要继承、覆写、注入的方法都还在且签名未变，注入点唯一 —— 换 AE2 版本先在这里报警；
 * 4. 本模组自己的补丁面：瓦片覆写了哪些方法、mixin 注到哪个方法上。
 * <p>
 * 离线运行：gradlew check（或 gradlew verifyParallelUnit）。只读字节码与资源，不碰 AEApi 单例。
 */
public final class ParallelUnitSelfCheck {
    private static int failed;

    public static void main(final String[] args) throws Exception {
        tierTable();
        resources();
        ae2Structure();
        ourPatchSurface();
        if (failed > 0) {
            System.out.println(failed + " 项未通过");
            System.exit(1);
        }
        System.out.println("全部通过");
    }

    private static void tierTable() {
        final ParallelTier[] tiers = ParallelTier.values();
        final int[] expected = {16, 64, 256, 1024, 2048, 4096};
        check(tiers.length == expected.length, "六档档位表齐全");
        if (tiers.length != expected.length) {
            return;
        }
        final Set<String> names = new HashSet<>();
        final Set<String> colors = new HashSet<>();
        for (int i = 0; i < tiers.length; i++) {
            final ParallelTier tier = tiers[i];
            check(tier.parallels() == expected[i], "第 " + (i + 1) + " 档等效 " + expected[i] + " 个并行单元");
            check(tier.extraParallels() == expected[i] - 1, "第 " + (i + 1) + " 档在集群侧补 N-1");
            check(tier.registryName().equals("parallel_unit_" + expected[i]), "第 " + (i + 1) + " 档注册名带档位");
            check(tier.label().equals(expected[i] + "x"), "第 " + (i + 1) + " 档名字前缀是 " + expected[i] + "x");
            check(tier.colorCode().length() == 2 && tier.colorCode().charAt(0) == '\u00A7',
                    "第 " + (i + 1) + " 档有格式颜色码");
            check(names.add(tier.registryName()), "第 " + (i + 1) + " 档注册名不重复");
            check(colors.add(tier.colorCode()), "第 " + (i + 1) + " 档颜色不重复");
        }
    }

    private static void resources() throws Exception {
        final String[] states = {"unformed", "formed_off", "formed_on"};
        for (final ParallelTier tier : ParallelTier.values()) {
            check(resourceExists("assets/ae2us/blockstates/" + tier.registryName() + ".json"),
                    tier.registryName() + " 有 blockstate");
            check(resourceExists("assets/ae2us/models/item/" + tier.registryName() + ".json"),
                    tier.registryName() + " 有物品模型");
            final String base = "parallel_" + tier.parallels() + "x";
            for (final String state : states) {
                check(resourceExists("assets/ae2us/models/block/" + base + "_" + state + ".json"),
                        base + "_" + state + " 有方块模型");
                check(resourceExists("assets/ae2us/textures/blocks/" + base + "_" + state + ".png"),
                        base + "_" + state + " 有贴图");
            }
        }
        check(resourceExists("assets/ae2us/textures/blocks/connected_neutral.png"), "中性连接纹理随包存在");
        for (final ParallelTier tier : ParallelTier.values()) {
            final String blockstate = resourceText("assets/ae2us/blockstates/" + tier.registryName() + ".json");
            final String base = "ae2us:parallel_" + tier.parallels() + "x";
            check(blockstate.contains(base + "_unformed") && blockstate.contains(base + "_formed_off")
                    && blockstate.contains(base + "_formed_on"),
                    tier.registryName() + " 的 blockstate 挂上了三态模型");
            // 方块带 powered/formed 两个列出的属性：默认状态映射的变体键是"属性=值"，必须逐个列全。
            // （dev3 踩过：只写 normal 键、又没注册自定义状态映射，结果所有状态都找不到模型。）
            for (final String formed : new String[]{"false", "true"}) {
                for (final String powered : new String[]{"false", "true"}) {
                    check(blockstate.contains("formed=" + formed + ",powered=" + powered),
                            tier.registryName() + " 的 blockstate 覆盖 formed=" + formed + ",powered=" + powered);
                }
            }
        }
        check(resourceExists("assets/ae2us/textures/blocks/connected_neutral.png"), "中性连接纹理随包存在");
        final List<String> zh = langKeys("assets/ae2us/lang/zh_cn.lang");
        final List<String> en = langKeys("assets/ae2us/lang/en_us.lang");
        check(zh.equals(en), "中英语言文件逐键对应");
        for (final ParallelTier tier : ParallelTier.values()) {
            final String key = "tile.ae2us." + tier.registryName() + ".name";
            check(zh.contains(key), "zh_cn 有 " + key);
            check(langValue("assets/ae2us/lang/zh_cn.lang", key).contains(tier.label()), "zh_cn 名字带 " + tier.label());
            check(langValue("assets/ae2us/lang/en_us.lang", key).contains(tier.label()), "en_us 名字带 " + tier.label());
        }
        check(zh.contains("ae2us.parallel_unit.tooltip"), "有悬停提示键");
        check(langValue("assets/ae2us/lang/zh_cn.lang", "ae2us.parallel_unit.tooltip").contains("%s"),
                "悬停提示写出并行数占位符");
    }

    private static void ae2Structure() throws Exception {
        final TypeInfo tile = typeInfo("appeng/tile/crafting/TileCraftingTile");
        check(tile.isPublic() && !tile.isFinal() && !tile.isAbstract(), "TileCraftingTile 可被继承");
        check(tile.constructor != null && (tile.constructor & Opcodes.ACC_PUBLIC) != 0, "TileCraftingTile 有无参公开构造");
        checkPublic(tile, "isAccelerator", "()Z", "并行单元判定");
        checkPublic(tile, "isStorage", "()Z", "存储单元判定");
        checkPublic(tile, "getStorageBytes", "()I", "存储字节数");
        checkPublic(tile, "isStatus", "()Z", "监控器判定");
        checkPublic(tile, "isValid", "()Z", "成型的合法成员判定");
        checkPublic(tile, "isFormed", "()Z", "成型判定（光晕用）");
        checkPublic(tile, "isActive", "()Z", "通电判定（光晕用）");
        // notLoaded 声明在 AE2 的瓦片基类上，由 TileCraftingTile 继承。
        checkPublic(typeInfo("appeng/tile/AEBaseTile"), "notLoaded", "()Z", "加载判定（读档位用）");

        final TypeInfo block = typeInfo("appeng/block/crafting/BlockCraftingUnit");
        check(block.isPublic() && !block.isFinal(), "BlockCraftingUnit 可被继承");
        check(block.methods.containsKey("<init>(Lappeng/block/crafting/BlockCraftingUnit$CraftingUnitType;)V"),
                "BlockCraftingUnit 有公开的档位构造");
        check(block.fields.contains("FORMED"), "BlockCraftingUnit.FORMED 存在（继承的状态属性）");

        final TypeInfo unitType = typeInfo("appeng/block/crafting/BlockCraftingUnit$CraftingUnitType");
        check(unitType.fields.contains("ACCELERATOR"), "CraftingUnitType.ACCELERATOR 存在");

        final TypeInfo baseBlock = typeInfo("appeng/block/AEBaseTileBlock");
        checkPublic(baseBlock, "setTileEntity", "(Ljava/lang/Class;)V", "方块绑定瓦片类");

        final TypeInfo cluster = typeInfo("appeng/me/cluster/implementations/CraftingCPUCluster");
        check(cluster.methods.containsKey("addTile(Lappeng/tile/crafting/TileCraftingTile;)V"), "CraftingCPUCluster.addTile 存在");
        check(cluster.methods.containsKey("getCoProcessors()I"), "CraftingCPUCluster.getCoProcessors 存在");
        check(acceleratorWritesInAddTile() == 1, "addTile 里对 accelerator 的写入点唯一（注入位置稳定）");
    }

    private static void ourPatchSurface() throws Exception {
        final TypeInfo tile = typeInfo("com/extrarawstyle/ae2us/parallel/TileParallelUnit");
        check("appeng/tile/crafting/TileCraftingTile".equals(tile.superName), "瓦片继承 AE2 合成单元瓦片");
        check(tile.interfaces != null && java.util.Arrays.asList(tile.interfaces)
                .contains("com/extrarawstyle/ae2us/parallel/IParallelCraftingUnit"), "瓦片实现并行数接口");
        check(tile.methods.containsKey("isAccelerator()Z"), "瓦片覆写了 isAccelerator");
        check(tile.methods.containsKey("extraParallels()I"), "瓦片实现了 extraParallels");

        final TypeInfo block = typeInfo("com/extrarawstyle/ae2us/parallel/BlockParallelUnit");
        check("appeng/block/crafting/BlockCraftingUnit".equals(block.superName), "方块继承 AE2 并行单元方块");

        final TypeInfo mixin = typeInfo("com/extrarawstyle/ae2us/mixin/CraftingCPUClusterMixin");
        check(mixin.annotations.contains("Lorg/spongepowered/asm/mixin/Mixin;"), "mixin 声明了 @Mixin");
        check(mixin.fieldAnnotations.getOrDefault("accelerator", java.util.Collections.emptySet())
                .contains("Lorg/spongepowered/asm/mixin/Shadow;"), "mixin 影射 accelerator 字段");
        boolean injected = false;
        for (final Set<String> annotations : mixin.methodAnnotations.values()) {
            if (annotations.contains("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
                injected = true;
            }
        }
        check(injected, "mixin 声明了 @Inject");
        check(injectedIntoAddTile(), "mixin 注入点是 addTile");
    }

    /** 读 mixin 的 @Inject 注解值：方法名是 addTile，@At 指向 accelerator 字段。 */
    private static boolean injectedIntoAddTile() throws Exception {
        final Set<String> values = new HashSet<>();
        try (InputStream in = open("com/extrarawstyle/ae2us/mixin/CraftingCPUClusterMixin.class")) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String desc,
                        final String signature, final String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public AnnotationVisitor visitAnnotation(final String annotationDesc, final boolean visible) {
                            return "Lorg/spongepowered/asm/mixin/injection/Inject;".equals(annotationDesc)
                                    ? new AnnotationValues(values) : null;
                        }
                    };
                }
            }, 0);
        }
        boolean target = false;
        for (final String value : values) {
            if (value.contains("CraftingCPUCluster;accelerator:I")) {
                target = true;
            }
        }
        return values.contains("addTile") && target;
    }

    /** 把注解树里所有字符串值收集起来（@Inject 的 method 是数组值，得递归取）。 */
    private static final class AnnotationValues extends AnnotationVisitor {
        private final Set<String> values;

        AnnotationValues(final Set<String> values) {
            super(Opcodes.ASM5);
            this.values = values;
        }

        @Override
        public void visit(final String name, final Object value) {
            if (value != null) {
                this.values.add(String.valueOf(value));
            }
        }

        @Override
        public AnnotationVisitor visitArray(final String name) {
            return new AnnotationValues(this.values);
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String name, final String desc) {
            return new AnnotationValues(this.values);
        }
    }

    private static int acceleratorWritesInAddTile() throws Exception {
        final int[] count = {0};
        try (InputStream in = open("appeng/me/cluster/implementations/CraftingCPUCluster.class")) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String desc,
                        final String signature, final String[] exceptions) {
                    final boolean target = "addTile".equals(name)
                            && "(Lappeng/tile/crafting/TileCraftingTile;)V".equals(desc);
                    if (!target) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitFieldInsn(final int opcode, final String owner, final String fieldName,
                                final String fieldDesc) {
                            if (opcode == Opcodes.PUTFIELD && "accelerator".equals(fieldName)) {
                                count[0]++;
                            }
                        }
                    };
                }
            }, 0);
        }
        return count[0];
    }

    private static final class TypeInfo {
        private int access;
        private String superName;
        private String[] interfaces;
        private Integer constructor;
        private final Map<String, Integer> methods = new HashMap<>();
        private final Set<String> fields = new HashSet<>();
        private final Set<String> annotations = new HashSet<>();
        private final Map<String, Set<String>> methodAnnotations = new HashMap<>();
        private final Map<String, Set<String>> fieldAnnotations = new HashMap<>();

        private boolean isPublic() {
            return (this.access & Opcodes.ACC_PUBLIC) != 0;
        }

        private boolean isFinal() {
            return (this.access & Opcodes.ACC_FINAL) != 0;
        }

        private boolean isAbstract() {
            return (this.access & Opcodes.ACC_ABSTRACT) != 0;
        }
    }

    private static TypeInfo typeInfo(final String internalName) throws Exception {
        final TypeInfo info = new TypeInfo();
        try (InputStream in = open(internalName + ".class")) {
            if (in == null) {
                throw new AssertionError("找不到类资源：" + internalName);
            }
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public void visit(final int version, final int access, final String name, final String signature,
                        final String superName, final String[] interfaces) {
                    info.access = access;
                    info.superName = superName;
                    info.interfaces = interfaces;
                }

                @Override
                public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                    info.annotations.add(desc);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String desc,
                        final String signature, final String[] exceptions) {
                    info.methods.put(name + desc, access);
                    if ("<init>".equals(name)) {
                        info.constructor = info.constructor == null || (access & Opcodes.ACC_PUBLIC) != 0
                                ? access : info.constructor;
                    }
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public AnnotationVisitor visitAnnotation(final String annotationDesc, final boolean visible) {
                            info.methodAnnotations.computeIfAbsent(name + desc, key -> new HashSet<>())
                                    .add(annotationDesc);
                            return null;
                        }
                    };
                }

                @Override
                public FieldVisitor visitField(final int access, final String name, final String desc,
                        final String signature, final Object value) {
                    info.fields.add(name);
                    return new FieldVisitor(Opcodes.ASM5) {
                        @Override
                        public AnnotationVisitor visitAnnotation(final String annotationDesc, final boolean visible) {
                            info.fieldAnnotations.computeIfAbsent(name, key -> new HashSet<>())
                                    .add(annotationDesc);
                            return null;
                        }
                    };
                }
            }, 0);
        }
        return info;
    }

    private static void checkPublic(final TypeInfo info, final String name, final String desc, final String what) {
        final Integer access = info.methods.get(name + desc);
        check(access != null && (access & Opcodes.ACC_PUBLIC) != 0 && (access & Opcodes.ACC_FINAL) == 0,
                what + "（" + name + desc + "）是公开可覆写方法");
    }

    private static boolean resourceExists(final String path) {
        try (InputStream in = open(path)) {
            return in != null;
        } catch (final Exception e) {
            return false;
        }
    }

    private static String resourceText(final String path) throws Exception {
        final InputStream in = open(path);
        if (in == null) {
            return "";
        }
        final StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }
        return text.toString();
    }

    private static List<String> langKeys(final String path) throws Exception {
        final List<String> keys = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                keys.add(line.substring(0, line.indexOf('=')));
            }
        }
        return keys;
    }

    private static String langValue(final String path, final String key) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(key + "=")) {
                    return line.substring(key.length() + 1);
                }
            }
        }
        return "";
    }

    private static InputStream open(final String path) {
        return ParallelUnitSelfCheck.class.getClassLoader().getResourceAsStream(path);
    }

    private static void check(final boolean condition, final String description) {
        if (condition) {
            System.out.println("OK   " + description);
        } else {
            failed++;
            System.out.println("FAIL " + description);
        }
    }

    private ParallelUnitSelfCheck() {
    }
}
