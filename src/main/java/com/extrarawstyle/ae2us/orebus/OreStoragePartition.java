package com.extrarawstyle.ae2us.orebus;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.prioritylist.IPartitionList;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Collections;

/** An empty whitelist deliberately denies access instead of disabling partitioning. */
public final class OreStoragePartition implements IPartitionList<IAEItemStack> {
    private final OreExportRules rules;

    public OreStoragePartition(final OreExportRules rules) {
        this.rules = new OreExportRules();
        this.rules.read(rules.write());
    }

    @Override
    public boolean isListed(final IAEItemStack stack) {
        final int[] ids = OreDictionary.getOreIDs(stack.getDefinition());
        final String[] names = new String[ids.length];
        for (int i = 0; i < ids.length; i++) { names[i] = OreDictionary.getOreName(ids[i]); }
        return this.rules.matchingRows(names).length > 0;
    }

    @Override
    public boolean isEmpty() { return false; }

    @Override
    public Iterable<IAEItemStack> getItems() { return Collections.emptyList(); }
}
