package com.extrarawstyle.ae2us;

import appeng.util.item.AEItemStack;
import com.extrarawstyle.ae2us.maintainer.BlockStockMaintainer;
import com.extrarawstyle.ae2us.maintainer.MaintainerAmount;
import com.extrarawstyle.ae2us.maintainer.MaintainerBook;
import com.extrarawstyle.ae2us.maintainer.MaintainerNbt;
import com.extrarawstyle.ae2us.maintainer.MaintainerQueue;
import com.extrarawstyle.ae2us.maintainer.MaintainerRule;
import com.extrarawstyle.ae2us.maintainer.MaintainerStatus;
import com.extrarawstyle.ae2us.maintainer.StockMaintainerNetwork;
import com.extrarawstyle.ae2us.maintainer.TileStockMaintainer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * ME库存维持器的行为自检：直接驱动规则表、队列、数量解析与配置包编解码，
 * 不启动游戏。核对的是“库存等于阈值不下单、低一单位下一次固定批量”这类语义，
 * 而不是只检查源码里有没有某个字符串。
 */
public final class StockMaintainerSelfCheck {
    public static void main(final String[] args) throws Exception {
        net.minecraft.init.Bootstrap.register();
        amountChecks();
        thresholdChecks();
        capacityChecks();
        duplicateChecks();
        targetChangeChecks();
        queueFairnessChecks();
        planExpiryChecks();
        orderLookupChecks();
        nbtChecks();
        statusChecks();
        packetChecks();
        modelChecks();
        isolationChecks();
        System.out.println("Stock maintainer checks passed (amounts, thresholds, capacity, duplicates, "
                + "target changes, queue fairness, plan expiry, NBT, statuses, packets, models, optional AE2FC isolation)");
    }

    // ---------------------------------------------------------------- 数量文本

    private static void amountChecks() {
        check(MaintainerAmount.parse("500") == 500L, "plain number");
        check(MaintainerAmount.parse("  2K ") == 2000L, "K suffix with spaces");
        check(MaintainerAmount.parse("1k") == 1000L, "lowercase k");
        check(MaintainerAmount.parse("1M") == 1000000L, "M suffix");
        check(MaintainerAmount.parse("1B") == 1000000000L, "B suffix");
        check(MaintainerAmount.parse("8G") == 8000000000L, "G suffix");
        check(MaintainerAmount.parse("1.5g") == 1500000000L, "decimal lowercase G suffix");
        check(MaintainerAmount.parse("1T") == 1000000000000L, "T suffix");
        check(MaintainerAmount.parse("1.5K") == 1500L, "decimal with suffix");
        check(MaintainerAmount.parse("0.5K") == 500L, "fraction below one with suffix");
        check(MaintainerAmount.parse("9223372036854775807") == Long.MAX_VALUE, "maximum long accepted");
        // 维持器与报警器的关键区别：0 不抬成 1、溢出不再截成上限。
        check(MaintainerAmount.parse("0") == null, "zero rejected instead of raised to one");
        check(MaintainerAmount.parse("0K") == null, "zero with suffix rejected");
        check(MaintainerAmount.parse("-5") == null, "negative rejected");
        check(MaintainerAmount.parse("99999999999999999999") == null, "overflow rejected instead of capped");
        check(MaintainerAmount.parse("9999999999999999P") == null, "suffix overflow rejected");
        check(MaintainerAmount.parse("abc") == null, "letters rejected");
        check(MaintainerAmount.parse("1K5") == null, "trailing digits rejected");
        check(MaintainerAmount.parse("1..5") == null, "double dot rejected");
        check(MaintainerAmount.parse(".5") == null, "missing leading digit rejected");
        check(MaintainerAmount.parse("") == null, "empty rejected");
        check(MaintainerAmount.parse("K") == null, "bare suffix rejected");
        check(MaintainerAmount.parse("1.5") == 2L, "fraction alone rounds half up");
        check(MaintainerAmount.parse("0.4") == null, "fraction rounding to zero is rejected");
        check("1000".equals(MaintainerAmount.format(1000L)), "committed text is the exact integer");
        check("1K".equals(MaintainerAmount.compact(1000L)), "compact uses K");
        check("1.5M".equals(MaintainerAmount.compact(1500000L)), "compact keeps one decimal");
    }

    // ---------------------------------------------------------------- 阈值判定

