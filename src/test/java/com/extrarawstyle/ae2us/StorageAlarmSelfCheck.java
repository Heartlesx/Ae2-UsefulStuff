package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.alarm.AlarmNetwork;
import com.extrarawstyle.ae2us.alarm.BlockStorageAlarm;
import com.extrarawstyle.ae2us.alarm.ThresholdText;
import com.extrarawstyle.ae2us.alarm.TileStorageAlarm;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class StorageAlarmSelfCheck {
    public static void main(final String[] args) {
        net.minecraft.init.Bootstrap.register();
        modelResourceChecks();
        parseChecks();
        transitionChecks();
        nbtChecks();
        slotChecks();
        visualStateChecks();
        packetChecks();
        System.out.println("Storage alarm checks passed (models, threshold text, transitions, slots, config NBT, visual states and packets)");
    }

    private static void modelResourceChecks() {
        final String blockstates = "/assets/ae2us/blockstates/me_storage_alarm.json";
        try (InputStream stream = StorageAlarmSelfCheck.class.getResourceAsStream(blockstates)) {
            check(stream != null, "alarm blockstates resource exists");
            final JsonObject variants = new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("variants");
            check(variants.entrySet().size() == 4, "all visual block states have variants");
            variants.entrySet().forEach(variant -> {
                final String name = variant.getValue().getAsJsonObject().get("model").getAsString();
                check(name.startsWith("ae2us:"), "alarm model namespace");
                final String model = "/assets/ae2us/models/block/" + name.substring("ae2us:".length()) + ".json";
                check(StorageAlarmSelfCheck.class.getResource(model) != null, "blockstate model resolves: " + model);
            });
        } catch (final java.io.IOException error) {
            throw new AssertionError("cannot read alarm blockstates", error);
        }
    }

    private static void parseChecks() {
        check(ThresholdText.parse("500") == 500L, "plain number");
        check(ThresholdText.parse("  2K ") == 2000L, "K suffix with spaces");
        check(ThresholdText.parse("1k") == 1000L, "lowercase k");
        check(ThresholdText.parse("1M") == 1000000L, "M suffix");
        check(ThresholdText.parse("1B") == 1000000000L, "B suffix");
        check(ThresholdText.parse("8G") == 8000000000L, "G suffix");
        check(ThresholdText.parse("1.5g") == 1500000000L, "decimal lowercase G suffix");
        check(ThresholdText.parse("1T") == 1000000000000L, "T suffix");
        check(ThresholdText.parse("8P") == 8000000000000000L, "P suffix");
        check(ThresholdText.parse("1.5K") == 1500L, "decimal with suffix");
        check(ThresholdText.parse("0.5K") == 500L, "fraction below one with suffix");
        check(ThresholdText.parse("0") == 1L, "zero raises to minimum one");
        check(ThresholdText.parse("0K") == 1L, "zero suffix raises to minimum one");
        check(ThresholdText.parse("9223372036854775807") == Long.MAX_VALUE, "maximum long accepted");
        check(ThresholdText.parse("99999999999999999999") == Long.MAX_VALUE, "overflow caps at long maximum");
        check(ThresholdText.parse("abc") == null, "letters rejected");
        check(ThresholdText.parse("1K5") == null, "trailing digits rejected");
        check(ThresholdText.parse("1..5") == null, "double dot rejected");
        check(ThresholdText.parse(".5") == null, "missing leading digit rejected");
        check(ThresholdText.parse("") == null, "empty rejected");
        check(ThresholdText.parse("K") == null, "bare suffix rejected");
        check(ThresholdText.parse("1.5") == 2L, "fraction alone rounds half up");
    }

    private static void transitionChecks() {
        check(TileStorageAlarm.transition(false, 10, 20) == 1, "crossing below triggers alert");
        check(TileStorageAlarm.transition(true, 5, 20) == 0, "still below stays below");
        check(TileStorageAlarm.transition(true, 20, 20) == -1, "reaching threshold exactly recovers");
        check(TileStorageAlarm.transition(false, 30, 20) == 0, "still above stays above");
        check(TileStorageAlarm.transition(false, 19, 20) == 1, "strictly less than alerts");
    }

    private static void nbtChecks() {
        final TileStorageAlarm.Entry[] entries = new TileStorageAlarm.Entry[TileStorageAlarm.CAPACITY];
        for (int i = 0; i < entries.length; i++) { entries[i] = new TileStorageAlarm.Entry(); }
        entries[0].item = new ItemStack(Items.IRON_INGOT);
        entries[0].threshold = 1500;
        entries[63].item = new ItemStack(Items.GOLD_INGOT);
        entries[63].threshold = Long.MAX_VALUE;
        final NBTTagCompound saved = TileStorageAlarm.saveEntries(entries);
        final TileStorageAlarm.Entry[] restored = new TileStorageAlarm.Entry[TileStorageAlarm.CAPACITY];
        for (int i = 0; i < restored.length; i++) { restored[i] = new TileStorageAlarm.Entry(); }
        TileStorageAlarm.loadEntries(restored, saved);
        check(ItemStack.areItemStacksEqual(restored[0].item, entries[0].item) && restored[0].threshold == 1500,
                "first entry survives NBT roundtrip");
        check(ItemStack.areItemStacksEqual(restored[63].item, entries[63].item)
                && restored[63].threshold == Long.MAX_VALUE, "last entry and long threshold survive roundtrip");
        check(restored[1].item.isEmpty() && restored[1].threshold == 1, "unset entries stay empty with minimum threshold");
        final TileStorageAlarm.Entry[] cleared = new TileStorageAlarm.Entry[TileStorageAlarm.CAPACITY];
        for (int i = 0; i < cleared.length; i++) {
            cleared[i] = new TileStorageAlarm.Entry();
            cleared[i].item = new ItemStack(Items.STICK);
            cleared[i].below = true;
        }
        TileStorageAlarm.loadEntries(cleared, saved);
        check(ItemStack.areItemStacksEqual(cleared[0].item, entries[0].item) && !cleared[5].below,
                "loading entries resets stale below flags and stale items");
    }

    private static void slotChecks() {
        final TileStorageAlarm tile = new TileStorageAlarm();
        boolean initialized = true;
        for (int i = 0; i < TileStorageAlarm.CAPACITY; i++) {
            final TileStorageAlarm.Entry entry = tile.entry(i);
            if (entry == null || entry.item == null) { initialized = false; }
        }
        check(initialized, "a freshly placed alarm has all " + TileStorageAlarm.CAPACITY + " slots initialized");
    }

    private static void packetChecks() {
        final AlarmNetwork.State state = new AlarmNetwork.State();
        state.window = 7; state.page = 7; state.status = TileStorageAlarm.STATUS_NO_CHANNEL;
        for (int i = 0; i < 8; i++) {
            state.thresholds[i] = 1000L + i;
            state.counts[i] = 2000L + i;
            state.below[i] = i % 2 == 0;
            state.items[i] = i == 3 ? new ItemStack(Items.IRON_INGOT) : ItemStack.EMPTY;
        }
        final io.netty.buffer.ByteBuf buffer = Unpooled.buffer();
        state.toBytes(buffer);
        check(buffer.readableBytes() < 512, "state packet stays small for one page");
        final AlarmNetwork.State decoded = new AlarmNetwork.State();
        decoded.fromBytes(buffer);
        buffer.release();
        check(decoded.window == 7 && decoded.page == 7 && decoded.status == TileStorageAlarm.STATUS_NO_CHANNEL,
                "state header roundtrip");
        check(decoded.thresholds[7] == 1007L && decoded.counts[7] == 2007L
                && decoded.below[0] && !decoded.below[1]
                && ItemStack.areItemStacksEqual(decoded.items[3], state.items[3])
                && decoded.items[0].isEmpty(), "state rows roundtrip with items, counts, thresholds and below flags");
    }

    private static void visualStateChecks() {
        final BlockStorageAlarm block = new BlockStorageAlarm();
        check(!block.getStateFromMeta(0).getValue(BlockStorageAlarm.LIT)
                && !block.getStateFromMeta(0).getValue(BlockStorageAlarm.ALERT), "old dark metadata remains offline");
        check(block.getStateFromMeta(1).getValue(BlockStorageAlarm.LIT)
                && !block.getStateFromMeta(1).getValue(BlockStorageAlarm.ALERT), "old lit metadata remains normal");
        check(block.getStateFromMeta(3).getValue(BlockStorageAlarm.LIT)
                && block.getStateFromMeta(3).getValue(BlockStorageAlarm.ALERT), "alert metadata roundtrip");
        check(block.getMetaFromState(block.getStateFromMeta(3)) == 3, "alert metadata writes back");
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
