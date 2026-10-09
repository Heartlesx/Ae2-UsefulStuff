package com.extrarawstyle.ae2us.orebus;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class OreExportRules {
    public enum Mode { ROUND_ROBIN, RANDOM, SEQUENTIAL }

    private final List<String> whitelist = new ArrayList<>();
    private final List<String> blacklist = new ArrayList<>();
    private final Map<String, Integer> exactWhite = new HashMap<>();
    private final Set<String> exactBlack = new HashSet<>();
    private final Map<Integer, Pattern> whitePatterns = new HashMap<>();
    private final List<Pattern> blackPatterns = new ArrayList<>();
    private Mode mode = Mode.ROUND_ROBIN;
    private int revision;

    public List<String> list(final boolean black) {
        return Collections.unmodifiableList(black ? this.blacklist : this.whitelist);
    }

    public Mode mode() { return this.mode; }
    public int revision() { return this.revision; }

    public void setMode(final Mode mode) {
        this.mode = mode;
        this.revision++;
    }

    public void add(final boolean black, final String text) {
        final List<String> target = black ? this.blacklist : this.whitelist;
        final LinkedHashSet<String> unique = new LinkedHashSet<>(target);
        for (final String line : text.split("\\r?\\n")) {
            final String name = line.trim();
            if (valid(name)) { unique.add(name); }
        }
        target.clear();
        target.addAll(unique);
        this.compile();
    }

    public static boolean valid(final String name) {
        return !name.isEmpty() && name.length() <= 256 && name.matches("[A-Za-z0-9_.*:/-]+");
    }

    public void remove(final boolean black, final int index) {
        final List<String> target = black ? this.blacklist : this.whitelist;
        if (index >= 0 && index < target.size()) {
            target.remove(index);
            this.compile();
        }
    }

    public void move(final boolean black, final int index, final int offset) {
        final List<String> target = black ? this.blacklist : this.whitelist;
        final int destination = index + offset;
        if (index >= 0 && index < target.size() && destination >= 0 && destination < target.size()) {
            Collections.swap(target, index, destination);
            this.compile();
        }
    }

    public void replace(final boolean black, final int index, final String name) {
        final List<String> target = black ? this.blacklist : this.whitelist;
        if (index >= 0 && index < target.size() && valid(name) && (!target.contains(name) || target.get(index).equals(name))) {
            target.set(index, name);
            this.compile();
        }
    }

    public int[] matchingRows(final String[] oreNames) {
        for (final String name : oreNames) {
            if (this.exactBlack.contains(name)) { return new int[0]; }
        }
        for (final Pattern pattern : this.blackPatterns) {
            for (final String name : oreNames) {
                if (pattern.matcher(name).matches()) { return new int[0]; }
            }
        }
        final BitSet rows = new BitSet();
        for (final String name : oreNames) {
            final Integer exact = this.exactWhite.get(name);
            if (exact != null) { rows.set(exact); }
        }
        for (final Map.Entry<Integer, Pattern> entry : this.whitePatterns.entrySet()) {
            for (final String name : oreNames) {
                if (entry.getValue().matcher(name).matches()) {
                    rows.set(entry.getKey());
                    break;
                }
            }
        }
        final int[] result = new int[rows.cardinality()];
        int index = 0;
        for (int row = rows.nextSetBit(0); row >= 0; row = rows.nextSetBit(row + 1)) { result[index++] = row; }
        return result;
    }

    public NBTTagCompound write() {
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("white", writeList(this.whitelist));
        tag.setTag("black", writeList(this.blacklist));
        tag.setString("mode", this.mode.name());
        return tag;
    }

    public void read(final NBTTagCompound tag) {
        readList(tag.getTagList("white", 8), this.whitelist);
        readList(tag.getTagList("black", 8), this.blacklist);
        try { this.mode = Mode.valueOf(tag.getString("mode")); }
        catch (final IllegalArgumentException ignored) { this.mode = Mode.ROUND_ROBIN; }
        this.compile();
    }

    private void compile() {
        this.exactWhite.clear();
        this.exactBlack.clear();
        this.whitePatterns.clear();
        this.blackPatterns.clear();
        for (int i = 0; i < this.whitelist.size(); i++) {
            final String name = this.whitelist.get(i);
            if (name.contains("*")) { this.whitePatterns.put(i, pattern(name)); }
            else { this.exactWhite.put(name, i); }
        }
        for (final String name : this.blacklist) {
            if (name.contains("*")) { this.blackPatterns.add(pattern(name)); }
            else { this.exactBlack.add(name); }
        }
        this.revision++;
    }

    private static Pattern pattern(final String name) {
        final StringBuilder regex = new StringBuilder();
        for (final String part : name.split("\\*", -1)) {
            if (regex.length() > 0) { regex.append(".*"); }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }

    private static NBTTagList writeList(final List<String> names) {
        final NBTTagList result = new NBTTagList();
        for (final String name : names) { result.appendTag(new NBTTagString(name)); }
        return result;
    }

    private static void readList(final NBTTagList tags, final List<String> target) {
        final LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (int i = 0; i < tags.tagCount(); i++) {
            final String name = tags.getStringTagAt(i).trim();
            if (valid(name)) { unique.add(name); }
        }
        target.clear();
        target.addAll(unique);
    }
}