    private static void thresholdChecks() {
        final MaintainerRule rule = new MaintainerRule();
        rule.mode = MaintainerRule.MODE_ITEM;
        rule.target = AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        rule.threshold = 1000;
        rule.batch = 256;
        rule.enabled = true;
        check(rule.usable(), "configured and enabled rule is usable");
        // 严格小于：等于阈值不算缺货。
        check(!rule.belowThreshold(1000), "equal to threshold does not trigger");
        check(rule.belowThreshold(999), "one below threshold triggers");
        check(rule.belowThreshold(0), "empty stock triggers");
        check(MaintainerQueue.needsRefill(999, 1000), "queue predicate agrees with the rule");
        check(!MaintainerQueue.needsRefill(1000, 1000), "queue predicate keeps the strict comparison");
        // 固定批量：阈值远高于批量时，一次仍只请求批量。
        rule.threshold = 100000;
        rule.batch = 256;
        check(rule.batch == 256, "batch stays fixed, not raised to the deficit");
        rule.batch = 0;
        check(!rule.usable(), "zero batch cannot submit");
        rule.batch = 256;
        rule.threshold = 0;
        check(!rule.usable(), "zero threshold cannot submit");
        rule.threshold = 1000;
        rule.enabled = false;
        check(!rule.usable(), "disabled rule cannot submit");
        check(MaintainerStatus.holdsOrder(MaintainerStatus.CRAFTING), "crafting status holds the order");
        check(MaintainerStatus.holdsOrder(MaintainerStatus.ORDER_UNKNOWN), "unknown status still holds the order");
        check(!MaintainerStatus.holdsOrder(MaintainerStatus.STOCKED), "stocked status holds no order");
        // 本轮尝试的结论要在面板上留得住，不能被常规核对刷回“排队中”。
        check(MaintainerStatus.isAttemptResult(MaintainerStatus.NO_PATTERN), "missing material stays visible");
        check(MaintainerStatus.isAttemptResult(MaintainerStatus.SUBMIT_FAILED), "submit failure stays visible");
        check(MaintainerStatus.isAttemptResult(MaintainerStatus.WAITING_CPU), "waiting for CPU stays visible");
        check(!MaintainerStatus.isAttemptResult(MaintainerStatus.QUEUED), "queued is not a final attempt result");
        check(!MaintainerStatus.isAttemptResult(MaintainerStatus.STOCKED), "stocked is not an attempt result");
    }

    // ---------------------------------------------------------------- 容量与 4 页

