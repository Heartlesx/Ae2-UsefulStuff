package com.extrarawstyle.ae2us;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PatternUploadData extends WorldSavedData {
    private static final String DATA_NAME = "ae2us_pattern_upload";
    private final Map<UUID, Settings> players = new HashMap<>();

    public PatternUploadData() {
        this(DATA_NAME);
    }

    public PatternUploadData(final String name) {
        super(name);
    }

    public static PatternUploadData get(final EntityPlayerMP player) {
        final MapStorage storage = player.server.getWorld(0).getMapStorage();
        PatternUploadData data = (PatternUploadData) storage.getOrLoadData(PatternUploadData.class, DATA_NAME);
        if (data == null) {
            data = new PatternUploadData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public boolean enabled(final UUID player) {
        return settings(player).enabled;
    }

    public void setEnabled(final UUID player, final boolean enabled) {
        final Settings settings = settings(player);
        if (settings.enabled != enabled) {
            settings.enabled = enabled;
            markDirty();
        }
    }

    public String targetName(final UUID player, final String uid, final String title) {
        final Mapping mapping = settings(player).mappings.get(uid);
        return mapping == null ? title : mapping.name;
    }

    public List<Mapping> mappings(final UUID player) {
        final List<Mapping> result = new ArrayList<>(settings(player).mappings.values());
        result.sort(Comparator.comparing((Mapping mapping) -> mapping.title, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(mapping -> mapping.uid));
        return result;
    }

    public boolean save(final UUID player, final String previousUid, final String uid,
            final String title, final String name) {
        if (!valid(uid, 128) || !valid(name, 256) || title == null || title.length() > 128) {
            return false;
        }
        final Settings settings = settings(player);
        final String key = uid.trim();
        if (!settings.mappings.containsKey(key) && !settings.mappings.containsKey(previousUid)
                && settings.mappings.size() >= 256) {
            return false;
        }
        if (previousUid != null && !previousUid.isEmpty()) {
            settings.mappings.remove(previousUid);
        }
        settings.mappings.put(key, new Mapping(key, title.trim().isEmpty() ? key : title.trim(), name.trim()));
        markDirty();
        return true;
    }

    public void remove(final UUID player, final String uid) {
        if (settings(player).mappings.remove(uid) != null) {
            markDirty();
        }
    }

    private static boolean valid(final String value, final int maxLength) {
        return value != null && !value.trim().isEmpty() && value.length() <= maxLength
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }

    private Settings settings(final UUID player) {
        return players.computeIfAbsent(player, key -> new Settings());
    }

    @Override
    public void readFromNBT(final NBTTagCompound nbt) {
        players.clear();
        final NBTTagList list = nbt.getTagList("players", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound saved = list.getCompoundTagAt(i);
            final Settings settings = new Settings();
            settings.enabled = !saved.hasKey("enabled") || saved.getBoolean("enabled");
            final NBTTagList mappings = saved.getTagList("mappings", 10);
            for (int j = 0; j < mappings.tagCount(); j++) {
                final NBTTagCompound entry = mappings.getCompoundTagAt(j);
                final String uid = entry.getString("uid");
                final String name = entry.getString("name");
                if (valid(uid, 128) && valid(name, 256)) {
                    final String title = entry.getString("title");
                    settings.mappings.put(uid, new Mapping(uid, title.isEmpty() ? uid : title, name));
                }
            }
            players.put(UUID.fromString(saved.getString("player")), settings);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound nbt) {
        final NBTTagList list = new NBTTagList();
        for (final Map.Entry<UUID, Settings> player : players.entrySet()) {
            final NBTTagCompound saved = new NBTTagCompound();
            saved.setString("player", player.getKey().toString());
            saved.setBoolean("enabled", player.getValue().enabled);
            final NBTTagList mappings = new NBTTagList();
            for (final Mapping mapping : player.getValue().mappings.values()) {
                final NBTTagCompound entry = new NBTTagCompound();
                entry.setString("uid", mapping.uid);
                entry.setString("title", mapping.title);
                entry.setString("name", mapping.name);
                mappings.appendTag(entry);
            }
            saved.setTag("mappings", mappings);
            list.appendTag(saved);
        }
        nbt.setTag("players", list);
        return nbt;
    }

    private static final class Settings {
        private boolean enabled = true;
        private final Map<String, Mapping> mappings = new HashMap<>();
    }

    public static final class Mapping {
        public final String uid;
        public final String title;
        public final String name;

        public Mapping(final String uid, final String title, final String name) {
            this.uid = uid;
            this.title = title;
            this.name = name;
        }
    }
}
