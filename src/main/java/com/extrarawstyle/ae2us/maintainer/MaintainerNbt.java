package com.extrarawstyle.ae2us.maintainer;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.extrarawstyle.ae2us.maintainer.fluid.FluidMaintainerBridge;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * 规则的存档格式：TE 的 NBT 与方块掉落物共用同一份结构。
 * 目标按 AE 自己的 writeToNBT 写；流体目标走 AE2UEL 自带的流体堆序列化，
 * 因此未安装 AE2FC 时已保存的流体配置也能照常读回。
 */
public final class MaintainerNbt {
    private MaintainerNbt() { }

    public static NBTTagCompound save(final MaintainerBook book) {
        final NBTTagCompound root = new NBTTagCompound();
        final NBTTagList list = new NBTTagList();
        final MaintainerRule[] rules = book.rules();
        for (int i = 0; i < rules.length; i++) {
            final MaintainerRule rule = rules[i];
            if (rule.target == null) { continue; }
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("slot", i);
            tag.setInteger("mode", rule.mode);
            tag.setLong("threshold", rule.threshold);
            tag.setLong("batch", rule.batch);
            tag.setBoolean("enabled", rule.enabled);
            tag.setBoolean("smartPartBatch", rule.smartPartBatch);
            final NBTTagCompound target = new NBTTagCompound();
            if (rule.mode == MaintainerRule.MODE_FLUID) {
                if (!FluidMaintainerBridge.writeTarget(rule.target, target)) { continue; }
            } else {
                ((IAEItemStack) rule.target).writeToNBT(target);
            }
            tag.setTag("target", target);
            if (rule.craftId != null) {
                tag.setString("craftId", rule.craftId);
                tag.setLong("orderBatch", rule.orderBatch);
                // 见过这单在跑：CPU 完成后从网络消失时，靠它判定"确实跑完了"。
                if (rule.seenRunning) { tag.setBoolean("seenRunning", true); }
                if (rule.hasCpuPos) {
                    tag.setBoolean("hasCpu", true);
                    tag.setInteger("cpuDim", rule.cpuDim);
                    tag.setLong("cpuPos", rule.cpuPos);
                }
            }
            list.appendTag(tag);
        }
        root.setTag("list", list);
        return root;
    }

    /** 先清空再装载；缺失或损坏的条目留空，不影响其他条目。 */
    public static void load(final MaintainerBook book, final NBTTagCompound root) {
        clear(book);
        if (root == null) { return; }
        // 迁移：上一版是方块级开关，存在根节点上。老存档把它下发到每条已有规则，
        // 保持玩家看到的行为不变；新标记的行另有默认值。
        final boolean legacyGlobal = root.getBoolean("smartPartBatch");
        final NBTTagList list = root.getTagList("list", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound tag = list.getCompoundTagAt(i);
            final int slot = tag.getInteger("slot");
            if (slot < 0 || slot >= MaintainerBook.CAPACITY) { continue; }
            final int mode = tag.getInteger("mode") == MaintainerRule.MODE_FLUID
                    ? MaintainerRule.MODE_FLUID : MaintainerRule.MODE_ITEM;
            final IAEStack<?> target = readTarget(tag.getCompoundTag("target"), mode);
            if (target == null) { continue; }
            final MaintainerRule rule = book.rule(slot);
            rule.target = target.setStackSize(1).setCraftable(false).setCountRequestable(0);
            rule.mode = mode;
            rule.threshold = Math.max(0L, tag.getLong("threshold"));
            rule.batch = Math.max(0L, tag.getLong("batch"));
            rule.enabled = tag.getBoolean("enabled");
            // 有 per-row 键就按它；没有就是老存档，继承当时的方块级开关。
            rule.smartPartBatch = tag.hasKey("smartPartBatch")
                    ? tag.getBoolean("smartPartBatch") : legacyGlobal;
            rule.clearOrder();
            final String craftId = tag.getString("craftId");
            if (!craftId.isEmpty()) {
                rule.craftId = craftId;
                rule.orderBatch = Math.max(0L, tag.getLong("orderBatch"));
                rule.seenRunning = tag.getBoolean("seenRunning");
                if (tag.getBoolean("hasCpu")) {
                    rule.hasCpuPos = true;
                    rule.cpuDim = tag.getInteger("cpuDim");
                    rule.cpuPos = tag.getLong("cpuPos");
                }
            }
            rule.observed = 0;
            rule.observedValid = false;
            rule.status = rule.enabled ? MaintainerStatus.QUEUED : MaintainerStatus.DISABLED;
        }
    }

    private static void clear(final MaintainerBook book) {
        for (final MaintainerRule rule : book.rules()) {
            rule.target = null;
            rule.mode = MaintainerRule.MODE_ITEM;
            rule.threshold = 0;
            rule.batch = 0;
            rule.enabled = false;
            rule.clearOrder();
            rule.observed = 0;
            rule.observedValid = false;
            rule.status = MaintainerStatus.EMPTY;
        }
    }

    private static IAEStack<?> readTarget(final NBTTagCompound tag, final int mode) {
        if (tag == null || tag.isEmpty()) { return null; }
        if (mode == MaintainerRule.MODE_FLUID) {
            final IAEFluidStack fluid = FluidMaintainerBridge.readTarget(tag);
            return fluid;
        }
        final IAEItemStack item = AEItemStack.fromNBT(tag);
        return item == null || item.getDefinition().isEmpty() ? null : item;
    }
}
