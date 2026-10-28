package com.extrarawstyle.ae2us;

import appeng.util.item.AEItemStack;
import com.extrarawstyle.ae2us.maintainer.MaintainerProbe;
import com.extrarawstyle.ae2us.maintainer.MaintainerRule;
import com.extrarawstyle.ae2us.maintainer.MaintainerStatus;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * TheOneProbe 集成的离线自检。
 * <p>
 * TOP 面板本身要游戏环境才画得出来，这里验的是它的"该显示什么"那部分纯逻辑：
 * 摘要条数、明细上限、异常优先。另外锁死两条工程约束：
 * 未装 TOP 时不得触碰 TOP 类（集成类只能经 FMLInterModComms 按类名加载），
 * 以及两个 provider 都注册了。
 */
public final class TopProbeSelfCheck {

    public static void main(final String[] args) throws Exception {
        Bootstrap.register();
        summaryChecks();
        detailOrderChecks();
        problemStatusChecks();
        isolationChecks();

        System.out.println("TOP probe checks passed (summary counts, detail cap with problems first, "
                + "problem statuses, class isolation)");
    }

    // ---------------------------------------------------------------- 摘要

    private static void summaryChecks() {
        // 什么都没配。
        final MaintainerProbe.Summary empty = MaintainerProbe.summarize(new MaintainerRule[32]);
        check(!empty.hasAnything(), "an unconfigured device reports nothing to show");
        check(empty.enabled == 0 && empty.running == 0 && empty.problems == 0, "unconfigured counts are zero");

        // 只有库存充足的行：不算在跑也不算异常。
        final MaintainerRule[] rules = rules();
        configure(rules[0], MaintainerStatus.STOCKED, 5000, 1000);
        final MaintainerProbe.Summary stocked = MaintainerProbe.summarize(rules);
        check(stocked.hasAnything(), "a configured row shows the summary");
        check(stocked.enabled == 1, "the enabled row is counted");
        check(stocked.running == 0 && stocked.problems == 0, "a stocked row is neither running nor a problem");
        check(stocked.details.isEmpty(), "a stocked row adds no detail line");
        check(stocked.hidden == 0, "nothing hidden when there are no detail lines");

        // 停用的行算"已配置"但不计入启用数。
        final MaintainerRule disabled = rules[1];
        configure(disabled, MaintainerStatus.DISABLED, 0, 1000);
        disabled.enabled = false;
        final MaintainerProbe.Summary withDisabled = MaintainerProbe.summarize(rules);
        check(withDisabled.enabled == 1, "a disabled row is not counted as enabled");
    }

    // ---------------------------------------------------------------- 明细上限

    /**
     * 明细最多 3 条，异常优先：异常多到占满时，"正在跑"会被挤掉但统计仍在。
     */
    private static void detailOrderChecks() {
        final MaintainerRule[] rules = rules();
        configure(rules[0], MaintainerStatus.CRAFTING, 100, 1000);
        configure(rules[1], MaintainerStatus.CRAFTING, 200, 1000);
        configure(rules[2], MaintainerStatus.NO_PATTERN, 0, 1000);

        final MaintainerProbe.Summary mixed = MaintainerProbe.summarize(rules);
        check(mixed.running == 2, "two rows are running");
        check(mixed.problems == 1, "one row is a problem");
        check(mixed.details.size() == 3, "three details fit exactly (actual " + mixed.details.size() + ")");
        check(mixed.details.get(0).kind == MaintainerProbe.Line.KIND_PROBLEM,
                "the problem line comes first even though it is the third row");
        check(mixed.hidden == 0, "nothing hidden when details fit");

        // 异常占满 3 条：在跑的明细被挤掉，但统计里仍有。
        final MaintainerRule[] many = rules();
        for (int i = 0; i < 4; i++) {
            configure(many[i], MaintainerStatus.SUBMIT_FAILED, 0, 1000);
        }
        configure(many[5], MaintainerStatus.CRAFTING, 10, 1000);
        final MaintainerProbe.Summary overwhelmed = MaintainerProbe.summarize(many);
        check(overwhelmed.problems == 4, "all four failing rows are counted");
        check(overwhelmed.running == 1, "the running row is still counted in the summary");
        check(overwhelmed.details.size() == MaintainerProbe.DETAIL_MAX,
                "details never exceed the cap (actual " + overwhelmed.details.size() + ")");
        boolean anyRunningDetail = false;
        for (final MaintainerProbe.Line line : overwhelmed.details) {
            if (line.kind == MaintainerProbe.Line.KIND_RUNNING) { anyRunningDetail = true; }
        }
        check(!anyRunningDetail, "problems push running rows out of the detail list");
        check(overwhelmed.hidden == 2, "the leftover count is reported (actual " + overwhelmed.hidden + ")");
    }

    // ---------------------------------------------------------------- 异常判定