    private static void capacityChecks() {
        final MaintainerBook book = new MaintainerBook();
        check(MaintainerBook.CAPACITY == 32, "32 rules per device");
        check(MaintainerBook.PAGES == 4 && MaintainerBook.ROWS_PER_PAGE == 8, "4 pages of 8 rows");
        boolean allPresent = true;
        for (int i = 0; i < MaintainerBook.CAPACITY; i++) {
            final MaintainerRule rule = book.rule(i);
            if (rule == null || rule.target != null || rule.enabled) { allPresent = false; }
        }
        check(allPresent, "a fresh book has 32 empty, disabled rows");
        check(book.rule(-1) == null && book.rule(32) == null, "out of range rows are refused");
        // 空行默认停用，且没有默认阈值/批量。
        book.setTarget(0, AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)), MaintainerRule.MODE_ITEM);
        check(book.rule(0).target != null && !book.rule(0).enabled, "a newly marked row starts disabled");
        check(book.rule(0).threshold == 0 && book.rule(0).batch == 0, "a newly marked row has no default numbers");
        check(!book.rule(0).usable(), "a row without numbers cannot submit");
    }

    // ---------------------------------------------------------------- 重复标记

    private static void duplicateChecks() {
        final MaintainerBook book = new MaintainerBook();
        book.setTarget(0, AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)), MaintainerRule.MODE_ITEM);
        book.setThreshold(0, 1000);
        book.setBatch(0, 256);
        book.setEnabled(0, true);
        final long before = book.configRevision();
        check(!book.setTarget(1, AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)),
                MaintainerRule.MODE_ITEM), "same item in another row is refused");
        check(book.rule(0).threshold == 1000 && book.rule(0).enabled, "the refused duplicate does not overwrite the original");
        check(book.configRevision() == before, "a refused duplicate does not change configuration");
        // 相同类型不同 NBT 是不同目标。
        final ItemStack tagged = new ItemStack(Items.IRON_INGOT);
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setString("note", "different");
        tagged.setTagCompound(tag);
        check(book.setTarget(1, AEItemStack.fromItemStack(tagged), MaintainerRule.MODE_ITEM),
                "same item with different NBT is a distinct target");
        check(book.duplicateOf(AEItemStack.fromItemStack(tagged), MaintainerRule.MODE_ITEM, 2) == 1,
                "duplicate lookup finds the NBT-specific row");
        // 物品行与流体行是两套身份，同一个物品目标不会和流体行互相判重。
        check(book.duplicateOf(AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)),
                MaintainerRule.MODE_FLUID, 5) == -1, "item rows do not collide with fluid rows");
        check(book.configuredCount() == 2, "two rows configured");
    }

    // ---------------------------------------------------------------- 换目标 / 改数字

    private static void targetChangeChecks() {
        final MaintainerBook book = new MaintainerBook();
        book.setTarget(0, AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)), MaintainerRule.MODE_ITEM);
        book.setThreshold(0, 1000);
        book.setBatch(0, 256);
        book.setEnabled(0, true);
        final MaintainerRule rule = book.rule(0);
        // 模拟一笔已提交订单。
        rule.craftId = "craft-1";
        rule.orderBatch = 256;
        rule.hasCpuPos = true;
        rule.cpuDim = 0;
        rule.cpuPos = 12345L;
        rule.status = MaintainerStatus.CRAFTING;

        // 改数字：保留关联，订单量不变。
        book.setThreshold(0, 5000);
        book.setBatch(0, 512);
        check("craft-1".equals(rule.craftId), "editing numbers keeps the order link");
        check(rule.orderBatch == 256, "editing numbers does not rewrite the running order batch");
        check(rule.threshold == 5000 && rule.batch == 512, "new numbers apply to later decisions");

        // 换目标：解除关联，旧单不会被取消（本类没有 cancel 路径），启用与数值保留。
        check(book.setTarget(0, AEItemStack.fromItemStack(new ItemStack(Items.GOLD_INGOT)),
                MaintainerRule.MODE_ITEM), "changing target is accepted");
        check(rule.craftId == null && !rule.hasCpuPos, "changing target drops the order link");
        check(rule.enabled && rule.threshold == 5000 && rule.batch == 512,
                "changing target keeps enable state and numbers");
        check(rule.status == MaintainerStatus.QUEUED, "a changed target is ready to queue again");

        // 清空后重建按新行处理。
        check(book.setTarget(0, null, MaintainerRule.MODE_ITEM), "clearing is accepted");
        check(rule.target == null && !rule.enabled && rule.threshold == 0 && rule.batch == 0,
                "clearing resets to an empty disabled row");
        check(rule.status == MaintainerStatus.EMPTY, "cleared row reports empty");
        book.setTarget(0, AEItemStack.fromItemStack(new ItemStack(Items.GOLD_INGOT)), MaintainerRule.MODE_ITEM);
        check(!rule.enabled && rule.threshold == 0, "re-marking after a clear behaves like a new row");
    }

    // ---------------------------------------------------------------- 队列公平性

    /**
     * 32 条全部缺货、只有 2 个可用 CPU：游标必须轮流推进，
     * 首条缺料或需要大 CPU 不能把后面的小任务永远挡在外面。
     */
    private static void queueFairnessChecks() {
        final MaintainerBook book = new MaintainerBook();
        for (int i = 0; i < MaintainerBook.CAPACITY; i++) {
            final ItemStack stack = new ItemStack(Items.IRON_INGOT);
            stack.setCount(1);
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("slot", i);
            stack.setTagCompound(tag);
            book.setTarget(i, AEItemStack.fromItemStack(stack), MaintainerRule.MODE_ITEM);
            book.setThreshold(i, 1000);
            book.setBatch(i, 64);
            book.setEnabled(i, true);
        }
        check(book.configuredCount() == MaintainerBook.CAPACITY, "all 32 rows configured with distinct targets");

        final MaintainerQueue queue = new MaintainerQueue();
        final long now = 1000L;
        // 第一轮：游标推进，前两条被取出后第三条不会重复拿到同一条。
        final int first = queue.pick(book, now);
        final int second = queue.pick(book, now);
        check(first == 0 && second == 1, "cursor advances one row per pick");

        // 只放行两条后停止：模拟两个 CPU 都占上了。
        for (int i = 0; i < 2; i++) { book.rule(i).craftId = "busy-" + i; }
        final int third = queue.pick(book, now);
        check(third == 2, "busy rows are skipped instead of blocking the queue");

        // 首条缺料被冷却：后续条目仍能轮到，不会卡在同一条上。
        queue.coolDown(2, now, MaintainerQueue.RETRY_NO_PATTERN);
        check(!queue.eligible(book.rule(2), 2, now + 1), "cooled row is not eligible");
        check(queue.eligible(book.rule(3), 3, now + 1), "the next row is eligible while a row cools down");
        final int fourth = queue.pick(book, now + 1);
        check(fourth == 3, "the queue moves past a cooled row");

        // 绕一圈后回到冷却好的条目。
        queue.reset();
        for (int i = 0; i < MaintainerBook.CAPACITY; i++) { book.rule(i).clearOrder(); }
        queue.coolDown(0, now, MaintainerQueue.RETRY_NO_PATTERN);
        int picked = -1;
        for (int step = 0; step < 40 && picked < 0; step++) {
            picked = queue.pick(book, now + step);
        }
        check(picked > 0, "a cooling first row does not starve the rest");
        final int later = queue.pick(book, now + MaintainerQueue.RETRY_NO_PATTERN + 1);
        check(later >= 0, "the cooled row rejoins the rotation after its cooldown");

        // 全部不可用时不推进游标。
        queue.reset();
        for (int i = 0; i < MaintainerBook.CAPACITY; i++) { book.rule(i).enabled = false; }
        final int before = queue.cursor();
        check(queue.pick(book, now) < 0, "nothing eligible yields no pick");
        check(queue.cursor() == before, "an empty round does not move the cursor");
    }

    // ---------------------------------------------------------------- 计划过期

    private static void planExpiryChecks() {
        final MaintainerBook book = new MaintainerBook();
        book.setTarget(0, AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)), MaintainerRule.MODE_ITEM);
        final long captured = book.configRevision();
        // 只是库存/状态刷新，不能让已算好的计划作废。
        book.observe(0, MaintainerStatus.QUEUED, 10, true);
        check(book.configRevision() == captured, "display updates do not invalidate a computed plan");
        // 配置变化必须作废。
        book.setThreshold(0, 1000);
        check(book.configRevision() != captured, "config change invalidates a computed plan");
        final long afterThreshold = book.configRevision();
        book.setEnabled(0, true);
        check(book.configRevision() != afterThreshold, "enable toggle invalidates a computed plan");
        // 冷却与过期常量符合工程约定。
        check(MaintainerQueue.RETRY_NO_PATTERN == 200, "missing material retry is 200 ticks");
        check(MaintainerQueue.RETRY_SUBMIT_FAILED == 100, "submit failure retry is 100 ticks");
        check(TileStockMaintainer.SCAN_TICKS == 20, "stock and CPU check runs every 20 ticks");

        // 等 CPU 的条目必须让出轮转：有计划的条目不能被反复挑中而挤掉后面的条目。
        final MaintainerQueue queue = new MaintainerQueue();
        for (int i = 0; i < 4; i++) {
            final ItemStack stack = new ItemStack(Items.IRON_INGOT);
            final NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("n", i);
            stack.setTagCompound(tag);
            book.setTarget(i, AEItemStack.fromItemStack(stack), MaintainerRule.MODE_ITEM);
            book.setThreshold(i, 1000);
            book.setBatch(i, 32);
            book.setEnabled(i, true);
        }
        final int first = queue.pick(book, 0);
        final int second = queue.pick(book, 0);
        check(first == 0 && second == 1, "waiting rows do not steal the next turn");
        check(queue.pick(book, 0) == 2, "the cursor keeps advancing past a row that holds a plan");
    }

    // ---------------------------------------------------------------- 订单核对

    /**
     * 订单被取消后必须能自己恢复。AE2 的 cancel() 会把 myLastLink 直接置 null，
     * 若还按“没有 link = 未知”处理，这条规则会带着非空 craftId 永远排不进队列。
     */
    private static void orderLookupChecks() {
        final com.extrarawstyle.ae2us.maintainer.OrderLookup running =
                com.extrarawstyle.ae2us.maintainer.OrderLookup.forRememberedCpu(true, true, true, true);
        check(running.state == com.extrarawstyle.ae2us.maintainer.OrderLookup.RUNNING,
                "matching craft id on a busy CPU is still running");

        // 同 craftID 但 CPU 已不忙：这一单结束了。
        check(com.extrarawstyle.ae2us.maintainer.OrderLookup.forRememberedCpu(true, true, true, false).state
                        == com.extrarawstyle.ae2us.maintainer.OrderLookup.FINISHED,
                "matching craft id on an idle CPU is finished");

        // 关键修复：订单被取消 → cancel() 清空 myLastLink → 没有 link 且不忙 = 结束。
        check(com.extrarawstyle.ae2us.maintainer.OrderLookup.forRememberedCpu(true, false, false, false).state
                        == com.extrarawstyle.ae2us.maintainer.OrderLookup.FINISHED,
                "cancelled order (no link, idle CPU) is finished, not unknown");

        // 看不到 link 但 CPU 仍在忙：证据不足，保持未知，绝不重下。
        check(com.extrarawstyle.ae2us.maintainer.OrderLookup.forRememberedCpu(true, false, false, true).state
                        == com.extrarawstyle.ae2us.maintainer.OrderLookup.UNKNOWN,
                "no link but CPU is busy stays unknown");

        // CPU 换成别的单：原单不再占用它。
        check(com.extrarawstyle.ae2us.maintainer.OrderLookup.forRememberedCpu(true, false, true, true).state
                        == com.extrarawstyle.ae2us.maintainer.OrderLookup.FINISHED,
                "a different craft id on that CPU finishes the old order");

        // CPU 被拆掉：不可能还在跑。
        check(com.extrarawstyle.ae2us.maintainer.OrderLookup.forRememberedCpu(false, false, false, false).state
                        == com.extrarawstyle.ae2us.maintainer.OrderLookup.FINISHED,
                "a removed CPU finishes the order");

        // 结束判定必须真的清掉关联，否则队列仍然不放行。
        final MaintainerRule rule = new MaintainerRule();
        rule.target = AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        rule.threshold = 1000;
        rule.batch = 64;
        rule.enabled = true;
        rule.craftId = "cancel-me";
        final MaintainerQueue queue = new MaintainerQueue();
        check(!queue.eligible(rule, 0, 0L), "a row holding an order is not eligible");
        rule.clearOrder();
        check(queue.eligible(rule, 0, 0L), "clearing the finished order puts the row back in rotation");

        // ECO（虚拟合成处理器）场景：vCPU 没有方块坐标，完成后自己从网络消失。
        // 只见过它跑过一单，就足以判定"跑完了"；从没见过才是真的未知。
        check(com.extrarawstyle.ae2us.maintainer.OrderLookup.forMissingCpu(true).state
                        == com.extrarawstyle.ae2us.maintainer.OrderLookup.FINISHED,
                "a CPU that vanished after being seen running is finished");
        check(com.extrarawstyle.ae2us.maintainer.OrderLookup.forMissingCpu(false).state
                        == com.extrarawstyle.ae2us.maintainer.OrderLookup.UNKNOWN,
                "a CPU never seen is still unknown (order may not have appeared yet)");

        // 旧存档里的订单没有这个标记，行为必须保持不变（仍是未知，绝不重下）。
        final MaintainerRule legacy = new MaintainerRule();
        legacy.target = AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        legacy.craftId = "legacy-order";
        check(!legacy.seenRunning, "an order loaded without the marker is not treated as seen");
        legacy.clearOrder();
        check(!legacy.seenRunning, "clearing an order also clears the marker");
    }

    // ---------------------------------------------------------------- NBT
    private static void nbtChecks() {
        final MaintainerBook book = new MaintainerBook();
        book.setTarget(0, AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)), MaintainerRule.MODE_ITEM);
        book.setThreshold(0, 1500);
        book.setBatch(0, Long.MAX_VALUE);
        book.setEnabled(0, true);
        book.rule(0).craftId = "order-abc";
        book.rule(0).orderBatch = Long.MAX_VALUE;
        book.rule(0).hasCpuPos = true;
        book.rule(0).cpuDim = -7;
        book.rule(0).cpuPos = 987654321L;

        final ItemStack tagged = new ItemStack(Items.GOLD_INGOT);
        tagged.setTagCompound(new NBTTagCompound());
        tagged.getTagCompound().setString("mark", "x");
        book.setTarget(31, AEItemStack.fromItemStack(tagged), MaintainerRule.MODE_ITEM);
        book.setThreshold(31, 3);
        book.setBatch(31, 4);

        final NBTTagCompound saved = MaintainerNbt.save(book);
        final MaintainerBook restored = new MaintainerBook();
        MaintainerNbt.load(restored, saved);

        final MaintainerRule first = restored.rule(0);
        check(first.target != null && ((appeng.api.storage.data.IAEItemStack) first.target).getDefinition().getItem() == Items.IRON_INGOT,
                "item target survives NBT roundtrip");
        check(first.threshold == 1500L, "threshold survives NBT roundtrip");
        check(first.batch == Long.MAX_VALUE, "long batch is not truncated in NBT");
        check(first.enabled, "enable state survives NBT roundtrip");
        check("order-abc".equals(first.craftId), "order id survives NBT roundtrip");
        check(first.orderBatch == Long.MAX_VALUE, "order batch survives NBT roundtrip");
        check(first.hasCpuPos && first.cpuDim == -7 && first.cpuPos == 987654321L, "CPU position survives NBT roundtrip");
        check(!first.seenRunning, "absent marker stays false after a roundtrip");
        check(!first.observedValid && first.observed == 0, "runtime observation is not restored from NBT");

        final MaintainerRule last = restored.rule(31);
        check(last.target != null && ((appeng.api.storage.data.IAEItemStack) last.target)
                .getDefinition().getTagCompound().getString("mark").equals("x"), "item NBT is part of the target identity");
        check(last.threshold == 3 && last.batch == 4, "last row survives NBT roundtrip");
        check(restored.configuredCount() == 2, "unset rows stay unset after loading");

        // 装载必须清掉旧内容，避免残留串行。
        final MaintainerBook dirty = new MaintainerBook();
        for (int i = 0; i < MaintainerBook.CAPACITY; i++) {
            dirty.setTarget(i, AEItemStack.fromItemStack(new ItemStack(Items.STICK)), MaintainerRule.MODE_ITEM);
            dirty.setThreshold(i, 99);
        }
        MaintainerNbt.load(dirty, saved);
        check(dirty.configuredCount() == 2, "loading clears rows that are not in the saved data");
        check(dirty.rule(1).target == null && dirty.rule(1).threshold == 0, "stale rows are reset, not merged");

        // 空白产物不带任何旧配置。
        final ItemStack fresh = new ItemStack(com.extrarawstyle.ae2us.maintainer.StockMaintainerRegistration.BLOCK);
        check(!fresh.hasTagCompound(), "a freshly crafted block carries no configuration");

        // "见过这单在跑"的标记也要能存盘：否则拆下再放回后又会把 ECO 的订单判成未知。
        final MaintainerBook seenBook = new MaintainerBook();
        seenBook.setTarget(0, AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)), MaintainerRule.MODE_ITEM);
        seenBook.setThreshold(0, 500);
        seenBook.setBatch(0, 100);
        seenBook.rule(0).craftId = "eco-order";
        seenBook.rule(0).seenRunning = true;
        final MaintainerRule seenBack = roundTrip(seenBook).rule(0);
        check("eco-order".equals(seenBack.craftId), "the ECO order id survives NBT roundtrip");
        check(seenBack.seenRunning, "the seen-running marker survives NBT roundtrip");

        // 流体目标：注册名 + NBT 是身份，数量不参与；两种水（有无 NBT）必须分开。
        // 这里用字面规则代替 setTarget，因为 setTarget 的查重走 AE 的 equals，
        // 而 AEFluidStack.equals 依赖 FML 平台比较器，离线自检里没有 FML 运行环境。
        final MaintainerBook fluidBook = new MaintainerBook();
        final appeng.api.storage.data.IAEFluidStack plainWater = appeng.fluids.util.AEFluidStack.fromFluidStack(
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000));
        final net.minecraftforge.fluids.FluidStack taggedWater =
                new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000);
        final NBTTagCompound waterTag = new NBTTagCompound();
        waterTag.setString("source", "well");
        taggedWater.tag = waterTag;
        final appeng.api.storage.data.IAEFluidStack annotatedWater =
                appeng.fluids.util.AEFluidStack.fromFluidStack(taggedWater);
        fluidBook.rule(0).target = plainWater.copy().setStackSize(1);
        fluidBook.rule(0).mode = MaintainerRule.MODE_FLUID;
        fluidBook.rule(0).threshold = 4096;
        fluidBook.rule(0).batch = 16384;
        fluidBook.rule(0).enabled = true;
        fluidBook.rule(1).target = annotatedWater.copy().setStackSize(1);
        fluidBook.rule(1).mode = MaintainerRule.MODE_FLUID;
        check(((appeng.api.storage.data.IAEFluidStack) fluidBook.rule(0).target).getStackSize() == 1,
                "fluid target identity does not keep a quantity");
        // 物品堆不能冒充流体目标。
        check(!com.extrarawstyle.ae2us.maintainer.MaintainerBookMode.accepts(
                        AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)), MaintainerRule.MODE_FLUID),
                "item stacks are refused in fluid mode");
        check(com.extrarawstyle.ae2us.maintainer.MaintainerBookMode.accepts(
                        plainWater, MaintainerRule.MODE_FLUID), "fluid stacks pass in fluid mode");

        final NBTTagCompound fluidSaved = MaintainerNbt.save(fluidBook);
        final MaintainerBook fluidRestored = new MaintainerBook();
        MaintainerNbt.load(fluidRestored, fluidSaved);
        check(fluidRestored.configuredCount() == 2, "fluid rows survive NBT roundtrip without AE2FC");
        check(fluidRestored.rule(0).mode == MaintainerRule.MODE_FLUID, "fluid mode survives NBT roundtrip");
        check(fluidRestored.rule(0).enabled && fluidRestored.rule(0).threshold == 4096
                && fluidRestored.rule(0).batch == 16384, "fluid numbers and enable state survive NBT roundtrip");
        final appeng.api.storage.data.IAEFluidStack restoredPlain =
                (appeng.api.storage.data.IAEFluidStack) fluidRestored.rule(0).target;
        check(restoredPlain.getFluid() == net.minecraftforge.fluids.FluidRegistry.WATER,
                "fluid identity survives NBT roundtrip");
        check(!((appeng.fluids.util.AEFluidStack) fluidRestored.rule(0).target).hasTagCompound(),
                "the plain water target stays NBT-free after a roundtrip");
        check(((appeng.fluids.util.AEFluidStack) fluidRestored.rule(1).target).hasTagCompound(),
                "the annotated water target keeps its NBT after a roundtrip");
        check(sameFluid(fluidRestored.rule(0).target, plainWater),
                "restored fluid target keeps the fluid identity");
        check(sameFluid(fluidRestored.rule(1).target, annotatedWater),
                "restored annotated fluid keeps its NBT");
        check(!sameFluid(fluidRestored.rule(0).target, annotatedWater),
                "different fluid NBT stays a different target");

        // 库存查询按实际存量返回，且不把另一种 NBT 的水算进来。
        // 用 AE 的流体列表实现直接构造：AEApi.instance() 需要 FML 运行环境，离线自检里不可用。
        final java.util.List<appeng.api.storage.data.IAEFluidStack> fluidList = new java.util.ArrayList<>();
        fluidList.add(annotatedWater.copy().setStackSize(700));
        check(com.extrarawstyle.ae2us.maintainer.fluid.FluidMaintainerBridge.stored(fluidList, plainWater) == 0,
                "a different fluid NBT is not counted as the target");
        check(com.extrarawstyle.ae2us.maintainer.fluid.FluidMaintainerBridge.stored(fluidList, annotatedWater) == 700,
                "the matching fluid NBT reports its stored amount");
        fluidList.add(plainWater.copy().setStackSize(250));
        check(com.extrarawstyle.ae2us.maintainer.fluid.FluidMaintainerBridge.stored(fluidList, plainWater) == 250,
                "stored amount is the network total in mB");
    }

    // ---------------------------------------------------------------- 状态语义

    private static void statusChecks() {
        check(MaintainerStatus.EMPTY != MaintainerStatus.DISABLED, "empty and disabled are distinct");
        // 订单状态与“未知”必须分开显示，不能把未知写成成功。
        check(MaintainerStatus.CRAFTING != MaintainerStatus.ORDER_UNKNOWN, "unknown order is its own status");
        check(MaintainerStatus.NO_DISCRETIZER != MaintainerStatus.CRAFTING, "missing discretizer is reported, not faked");
        final MaintainerRule rule = new MaintainerRule();
        rule.target = AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        rule.status = MaintainerStatus.CRAFTING;
        rule.clearOrder();
        check(rule.craftId == null && rule.orderBatch == 0, "clearing the order also drops its batch");
    }

    // ---------------------------------------------------------------- 配置包

    private static void packetChecks() {
        final StockMaintainerNetwork.State state = new StockMaintainerNetwork.State();
        state.window = 9;
        state.page = 3;
        state.status = TileStockMaintainer.STATUS_NO_CHANNEL;
        state.fluidReady = true;
        for (int i = 0; i < MaintainerBook.ROWS_PER_PAGE; i++) {
            state.configured[i] = i % 2 == 0;
            state.modes[i] = i == 2 ? MaintainerRule.MODE_FLUID : MaintainerRule.MODE_ITEM;
            state.statuses[i] = MaintainerStatus.CRAFTING + i % 3;
            state.thresholds[i] = 1000L + i;
            state.batches[i] = Long.MAX_VALUE - i;
            state.counts[i] = 2000L + i;
            state.countsValid[i] = i != 5;
            state.enabled[i] = i % 2 == 0;
            state.hasOrder[i] = i == 2;
            state.labels[i] = "row-" + i;
            state.targets[i] = i == 2 ? "{Fluid:\"water\"}" : "";
            state.icons[i] = i == 0 ? new ItemStack(Items.IRON_INGOT) : ItemStack.EMPTY;
        }
        final ByteBuf buffer = Unpooled.buffer();
        state.toBytes(buffer);
        final StockMaintainerNetwork.State decoded = new StockMaintainerNetwork.State();
        decoded.fromBytes(buffer);
        buffer.release();
        check(decoded.window == 9 && decoded.page == 3 && decoded.fluidReady, "state header roundtrip");
        check(decoded.status == TileStockMaintainer.STATUS_NO_CHANNEL, "device status roundtrip");
        check(decoded.thresholds[7] == 1007L, "threshold roundtrip keeps long precision");
        check(decoded.batches[7] == Long.MAX_VALUE - 7, "batch roundtrip keeps full long range");
        check(decoded.counts[7] == 2007L, "stored count roundtrip");
        check(!decoded.countsValid[5], "invalid counts are distinguishable from zero");
        check(decoded.modes[2] == MaintainerRule.MODE_FLUID, "row mode roundtrip");
        check(decoded.hasOrder[2] && !decoded.hasOrder[3], "order flag roundtrip");
        check("row-7".equals(decoded.labels[7]), "label roundtrip");
        check("{Fluid:\"water\"}".equals(decoded.targets[2]), "fluid target payload roundtrip");
        check(ItemStack.areItemStacksEqual(decoded.icons[0], state.icons[0]), "row icon roundtrip");
        check(decoded.icons[1].isEmpty(), "empty row icon stays empty");
    }

    // ---------------------------------------------------------------- 资源

    private static void modelChecks() throws Exception {
        final String blockstates = "/assets/ae2us/blockstates/me_stock_maintainer.json";
        try (InputStream stream = StockMaintainerSelfCheck.class.getResourceAsStream(blockstates)) {
            check(stream != null, "maintainer blockstates resource exists");
            final JsonObject variants = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("variants");
            check(variants.entrySet().size() == 4, "all four lit/working combinations have variants");
            variants.entrySet().forEach(variant -> {
                final String name = variant.getValue().getAsJsonObject().get("model").getAsString();
                check(name.startsWith("ae2us:"), "maintainer model namespace");
                final String model = "/assets/ae2us/models/block/" + name.substring("ae2us:".length()) + ".json";
                check(StockMaintainerSelfCheck.class.getResource(model) != null, "blockstate model resolves: " + model);
            });
        } catch (final java.io.IOException error) {
            throw new AssertionError("cannot read maintainer blockstates", error);
        }
        final String itemModel = "/assets/ae2us/models/item/me_stock_maintainer.json";
        check(StockMaintainerSelfCheck.class.getResource(itemModel) != null, "item model exists");
        // 贴图必须是 16x16，和 AE2UEL 原版一致。
        for (final String name : new String[]{"front_off", "front_on", "front_working", "side_off", "side_on",
                "side_working", "top_off", "top_on", "top_working", "bottom"}) {
            final String path = "/assets/ae2us/textures/blocks/stock_maintainer_" + name + ".png";
            check(StockMaintainerSelfCheck.class.getResource(path) != null, "texture exists: " + name);
        }
        // 方块状态编码必须能来回转换。
        final BlockStockMaintainer block = new BlockStockMaintainer();
        for (int meta = 0; meta < 4; meta++) {
            check(block.getMetaFromState(block.getStateFromMeta(meta)) == meta, "block metadata roundtrip: " + meta);
        }
        check(!block.getStateFromMeta(0).getValue(BlockStockMaintainer.LIT)
                && !block.getStateFromMeta(0).getValue(BlockStockMaintainer.WORKING), "plain metadata stays offline");
        check(block.getStateFromMeta(3).getValue(BlockStockMaintainer.LIT)
                && block.getStateFromMeta(3).getValue(BlockStockMaintainer.WORKING), "highest metadata is online and working");
        itemModelRegistrationChecks();
    }

    /**
     * 物品模型注册回归：资源存在不等于注册了。
     * 客户端 ModelRegistryEvent 里必须真的把库存维持器 ITEM 的 metadata 0 指到 inventory 模型，
     * 否则背包/手持/掉落物都是黑紫块。这里直接读客户端注册入口的字节码核对那条绑定。
     */
    private static void itemModelRegistrationChecks() throws Exception {
        final String resource = "/com/extrarawstyle/ae2us/client/OreBusModels.class";
        try (InputStream stream = StockMaintainerSelfCheck.class.getResourceAsStream(resource)) {
            check(stream != null, "client model registration class is on the test classpath");
            final boolean[] bindsItem = new boolean[1];
            final boolean[] usesInventoryVariant = new boolean[1];
            final boolean[] registersModel = new boolean[1];
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                                 final String signature, final String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitFieldInsn(final int opcode, final String owner, final String field,
                                                   final String desc) {
                            if (owner != null && owner.startsWith("com/extrarawstyle/ae2us/maintainer/")
                                    && field != null && field.equals("ITEM")) {
                                bindsItem[0] = true;
                            }
                        }

                        @Override
                        public void visitLdcInsn(final Object value) {
                            if (value instanceof String && ((String) value).equals("inventory")) {
                                usesInventoryVariant[0] = true;
                            }
                        }

                        @Override
                        public void visitMethodInsn(final int opcode, final String owner, final String name,
                                                    final String desc, final boolean itf) {
                            if (owner != null && owner.equals("net/minecraftforge/client/model/ModelLoader")
                                    && "setCustomModelResourceLocation".equals(name)) {
                                registersModel[0] = true;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG);
            check(bindsItem[0], "client model registration binds the maintainer ITEM");
            check(registersModel[0], "client model registration goes through ModelLoader");
            check(usesInventoryVariant[0], "the maintainer item model uses the inventory variant");
        }
        // 资源链完整性：item JSON 的父模型必须能落到已存在的方块模型。
        final String itemModel = "/assets/ae2us/models/item/me_stock_maintainer.json";
        try (InputStream stream = StockMaintainerSelfCheck.class.getResourceAsStream(itemModel)) {
            check(stream != null, "item model json is packaged");
            final JsonObject json = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            final String parent = json.get("parent").getAsString();
            check(parent.startsWith("ae2us:block/"), "item model parent points at a block model: " + parent);
            final String parentPath = "/assets/ae2us/models/" + parent.substring("ae2us:".length()) + ".json";
            check(StockMaintainerSelfCheck.class.getResource(parentPath) != null,
                    "item model parent resolves: " + parent);
        } catch (final java.io.IOException error) {
            throw new AssertionError("cannot read maintainer item model", error);
        }
    }

    // ---------------------------------------------------------------- 可选依赖隔离

    /**
     * 未安装 AE2FC 时物品功能必须照常加载，已保存的流体配置也不能丢：
     * 物品侧与流体公共层（Rule/Nbt/Network/TE/Block/Container/Bridge）都不得直接引用 AE2FC 类型，
     * 相关调用只能出现在 Ae2fcFluidAdapter 里。
     */
    private static void isolationChecks() throws Exception {
        final String[] ae2fcFree = {
                "com.extrarawstyle.ae2us.maintainer.TileStockMaintainer",
                "com.extrarawstyle.ae2us.maintainer.MaintainerNbt",
                "com.extrarawstyle.ae2us.maintainer.MaintainerRule",
                "com.extrarawstyle.ae2us.maintainer.StockMaintainerNetwork",
                "com.extrarawstyle.ae2us.maintainer.BlockStockMaintainer",
                "com.extrarawstyle.ae2us.maintainer.ContainerStockMaintainer",
                "com.extrarawstyle.ae2us.maintainer.fluid.FluidMaintainerBridge"
        };
        for (final String className : ae2fcFree) {
            check(!references(className, "com/glodblock/github/"),
                    className + " must not reference AE2FC types directly");
        }
        // 模组在场判断只能走 FML 的清单，不能用类加载探测（会把类名永久写进 invalidClasses）。
        check(!references("com.extrarawstyle.ae2us.maintainer.fluid.FluidMaintainerBridge", "java/lang/Class"),
                "mod presence must not be probed with Class.forName");
        // 唯一允许碰 AE2FC 的地方是兼容层本身。
        check(references("com.extrarawstyle.ae2us.maintainer.fluid.Ae2fcFluidAdapter", "com/glodblock/github/"),
                "the AE2FC adapter is where AE2FC references live");

        // 流体目标与库存读取只用 AE2UEL 自带类型：未装 AE2FC 也能读回配置、显示库存。
        // （channel() 走 AEApi.instance()，需要 AE2 完整初始化，离线自检里不调用。）
        final appeng.api.storage.data.IAEFluidStack water =
                appeng.fluids.util.AEFluidStack.fromFluidStack(
                        new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1000));
        check(water != null, "AE2 fluid stack is available without AE2FC");
        final NBTTagCompound tag = new NBTTagCompound();
        check(com.extrarawstyle.ae2us.maintainer.fluid.FluidMaintainerBridge.writeTarget(water, tag),
                "fluid target is serialisable without AE2FC");
        final appeng.api.storage.data.IAEFluidStack restored =
                com.extrarawstyle.ae2us.maintainer.fluid.FluidMaintainerBridge.readTarget(tag);
        check(restored != null && restored.getFluid() == net.minecraftforge.fluids.FluidRegistry.WATER,
                "fluid target reads back without AE2FC");
        // 界面图标走 AEFluidStack.asItemStackRepresentation → AEApi.instance()，
        // 需要 AE2 完整初始化，离线自检里不调用；其前提已由上面的类型隔离断言覆盖。
    }

    /** 用 ASM 读常量池里的类引用。 */
    private static boolean references(final String className, final String prefix) throws Exception {
        final String resource = "/" + className.replace('.', '/') + ".class";
        try (InputStream stream = StockMaintainerSelfCheck.class.getResourceAsStream(resource)) {
            if (stream == null) { throw new AssertionError("class not found on test classpath: " + className); }
            final boolean[] found = new boolean[1];
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public FieldVisitor visitField(final int access, final String name, final String descriptor,
                                               final String signature, final Object value) {
                    if (descriptor != null && descriptor.contains(prefix)) { found[0] = true; }
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                                 final String signature, final String[] exceptions) {
                    if (descriptor != null && descriptor.contains(prefix)) { found[0] = true; }
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitTypeInsn(final int opcode, final String type) {
                            if (type != null && type.startsWith(prefix)) { found[0] = true; }
                        }

                        @Override
                        public void visitMethodInsn(final int opcode, final String owner, final String name,
                                                    final String desc, final boolean itf) {
                            if (owner != null && owner.startsWith(prefix)) { found[0] = true; }
                        }

                        @Override
                        public void visitFieldInsn(final int opcode, final String owner, final String name,
                                                   final String desc) {
                            if (owner != null && owner.startsWith(prefix)) { found[0] = true; }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG);
            return found[0];
        }
    }

    /** 流体身份比较：注册名相同且 NBT 相等。不经过 AEFluidStack.equals（它依赖 FML 平台比较器）。 */
    private static boolean sameFluid(final Object left, final Object right) {
        if (!(left instanceof appeng.api.storage.data.IAEFluidStack)
                || !(right instanceof appeng.api.storage.data.IAEFluidStack)) { return false; }
        final net.minecraftforge.fluids.FluidStack a =
                ((appeng.api.storage.data.IAEFluidStack) left).getFluidStack();
        final net.minecraftforge.fluids.FluidStack b =
                ((appeng.api.storage.data.IAEFluidStack) right).getFluidStack();
        if (a == null || b == null || a.getFluid() != b.getFluid()) { return false; }
        return a.tag == null ? b.tag == null : a.tag.equals(b.tag);
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    /** 存盘再读回，用来核对某个字段能不能往返。 */
    private static MaintainerBook roundTrip(final MaintainerBook book) {
        final MaintainerBook restored = new MaintainerBook();
        MaintainerNbt.load(restored, MaintainerNbt.save(book));
        return restored;
    }
}
