package com.extrarawstyle.ae2us;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

public final class PatternUploadDataSelfCheck {
    private PatternUploadDataSelfCheck() {
    }

    public static void main(final String[] args) {
        final UUID alice = UUID.randomUUID();
        final UUID bob = UUID.randomUUID();
        final PatternUploadData data = new PatternUploadData();

        check(data.enabled(alice), "auto upload defaults on");
        check("Wiremill".equals(data.targetName(alice, "gregtech.wiremill", "Wiremill")),
                "category title is the default target");
        check(data.save(alice, "", "gregtech.wiremill", "Wiremill", "Copper line"), "save mapping");
        check("Copper line".equals(data.targetName(alice, "gregtech.wiremill", "Wiremill")),
                "mapping overrides the title");
        check("Wiremill".equals(data.targetName(bob, "gregtech.wiremill", "Wiremill")),
                "another player cannot see the mapping");

        check(data.save(alice, "gregtech.wiremill", "gregtech.wiremill.new", "Wiremill", "Copper line"),
                "rename mapping key");
        check("Wiremill".equals(data.targetName(alice, "gregtech.wiremill", "Wiremill")),
                "old key removed after rename");
        data.setEnabled(alice, false);

        final PatternUploadData restored = new PatternUploadData();
        restored.readFromNBT(data.writeToNBT(new NBTTagCompound()));
        check(!restored.enabled(alice) && restored.enabled(bob), "toggle survives save and remains per-player");
        check("Copper line".equals(restored.targetName(alice, "gregtech.wiremill.new", "Wiremill")),
                "renamed mapping survives save");
        restored.remove(alice, "gregtech.wiremill.new");
        check("Wiremill".equals(restored.targetName(alice, "gregtech.wiremill.new", "Wiremill")),
                "deleted mapping returns to default");
        final String gear = "gregtech.extruder|gregtech:meta_item_1@40";
        final String smallGear = "gregtech.extruder|gregtech:meta_item_1@41";
        final String ring = "gregtech.extruder|gregtech:meta_item_1@42";
        check(restored.save(alice, "", "gregtech.extruder", "Extruder", "Default extruder"), "retain old category mapping");
        check(restored.targetName(alice, gear, "").isEmpty(), "unbound mold does not use the old category mapping");
        check(restored.save(alice, "", gear, "Extruder / Gear", "Gear extruder"), "bind gear die");
        check(restored.save(alice, "", smallGear, "Extruder / Small gear", "Small gear extruder"), "bind small gear die");
        check(restored.save(alice, "", ring, "Extruder / Ring", "Ring extruder"), "bind ring die");
        final PatternUploadData moldRestored = new PatternUploadData();
        moldRestored.readFromNBT(restored.writeToNBT(new NBTTagCompound()));
        check(moldRestored.targetName(alice, gear, "").equals("Gear extruder")
                && moldRestored.targetName(alice, smallGear, "").equals("Small gear extruder")
                && moldRestored.targetName(alice, ring, "").equals("Ring extruder"),
                "mold mappings remain separate after saving and loading");
        check(moldRestored.targetName(bob, gear, "").isEmpty(), "mold mapping remains per-player");
        moldRestored.remove(alice, gear);
        check(moldRestored.targetName(alice, gear, "").isEmpty()
                && moldRestored.targetName(alice, smallGear, "").equals("Small gear extruder"),
                "removing one mold mapping does not fall back or delete another");
        System.out.println("Pattern upload data checks passed");
    }

    private static void check(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
