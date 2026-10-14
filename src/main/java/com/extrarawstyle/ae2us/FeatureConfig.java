package com.extrarawstyle.ae2us;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class FeatureConfig {
    public static boolean enableSmartBlocking = true;
    public static boolean enableTerminalCraftableMarks = true;
    public static boolean enableRecipeCraftableMarks = true;
    public static boolean enablePatternRefill = true;
    public static boolean enableCraftedItemPin = true;
    public static boolean autoDisableRandomComplementConflicts = true;
    public static boolean randomComplementPresent;

    private FeatureConfig() {
    }

    public static boolean smartBlockingEnabled() {
        return enableSmartBlocking && !avoidRandomComplement();
    }

    public static boolean terminalCraftableMarksEnabled() {
        return enableTerminalCraftableMarks && !avoidRandomComplement();
    }

    public static boolean recipeCraftableMarksEnabled() {
        return enableRecipeCraftableMarks && !avoidRandomComplement();
    }

    public static boolean patternRefillEnabled() {
        return enablePatternRefill;
    }

    public static boolean craftedItemPinEnabled() {
        return enableCraftedItemPin && !avoidRandomComplement();
    }

    private static boolean avoidRandomComplement() {
        return autoDisableRandomComplementConflicts && randomComplementPresent;
    }

    public static void load(final File file) {
        final Configuration config = new Configuration(file);
        config.load();
        enableSmartBlocking = config.get("features", "enableSmartBlocking", true,
                "Enable AE2US smart blocking logic and button. Disable when using another smart blocking implementation.")
                .setRequiresMcRestart(true).getBoolean();
        enableTerminalCraftableMarks = config.get("features", "enableTerminalCraftableMarks", true,
                "Show craftable item marks in ME terminals and pattern input slots.")
                .setRequiresMcRestart(true).getBoolean();
        enableRecipeCraftableMarks = config.get("features", "enableRecipeCraftableMarks", true,
                "Show craftable item marks in JEI/HEI recipe pages. Does not disable pattern category capture or upload.")
                .setRequiresMcRestart(true).getBoolean();
        enablePatternRefill = config.get("features", "enablePatternRefill", true,
                "Keep the blank pattern slot of pattern terminals stocked from the ME network (toggle button in the terminal GUI).")
                .setRequiresMcRestart(true).getBoolean();
        enableCraftedItemPin = config.get("features", "enableCraftedItemPin", true,
                "Pin the output of crafting jobs you ordered to the front of ME terminal item lists (cyan corner mark). "
                        + "Pinned while the job runs and for a few minutes after it finishes.")
                .setRequiresMcRestart(true).getBoolean();
        autoDisableRandomComplementConflicts = config.get("compatibility", "autoDisableRandomComplementConflicts", true,
                "Automatically disable AE2US smart blocking, both craftable marks and the crafted item pin when RandomComplement is installed. Set false to use the manual feature switches. Does not change saved settings.")
                .setRequiresMcRestart(true).getBoolean();
        config.addCustomCategoryComment("features", "All changes require a game/server restart. Keep smart blocking consistent on client and server.");
        if (config.hasChanged()) {
            config.save();
        }
    }
}
