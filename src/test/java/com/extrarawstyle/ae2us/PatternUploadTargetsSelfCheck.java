package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.PatternUploadService.TargetInfo;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class PatternUploadTargetsSelfCheck {
    private PatternUploadTargetsSelfCheck() {
    }

    public static void main(final String[] args) {
        final String displayName = "\u5206\u5b50\u88c5\u914d\u5ba4";
        final String rawName = "tile.appliedenergistics2.molecular_assembler";
        final TargetInfo assembler = new TargetInfo("name:" + rawName, rawName, "", 3, 6,
                Collections.emptyList());
        final List<TargetInfo> targets = Collections.singletonList(assembler);
        final Function<String, String> localize = name -> name.equals(rawName) ? displayName : name;

        check(targets.stream().noneMatch(target -> target.key.equals("name:" + displayName)),
                "reproduce: Chinese mapping cannot directly match the default translation key");
        check(TargetInfo.findByName(targets, displayName, localize) == assembler,
                "Chinese mapping resolves to the default interface group");
        check(TargetInfo.findByName(targets, rawName, localize) == assembler,
                "mapping bound to the translation key resolves without localization");
        check(TargetInfo.findByName(targets, displayName.substring(0, 2), localize) == null,
                "partial names never match");
        check(TargetInfo.findByName(targets, displayName + "2", localize) == null,
                "different full names never match");
        final TargetInfo other = new TargetInfo("name:other", "other", "", 1, 9,
                Collections.emptyList());
        final Function<String, String> ambiguous = name -> displayName;
        check(TargetInfo.findByName(Arrays.asList(assembler, other), displayName, ambiguous) == null,
                "ambiguous translated names do not select an arbitrary group");
        check(TargetInfo.findByName(Arrays.asList(assembler, other), "other", ambiguous) == other,
                "exact stored name takes priority over translated names");
        renameChecks();
        packetChecks();
        System.out.println("Pattern upload target checks passed (matching, rename names, packet roundtrip)");
    }

    private static void renameChecks() {
        check(PatternUploadService.normalizeName("  HV \u8f93\u51fa\u4ed3  ").equals("HV \u8f93\u51fa\u4ed3"),
                "rename names are trimmed");
        check(PatternUploadService.normalizeName(null).isEmpty(), "null rename clears the custom name");
        check(PatternUploadService.normalizeName("   ").isEmpty(), "blank rename clears the custom name");
        final StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            longName.append('\u540d');
        }
        check(PatternUploadService.normalizeName(longName.toString()).length() == 32,
                "rename names are cut to 32 characters");
        check(PatternUploadService.normalizeName("short").equals("short"), "short rename names stay intact");
    }

    private static void packetChecks() {
        final PacketBuffer source = new PacketBuffer(Unpooled.buffer());
        source.writeVarInt(1);
        source.writeString("name:A");
        source.writeString("A");
        source.writeString("");
        source.writeVarInt(2);
        source.writeVarInt(4);
        source.writeVarInt(2);
        source.writeInt(0);
        source.writeLong(new BlockPos(10, 64, -3).toLong());
        source.writeByte(2);
        source.writeString("0: 10, 64, -3 / up");
        source.writeVarInt(4);
        source.writeBoolean(true);
        source.writeInt(-1);
        source.writeLong(BlockPos.ORIGIN.toLong());
        source.writeByte(6);
        source.writeString("-1: 0, 0, 0");
        source.writeVarInt(0);
        source.writeBoolean(false);
        final byte[] expected = ByteBufUtil.getBytes(source);

        final PatternUploadNetwork.TargetsMessage message = new PatternUploadNetwork.TargetsMessage();
        message.fromBytes(source);
        final PacketBuffer reencoded = new PacketBuffer(Unpooled.buffer());
        message.toBytes(reencoded);
        check(Arrays.equals(expected, ByteBufUtil.getBytes(reencoded)),
                "targets packet roundtrip keeps the custom name flag");
    }

    private static void check(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
