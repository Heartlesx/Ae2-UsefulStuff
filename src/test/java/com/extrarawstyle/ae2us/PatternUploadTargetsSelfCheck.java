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
        check(TargetInfo.findByName(targets, displayName, false, localize) == assembler,
                "Chinese mapping resolves to the default interface group");
        check(TargetInfo.findByName(targets, rawName, false, localize) == assembler,
                "mapping bound to the translation key resolves without localization");
        check(TargetInfo.findByName(targets, displayName.substring(0, 2), false, localize) == null,
                "partial names never match");
        check(TargetInfo.findByName(targets, displayName + "2", false, localize) == null,
                "different full names never match");
        final TargetInfo other = new TargetInfo("name:other", "other", "", 1, 9,
                Collections.emptyList());
        final Function<String, String> ambiguous = name -> displayName;
        check(TargetInfo.findByName(Arrays.asList(assembler, other), displayName, false, ambiguous) == null,
                "ambiguous translated names do not select an arbitrary group");
        check(TargetInfo.findByName(Arrays.asList(assembler, other), "other", false, ambiguous) == other,
                "exact stored name takes priority over translated names");
        renameChecks();
        singleRowChecks();
        exactNameChecks();
        hiddenChecks();
        packetChecks();
        System.out.println("Pattern upload target checks passed (matching, exact names, single-row default, "
                + "rename names, hidden interfaces, packet roundtrip)");
    }

    /**
     * 未保存映射的默认分支：搜索结果必须恰好一行，且该行完整名称匹配。
     *
     * <p>这里的 {@code targets} 代表 UI 过滤后的完整结果（跨分页、含满槽组）。</p>
     */
    private static void singleRowChecks() {
        final String name = "化学反应釜";
        final Function<String, String> identity = value -> value;
        final TargetInfo plain = new TargetInfo("name:" + name, name, "", 1, 9,
                Collections.emptyList());
        final TargetInfo full = new TargetInfo("name:" + name + "_full", name, "", 1, 0,
                Collections.emptyList());
        final TargetInfo numbered = new TargetInfo("name:" + name + "1", name + "1", "", 1, 0,
                Collections.emptyList());

        check(TargetInfo.findByName(Collections.singletonList(plain), name, true, identity) == plain,
                "U01 a lone exact name auto-uploads");
        check(TargetInfo.findByName(Collections.singletonList(full), name, true, identity) == full,
                "U09 selection ignores free slots; the caller keeps the writable-slot check");
        final TargetInfo shared = new TargetInfo("name:" + name, name, "", 2, 18,
                Arrays.asList(new TargetInfo.MemberInfo(0, BlockPos.ORIGIN, 6, "", 9, true),
                        new TargetInfo.MemberInfo(0, new BlockPos(1, 0, 0), 6, "", 9, true)));
        check(TargetInfo.findByName(Collections.singletonList(shared), name, true, identity) == shared,
                "U06 two same-named members remain a single matching row");
        check(TargetInfo.findByName(Arrays.asList(plain, numbered), name, true, identity) == null,
                "U02 an exact row does not win while other rows are in the search result");
        check(TargetInfo.findByName(Arrays.asList(plain, numbered), name + "1", true, identity) == null,
                "U02 the numbered row is not picked either");
        final TargetInfo second = new TargetInfo("name:" + name + "2", name + "2", "", 1, 9,
                Collections.emptyList());
        final TargetInfo third = new TargetInfo("name:" + name + "3", name + "3", "", 1, 9,
                Collections.emptyList());
        final List<TargetInfo> fourRows = Arrays.asList(plain, numbered, second, third);
        check(TargetInfo.findByName(fourRows, name, true, identity) == null,
                "four filtered rows with one exact name still open the list");
        check(TargetInfo.findByName(fourRows, name, false, identity) == plain,
                "an explicit mapping still picks its exact row out of four candidates");
        final TargetInfo crusher = new TargetInfo("name:粉碎机", "粉碎机", "", 1, 9,
                Collections.emptyList());
        check(TargetInfo.findByName(Arrays.asList(plain, crusher), name, true, identity) == null,
                "U08 the caller passes only the rows matching the search, so unrelated groups do not reach here");
        check(TargetInfo.findByName(Collections.singletonList(numbered), name, true, identity) == null,
                "U03 a lone numbered machine is not an exact match");
        final TargetInfo noCircuit = new TargetInfo("name:" + name + "无电路", name + "无电路", "", 1, 9,
                Collections.emptyList());
        check(TargetInfo.findByName(Collections.singletonList(noCircuit), name, true, identity) == null,
                "U04 a lone no-circuit machine is not an exact match");
        check(TargetInfo.findByName(Arrays.asList(noCircuit, numbered), name, true, identity) == null,
                "U05 no exact name among several rows opens the list");
        check(TargetInfo.findByName(Arrays.asList(plain, numbered), name, true, identity) == null,
                "U07 a full numbered row still counts as a row");
        check(TargetInfo.findByName(Arrays.asList(plain, numbered), name + "1", false, identity) == numbered,
                "U14 a saved numbered mapping tolerates neighbouring names");
        check(TargetInfo.findByName(Arrays.asList(numbered, plain), name, false, identity) == plain,
                "U15 a saved exact-name mapping tolerates neighbouring names");
        final TargetInfo sameTranslation = new TargetInfo("name:alias", "alias", "", 1, 9,
                Collections.emptyList());
        final Function<String, String> bothTranslate = value -> name;
        check(TargetInfo.findByName(Arrays.asList(plain, sameTranslation), name, true, bothTranslate) == null,
                "U13 two rows translating to the same name are not collapsed");
        final TargetInfo translated = new TargetInfo("name:translated", "translated", "", 1, 9,
                Collections.emptyList());
        final Function<String, String> translate = value -> value.equals("translated") ? name : value;
        check(TargetInfo.findByName(Collections.singletonList(translated), name, true, translate) == translated,
                "U12 a lone localized full name auto-uploads");

        // 行数按 UI 过滤后的完整结果计，与分页无关：filtered 是跨分页的，这里只验证同一份列表的总量口径。
        final List<TargetInfo> acrossPages = Arrays.asList(plain, numbered, second, third, noCircuit);
        check(TargetInfo.findByName(acrossPages, name, true, identity) == null,
                "U10 row count spans every page of the filtered result");
    }

    /** 隐藏接口的过滤必须落在 groups() 里：列表和自动上传都从它取目标，一处过滤两处生效。 */
    private static void hiddenChecks() {
        final String service = source("com/extrarawstyle/ae2us/PatternUploadService.java");
        final String groups = service.substring(service.indexOf("private static Map<String, Group> groups("),
                service.indexOf("private static IInterfaceHost hostOf("));
        check(groups.contains("hidden(host)"),
                "hidden interfaces are excluded when building upload groups");
        final String hidden = service.substring(service.indexOf("private static boolean hidden("),
                service.indexOf("private static int enabledSlots("));
        check(hidden.contains("Settings.INTERFACE_TERMINAL") && hidden.contains("YesNo.NO"),
                "hidden interfaces are the ones with the interface terminal toggle turned off");
    }

    /** 完整名称查找本身的规则；{@code false} 即显式映射分支，不要求候选只有一行。 */
    private static void exactNameChecks() {
        final String name = "燃烧反应室";
        final TargetInfo plain = new TargetInfo("name:" + name, name, "", 1, 9,
                Collections.emptyList());
        final TargetInfo numbered = new TargetInfo("name:" + name + "1", name + "1", "", 1, 9,
                Collections.emptyList());
        final TargetInfo noCircuit = new TargetInfo("name:" + name + "无电路", name + "无电路", "", 1, 9,
                Collections.emptyList());
        final Function<String, String> identity = value -> value;
        check(TargetInfo.findByName(Arrays.asList(noCircuit, numbered), name, false, identity) == null,
                "no exact name: never auto-upload to the numbered combustion chamber");
        check(TargetInfo.findByName(Collections.singletonList(numbered), name, false, identity) == null,
                "a lone numbered machine is not an exact match");
        check(TargetInfo.findByName(Collections.singletonList(noCircuit), name, false, identity) == null,
                "a lone no-circuit machine is not an exact match");
        check(TargetInfo.findByName(Collections.singletonList(plain), name, false, identity) == plain,
                "a lone exact name auto-uploads");
        check(TargetInfo.findByName(Arrays.asList(numbered, plain), name, false, identity) == plain,
                "an explicitly mapped exact name wins even with a numbered machine nearby");
        check(TargetInfo.findByName(Arrays.asList(noCircuit, plain), name, false, identity) == plain,
                "an explicitly mapped exact name wins even with a no-circuit machine nearby");
        check(TargetInfo.findByName(Arrays.asList(noCircuit, numbered, plain), name + "1", false, identity) == numbered,
                "an explicit numbered mapping matches the full name");
        final TargetInfo shared = new TargetInfo("name:" + name, name, "", 2, 18,
                Arrays.asList(new TargetInfo.MemberInfo(0, BlockPos.ORIGIN, 6, "", 9, true),
                        new TargetInfo.MemberInfo(0, new BlockPos(1, 0, 0), 6, "", 9, true)));
        check(TargetInfo.findByName(Collections.singletonList(shared), name, false, identity) == shared,
                "two same-named members remain a single matching group");

        final String service = source("com/extrarawstyle/ae2us/PatternUploadService.java");
        final String auto = service.substring(service.indexOf("public static boolean autoUpload("),
                service.indexOf("private static boolean duplicate("));
        check(!auto.contains("PatternCategory.title(context.stack)"),
                "the server no longer falls back to the category title for direct uploads");
        check(auto.indexOf("targetName(") < auto.indexOf("name.isEmpty()"),
                "the server only uploads directly for an explicitly mapped name");
        check(auto.contains("groups(context.grid, context.stack).get(\"name:\" + name)"),
                "the server auto path only selects the exact stored name");
        check(!auto.contains("members.size()"),
                "the server auto path allows multiple same-named members");
        final String client = source("com/extrarawstyle/ae2us/client/PatternUploadClient.java");
        final int tryAutoAt = client.indexOf("if (tryAuto) {");
        final String clientAuto = client.substring(tryAutoAt,
                client.indexOf("closeButton = new GuiButton", tryAutoAt));
        final String defaultSearch = client.substring(client.indexOf("search.setText(TextFormatting"),
                tryAutoAt);
        check(defaultSearch.contains("filter();"),
                "the client filters the default search before the auto decision");
        check(clientAuto.contains("findByName(filtered, suggestedName, true,"),
                "the default branch looks up the filtered rows requiring one row");
        check(clientAuto.contains("findByName(targets, suggestedName, false,"),
                "the saved-mapping branch searches all targets without the row limit");
        check(clientAuto.indexOf("target.freeSlots > 0") > clientAuto.indexOf("findByName("),
                "the free-slot check runs after the row-count decision");
        check(!clientAuto.contains("members.size()"),
                "the client auto path allows multiple same-named members");
    }

    /** 读主源码树里的文件（自检要核对的是实现，不是产物）。 */
    private static String source(final String relativePath) {
        final java.io.File file = new java.io.File("src/main/java", relativePath);
        if (!file.isFile()) { throw new AssertionError("source not found: " + relativePath); }
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (final java.io.IOException error) {
            throw new AssertionError("cannot read source: " + relativePath, error);
        }
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
