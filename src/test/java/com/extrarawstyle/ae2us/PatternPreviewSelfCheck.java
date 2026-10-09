package com.extrarawstyle.ae2us;

import appeng.items.misc.ItemEncodedPattern;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * shift 预览修复的结构校验：
 * 1. mixin 注解字符串必须是运行时名（本工程不生成 refmap，写错就注入不上）；
 * 2. 原版 renderItemModelIntoGUI / 两参 renderItem 与 AE2 的 getOutput 签名都在（换版本先在这里报警）；
 * 3. mixin 已在对应 mixin 配置注册（漏注册 = 功能静默失效；CCL 那个必须挂在晚期配置
 *    mixins.ae2us.ccl.json 里，早期配置会毒化它的目标类，见下）。
 * CCL 的接管点不是本模组依赖，只能校验注解字符串与注册。
 */
public final class PatternPreviewSelfCheck {
    private static final String MIXIN_CLASS =
            "com/extrarawstyle/ae2us/mixin/client/RenderItemPatternPreviewMixin.class";
    private static final String CCL_MIXIN_CLASS =
            "com/extrarawstyle/ae2us/mixin/client/CCRenderItemPatternPreviewMixin.class";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String EXPECTED_GUI_TARGET =
            "func_191962_a(Lnet/minecraft/item/ItemStack;IILnet/minecraft/client/renderer/block/model/IBakedModel;)V";
    private static final String EXPECTED_ITEM_TARGET =
            "func_180454_a(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V";
    private static final String EXPECTED_CCL_TARGET =
            "Lcodechicken/lib/render/item/IItemRenderer;renderItem(Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V";

    public static void main(final String[] args) throws Exception {
        final Set<String> injects = annotationStrings(MIXIN_CLASS, INJECT);
        check(injects.contains(EXPECTED_GUI_TARGET),
                "mixin injects renderItemModelIntoGUI by its runtime (SRG) name");
        check(injects.contains(EXPECTED_ITEM_TARGET),
                "mixin injects two-arg renderItem by its runtime (SRG) name");
        check(annotationStrings(CCL_MIXIN_CLASS, MODIFY_ARG).contains(EXPECTED_CCL_TARGET),
                "CCL mixin targets IItemRenderer.renderItem by string");

        final Method render = RenderItem.class.getDeclaredMethod("renderItemModelIntoGUI",
                ItemStack.class, int.class, int.class, IBakedModel.class);
        check(Modifier.isProtected(render.getModifiers()), "RenderItem.renderItemModelIntoGUI is still protected");
        check(render.getReturnType() == void.class, "RenderItem.renderItemModelIntoGUI still returns void");
        RenderItem.class.getDeclaredMethod("renderItem", ItemStack.class, IBakedModel.class);

        final Method output = ItemEncodedPattern.class.getMethod("getOutput", ItemStack.class);
        check(output.getReturnType() == ItemStack.class, "ItemEncodedPattern.getOutput still returns ItemStack");

        check(registered("mixins.ae2us.json", "client.RenderItemPatternPreviewMixin"),
                "RenderItemPatternPreviewMixin is registered in mixins.ae2us.json");
        // CCL 不是 coremod：它的目标类在早期配置里读不到，Mixin 会把这次读取失败缓存进
        // LaunchClassLoader 的 negativeResourceCache，之后 CCL 自己加载该类时拿到缓存的 null
        // 直接崩（dev15/dev16 两次启动崩溃的根因）。必须走晚期加载器配置，且不得留在早期配置。
        check(registered("mixins.ae2us.ccl.json", "client.CCRenderItemPatternPreviewMixin"),
                "CCRenderItemPatternPreviewMixin is registered in mixins.ae2us.ccl.json");
        check(!registered("mixins.ae2us.json", "client.CCRenderItemPatternPreviewMixin"),
                "CCRenderItemPatternPreviewMixin is not in mixins.ae2us.json (early config poisons the class)");
        check(new CclMixinLoader().getMixinConfigs().contains("mixins.ae2us.ccl.json"),
                "CclMixinLoader registers mixins.ae2us.ccl.json");
        System.out.println("Pattern preview checks passed (inject points, target signatures, registration)");
    }

    /** 收集某 mixin 类上指定注入注解里的全部字符串值（含嵌套的 @At.target）。 */
    private static Set<String> annotationStrings(final String className, final String annotationDesc) throws Exception {
        final Set<String> values = new HashSet<>();
        try (InputStream in = PatternPreviewSelfCheck.class.getClassLoader().getResourceAsStream(className)) {
            if (in == null) {
                throw new AssertionError("class not found on classpath: " + className);
            }
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String desc,
                        final String signature, final String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public AnnotationVisitor visitAnnotation(final String seen, final boolean visible) {
                            return annotationDesc.equals(seen) ? new AnnotationValues(values) : null;
                        }
                    };
                }
            }, 0);
        }
        return values;
    }

    private static boolean registered(final String configName, final String mixinName) throws Exception {
        try (InputStream in = PatternPreviewSelfCheck.class.getClassLoader()
                .getResourceAsStream(configName)) {
            if (in == null) {
                throw new AssertionError(configName + " not found on classpath");
            }
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            final byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8).contains("\"" + mixinName + "\"");
        }
    }

    private static final class AnnotationValues extends AnnotationVisitor {
        private final Set<String> values;

        private AnnotationValues(final Set<String> values) {
            super(Opcodes.ASM5);
            this.values = values;
        }

        @Override
        public void visit(final String name, final Object value) {
            if (value instanceof String) {
                values.add((String) value);
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
