package com.extrarawstyle.ae2us;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PatternCategory {
    private static final String TAG = "ae2us";
    private static final String UID = "category";
    private static final String TITLE = "title";
    private static final String MODIFIED_INPUTS = "modifiedInputs";
    private static final String MOLD = "mold";
    private static final String MOLD_TITLE = "moldTitle";
    private static final ResourceLocation FLUID_PACKET = new ResourceLocation("ae2fc", "fluid_packet");

    private PatternCategory() {
    }

    public static boolean matches(final ItemStack[] expected, final ItemStack[] current,
            final boolean ignoreCount) {
        if (expected == null || current == null || expected.length != current.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (!sameEntry(expected[i], current[i], ignoreCount)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 只删不换的匹配：玩家把概率副产物从样板里去掉时，仍算同一配方（分类路由继续生效）。
     * AE2 处理模式的产物是紧凑数组（{@code getOutputs()} 只收非空槽），删一样数组就少一格，
     * 所以这里不看长度、也不看槽位顺序：当前产物必须都能在抓拍里找到（数量相同、无视产量），
     * 且至少还剩一样；新增或替换产物一律不算同一配方。
     */
    public static boolean matchesAfterTrimming(final ItemStack[] snapshot, final ItemStack[] current,
            final boolean ignoreCount) {
        if (matches(snapshot, current, ignoreCount)) {
            return true;
        }
        if (snapshot == null || current == null) {
            return false;
        }
        final List<ItemStack> pool = new ArrayList<>(Arrays.asList(snapshot));
        boolean any = false;
        for (final ItemStack item : current) {
            if (item.isEmpty()) {
                continue;
            }
            final int index = indexOfEntry(pool, item, ignoreCount);
            if (index < 0) {
                return false;
            }
            pool.remove(index);
            any = true;
        }
        return any;
    }

    /**
     * 编码落签的总判据（1.3.8-dev4 起）：输入或产物**任意一侧**仍与抓拍对得上（沿用上面的"只删不换"
     * 规则）就算同一配方——产物整样换掉（如热锭换冷锭）时由未动的输入侧兜底；两侧都换到对不上才不落签
     * （防止拿旧抓拍给无关配方乱签）。落签后，上传的自动填写/自动上传照旧按分类映射走。
     */
    public static boolean stillSameRecipe(final ItemStack[] snapshotInputs, final ItemStack[] currentInputs,
            final ItemStack[] snapshotOutputs, final ItemStack[] currentOutputs, final boolean ignoreCount) {
        return matchesAfterTrimming(snapshotOutputs, currentOutputs, ignoreCount)
                || matchesAfterTrimming(snapshotInputs, currentInputs, ignoreCount);
    }

    private static int indexOfEntry(final List<ItemStack> pool, final ItemStack target,
            final boolean ignoreCount) {
        for (int i = 0; i < pool.size(); i++) {
            if (sameEntry(pool.get(i), target, ignoreCount)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameEntry(final ItemStack a, final ItemStack b, final boolean ignoreCount) {
        if (ignoreCount && !a.isEmpty() && !b.isEmpty()) {
            return ItemStack.areItemsEqual(a, b)
                    && ItemStack.areItemStackTagsEqual(withoutFluidAmount(a), withoutFluidAmount(b));
        }
        return ItemStack.areItemStacksEqual(a, b);
    }

    private static ItemStack withoutFluidAmount(final ItemStack stack) {
        if (!FLUID_PACKET.equals(stack.getItem().getRegistryName()) || !stack.hasTagCompound()
                || !stack.getTagCompound().hasKey("FluidStack", 10)) {
            return stack;
        }
        // AE2FC packets store fluid quantity in NBT rather than the item stack count.
        final ItemStack copy = stack.copy();
        copy.getTagCompound().getCompoundTag("FluidStack").removeTag("Amount");
        return copy;
    }

    public static void write(final NBTTagCompound patternTag, final String uid, final String title,
            final boolean modifiedInputs) {
        write(patternTag, uid, title, modifiedInputs, "", "");
    }

    public static void write(final NBTTagCompound patternTag, final String uid, final String title,
            final boolean modifiedInputs, final String mold, final String moldTitle) {
        final NBTTagCompound category = new NBTTagCompound();
        category.setString(UID, uid);
        category.setString(TITLE, title);
        category.setBoolean(MODIFIED_INPUTS, modifiedInputs);
        if (!mold.isEmpty()) {
            category.setString(MOLD, mold);
            category.setString(MOLD_TITLE, moldTitle);
        }
        patternTag.setTag(TAG, category);
    }

    public static String uid(final ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG, 10) ? tag.getCompoundTag(TAG).getString(UID) : "";
    }

    public static String title(final ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG, 10) ? tag.getCompoundTag(TAG).getString(TITLE) : "";
    }

    public static String moldKey(final ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return "";
        }
        final String namespace = stack.getItem().getRegistryName().getNamespace();
        final String key = stack.getItem().getTranslationKey(stack);
        return (namespace.equals("gregtech") || namespace.equals("gtlitecore"))
                && (key.contains(".shape.mold.") || key.contains(".shape.extruder.")
                        || key.contains(".shape.field."))
                ? stack.getItem().getRegistryName() + "@" + stack.getMetadata() : "";
    }

    /**
     * 无模具记录时的路由拦截名单：forming press / extruder / fluid solidifier 的旧样板（编码时
     * 还没有模具记录）不允许回退到通用分类映射，需重新从 HEI 导入编码。模具的抓取不经过这里——
     * 按配方内容识别（见 RecipeTransferCategoryMixin）。注意：合金炉这类"多数配方没有模具"的
     * 机器不要加进来，否则普通配方也会失去分类路由。GTLCore 事件视界收束的两类配方同样按
     * 形态力场分流（挤压分类里复制来的压模器配方带 GT 模具，照常走模具键）。
     */
    public static boolean moldMachine(final String uid) {
        return uid.equals("gregtech.forming_press") || uid.equals("gregtech.extruder")
                || uid.equals("gregtech.fluid_solidifier")
                || uid.equals("gtlitecore.blackhole_shaping")
                || uid.equals("gtlitecore.blackhole_stamping");
    }

    public static boolean requiresMoldMapping(final ItemStack stack) {
        return !mold(stack).isEmpty() || moldMachine(uid(stack));
    }

    public static String routingUid(final ItemStack stack) {
        final String uid = uid(stack);
        final String mold = mold(stack);
        if (uid.isEmpty() || mold.isEmpty() && requiresMoldMapping(stack)) {
            return "";
        }
        return mold.isEmpty() ? uid : uid + "|" + mold;
    }

    public static String routingTitle(final ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        final String moldTitle = tag == null ? "" : tag.getCompoundTag(TAG).getString(MOLD_TITLE);
        return moldTitle.isEmpty() ? title(stack) : title(stack) + " / " + moldTitle;
    }

    private static String mold(final ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? "" : tag.getCompoundTag(TAG).getString(MOLD);
    }

    public static boolean modifiedInputs(final ItemStack stack) {
        final NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey(TAG, 10) && tag.getCompoundTag(TAG).getBoolean(MODIFIED_INPUTS);
    }
}