    private static void problemStatusChecks() {
        check(MaintainerProbe.isProblem(MaintainerStatus.NO_PATTERN), "missing material is a problem");
        check(MaintainerProbe.isProblem(MaintainerStatus.SUBMIT_FAILED), "submit failure is a problem");
        check(MaintainerProbe.isProblem(MaintainerStatus.ORDER_UNKNOWN), "unknown order is a problem");
        check(MaintainerProbe.isProblem(MaintainerStatus.NO_DISCRETIZER), "missing discretizer is a problem");
        check(!MaintainerProbe.isProblem(MaintainerStatus.STOCKED), "stocked is not a problem");
        check(!MaintainerProbe.isProblem(MaintainerStatus.QUEUED), "queued is not a problem");
        check(!MaintainerProbe.isProblem(MaintainerStatus.CRAFTING), "crafting is not a problem");
        check(MaintainerStatus.holdsOrder(MaintainerStatus.ORDER_UNKNOWN),
                "an unknown order still holds the order");

        // ORDER_UNKNOWN 既是"占着订单"又是"要处理的异常"：明细里必须算异常，不能混进正在跑。
        final MaintainerRule[] unknown = rules();
        configure(unknown[0], MaintainerStatus.ORDER_UNKNOWN, 0, 1000);
        final MaintainerProbe.Summary summary = MaintainerProbe.summarize(unknown);
        check(summary.problems == 1, "an unknown order is counted as a problem");
        check(summary.running == 0, "an unknown order is not counted as running");
        check(summary.details.size() == 1 && summary.details.get(0).kind == MaintainerProbe.Line.KIND_PROBLEM,
                "an unknown order shows as a problem detail line");
    }

    // ---------------------------------------------------------------- 类隔离

    /**
     * 未装 TOP 时必须一座 TOP 类都不碰：两个集成类只能靠 FMLInterModComms 按类名加载，
     * 别的地方（注册入口、TE）不得直接引用它们。
     * 这里核对集成类声明了 registerProvider，且注册入口只出现类名字符串。
     */
    private static void isolationChecks() throws Exception {
        for (final String name : new String[]{
                "com.extrarawstyle.ae2us.maintainer.TopMaintainerIntegration",
                "com.extrarawstyle.ae2us.mirror.TopMirrorIntegration"}) {
            final String resource = "/" + name.replace('.', '/') + ".class";
            try (InputStream stream = TopProbeSelfCheck.class.getResourceAsStream(resource)) {
                check(stream != null, "integration class is on the test classpath: " + name);
                final boolean[] registers = new boolean[1];
                final boolean[] implementsFunction = new boolean[1];
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM5) {
                    @Override
                    public void visit(final int version, final int access, final String className,
                            final String signature, final String superName, final String[] interfaces) {
                        if (signature != null && signature.contains("java/util/function/Function")) {
                            implementsFunction[0] = true;
                        }
                        for (final String itf : interfaces == null ? new String[0] : interfaces) {
                            if ("java/util/function/Function".equals(itf)) { implementsFunction[0] = true; }
                        }
                    }

                    @Override
                    public MethodVisitor visitMethod(final int access, final String methodName,
                            final String descriptor, final String signature, final String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM5) {
                            @Override
                            public void visitMethodInsn(final int opcode, final String owner, final String name2,
                                    final String desc, final boolean itf) {
                                if (name2 != null && name2.equals("registerProvider")) {
                                    registers[0] = true;
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG);
                check(implementsFunction[0], name + " is an ITheOneProbe Function");
                check(registers[0], name + " registers its probe provider");
            }

            // 注册入口只能按类名发 IMC，不能直接 new / 引用集成类（否则未装 TOP 就 NoClassDefFound）。
            final String owner = name.startsWith("com.extrarawstyle.ae2us.maintainer")
                    ? "/com/extrarawstyle/ae2us/maintainer/StockMaintainerRegistration.class"
                    : "/com/extrarawstyle/ae2us/mirror/MirrorRegistration.class";
            try (InputStream stream = TopProbeSelfCheck.class.getResourceAsStream(owner)) {
                check(stream != null, "registration class is on the test classpath: " + owner);
                final boolean[] sendsImc = new boolean[1];
                final boolean[] touchesIntegration = new boolean[1];
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM5) {
                    @Override
                    public MethodVisitor visitMethod(final int access, final String methodName,
                            final String descriptor, final String signature, final String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM5) {
                            @Override
                            public void visitMethodInsn(final int opcode, final String methodOwner,
                                    final String name2, final String desc, final boolean itf) {
                                if ("sendFunctionMessage".equals(name2)) { sendsImc[0] = true; }
                            }

                            @Override
                            public void visitTypeInsn(final int opcode, final String type) {
                                if (type != null && type.contains("TopMaintainerIntegration")
                                        || type != null && type.contains("TopMirrorIntegration")) {
                                    touchesIntegration[0] = true;
                                }
                            }

                            @Override
                            public void visitLdcInsn(final Object value) {
                                // 类名以字符串形式出现是允许的（IMC 就是这么用的）。
                                if (value instanceof String && ((String) value).contains("TopM")) {
                                    sendsImc[0] = true;
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG);
                check(sendsImc[0], owner + " registers the probe through FMLInterModComms");
                check(!touchesIntegration[0], owner + " must not reference the TOP integration class directly");
            }
        }
    }

    // ---------------------------------------------------------------- 工具

    private static MaintainerRule[] rules() {
        final MaintainerRule[] rules = new MaintainerRule[32];
        for (int i = 0; i < rules.length; i++) { rules[i] = new MaintainerRule(); }
        return rules;
    }

    /** 配一条规则：不同下标给不同 NBT，免得被当成同一个目标。 */
    private static void configure(final MaintainerRule rule, final int status, final long observed, final long threshold) {
        final ItemStack stack = new ItemStack(Items.IRON_INGOT);
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("row", System.identityHashCode(rule));
        stack.setTagCompound(tag);
        rule.target = AEItemStack.fromItemStack(stack);
        rule.mode = MaintainerRule.MODE_ITEM;
        rule.threshold = threshold;
        rule.batch = 64;
        rule.enabled = true;
        rule.status = status;
        rule.observed = observed;
        rule.observedValid = true;
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
