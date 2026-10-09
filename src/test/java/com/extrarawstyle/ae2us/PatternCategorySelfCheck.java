package com.extrarawstyle.ae2us;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import io.netty.buffer.Unpooled;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;

public final class PatternCategorySelfCheck {
    private static final String CAPTURE_MIXIN =
            "com/extrarawstyle/ae2us/mixin/client/RecipeTransferCategoryMixin.class";
    private static final String ENCODER_MIXIN =
            "com/extrarawstyle/ae2us/mixin/PatternEncoderCategoryMixin.class";

    public static void main(final String[] args) throws Exception {
        Bootstrap.register();
        final ItemStack[] inputs = {new ItemStack(Items.IRON_INGOT, 2), ItemStack.EMPTY};
        final ItemStack[] outputs = {new ItemStack(Items.GOLD_INGOT, 1), ItemStack.EMPTY};
        final ItemStack[] doubledInputs = {new ItemStack(Items.IRON_INGOT, 4), ItemStack.EMPTY};
        final ItemStack[] doubledOutputs = {new ItemStack(Items.GOLD_INGOT, 2), ItemStack.EMPTY};

        check(PatternCategory.matches(outputs, doubledOutputs, true), "doubling retains output identity");
        check(!PatternCategory.matches(inputs, doubledInputs, false), "doubling inputs is recorded as an edit");
        check(!PatternCategory.matches(outputs, doubledOutputs, false), "changing only output count is recorded as an edit");
        check(PatternCategory.matches(outputs, outputs, false), "unchanged recipe permits auto upload");
        check(!PatternCategory.matches(outputs,
                new ItemStack[]{new ItemStack(Items.DIAMOND), ItemStack.EMPTY}, true),
                "different output item loses category");
        check(!PatternCategory.matches(outputs, new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY}, true),
                "removed output loses category");

        // 只删不换：把概率副产物从样板里去掉时，分类路由与电路替换都要继续生效。
        // 注意 AE2 处理模式的产物是紧凑数组（getOutputs() 只收非空槽），删掉一样数组就少一格，
        // 所以规则不能依赖长度与槽位顺序。
        final ItemStack[] withByproduct = {new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.GOLD_NUGGET)};
        check(PatternCategory.matchesAfterTrimming(withByproduct, withByproduct, true),
                "untouched recipe keeps category");
        check(PatternCategory.matchesAfterTrimming(withByproduct,
                new ItemStack[]{new ItemStack(Items.GOLD_INGOT)}, true),
                "dense array: removing the byproduct keeps category");
        check(PatternCategory.matchesAfterTrimming(withByproduct,
                new ItemStack[]{new ItemStack(Items.GOLD_INGOT), ItemStack.EMPTY}, true),
                "padded array: removing the byproduct keeps category");
        check(PatternCategory.matchesAfterTrimming(withByproduct,
                new ItemStack[]{new ItemStack(Items.GOLD_NUGGET)}, true),
                "keeping only one of the two outputs keeps category");
        check(!PatternCategory.matchesAfterTrimming(withByproduct,
                new ItemStack[]{new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.DIAMOND)}, true),
                "adding an unseen output loses category");
        check(!PatternCategory.matchesAfterTrimming(withByproduct,
                new ItemStack[]{new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.GOLD_INGOT)}, true),
                "replacing a byproduct loses category");
        check(!PatternCategory.matchesAfterTrimming(withByproduct,
                new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY}, true),
                "clearing every output loses category");

        // 编码落签放宽（1.3.8-dev4）：输入或产物任意一侧仍与抓拍对得上（只删不换）就算同一配方。
        // 换产物（如热锭换冷锭）由未动的输入侧兜底；两侧都换到对不上才不落签。
        final ItemStack[] swappedOutputs = {new ItemStack(Items.DIAMOND), ItemStack.EMPTY};
        check(PatternCategory.stillSameRecipe(inputs, inputs, outputs, swappedOutputs, true),
                "swapping the output keeps category while inputs stay untouched");
        check(PatternCategory.stillSameRecipe(inputs, doubledInputs, outputs, outputs, true),
                "edited input counts keep category while outputs stay untouched");
        check(PatternCategory.stillSameRecipe(inputs, inputs, withByproduct,
                new ItemStack[]{new ItemStack(Items.GOLD_INGOT)}, true),
                "trimming a byproduct still keeps category (dev9 behavior preserved)");
        check(!PatternCategory.stillSameRecipe(inputs, new ItemStack[]{new ItemStack(Items.CLAY_BALL), ItemStack.EMPTY},
                outputs, swappedOutputs, true),
                "rewriting both inputs and outputs loses category");

        final ItemStack changedTag = outputs[0].copy();
        final NBTTagCompound itemTag = new NBTTagCompound();
        itemTag.setString("variant", "different");
        changedTag.setTagCompound(itemTag);
        check(!PatternCategory.matches(outputs, new ItemStack[]{changedTag, ItemStack.EMPTY}, true),
                "different output NBT loses category");
        final ItemStack changedMetadata = new ItemStack(Items.GOLD_INGOT, 1, 1);
        check(!PatternCategory.matches(outputs, new ItemStack[]{changedMetadata, ItemStack.EMPTY}, true),
                "different output metadata loses category");

        final Item fluidPacket = new Item().setRegistryName("ae2fc", "fluid_packet");
        final ItemStack fluid = fluidPacket(fluidPacket, "blaze", 144);
        final ItemStack doubledFluid = fluidPacket(fluidPacket, "blaze", 288);
        check(PatternCategory.matches(new ItemStack[]{fluid}, new ItemStack[]{doubledFluid}, true),
                "144 to 288 fluid output retains category");
        check(!PatternCategory.matches(new ItemStack[]{fluid}, new ItemStack[]{doubledFluid}, false),
                "fluid amount change is recorded as an edit");
        check(!PatternCategory.matches(new ItemStack[]{fluid},
                new ItemStack[]{fluidPacket(fluidPacket, "water", 288)}, true),
                "different fluid loses category");
        final ItemStack taggedFluid = doubledFluid.copy();
        final NBTTagCompound fluidVariant = new NBTTagCompound();
        fluidVariant.setString("variant", "different");
        taggedFluid.getTagCompound().getCompoundTag("FluidStack").setTag("Tag", fluidVariant);
        check(!PatternCategory.matches(new ItemStack[]{fluid}, new ItemStack[]{taggedFluid}, true),
                "different fluid NBT loses category");
        check(fluid.getTagCompound().getCompoundTag("FluidStack").getInteger("Amount") == 144
                && doubledFluid.getTagCompound().getCompoundTag("FluidStack").getInteger("Amount") == 288,
                "comparison does not modify fluid quantities");
        check(!PatternCategory.matches(new ItemStack[]{fluidPacket(Items.PAPER, "blaze", 144)},
                new ItemStack[]{fluidPacket(Items.PAPER, "blaze", 288)}, true),
                "ordinary item NBT remains exact even when named FluidStack");

        final ItemStack pattern = new ItemStack(Items.PAPER);
        final NBTTagCompound patternTag = new NBTTagCompound();
        PatternCategory.write(patternTag, "gregtech.test", "Test", true);
        pattern.setTagCompound(patternTag);
        check("gregtech.test".equals(PatternCategory.uid(pattern)) && PatternCategory.modifiedInputs(pattern),
                "edit flag and category survive pattern NBT");
        final Item molds = new Item() {
            @Override public String getTranslationKey(final ItemStack stack) {
                return stack.getMetadata() == 12 ? "metaitem.shape.mold.plate"
                        : stack.getMetadata() == 13 ? "metaitem.shape.extruder.gear"
                        : stack.getMetadata() == 14 ? "metaitem.shape.extruder.gear.small"
                        : stack.getMetadata() == 15 ? "metaitem.shape.extruder.ring" : "metaitem.circuit.integrated";
            }
        }.setHasSubtypes(true).setRegistryName("gregtech", "meta_item_1");
        final String plate = PatternCategory.moldKey(new ItemStack(molds, 1, 12));
        final String gear = PatternCategory.moldKey(new ItemStack(molds, 1, 13));
        final String smallGear = PatternCategory.moldKey(new ItemStack(molds, 1, 14));
        final String ring = PatternCategory.moldKey(new ItemStack(molds, 1, 15));
        check(!plate.isEmpty() && !gear.isEmpty() && !smallGear.isEmpty() && !ring.isEmpty(),
                "forming press molds and extruder dies are recognized");
        check(!gear.equals(smallGear) && !gear.equals(ring), "gear, small gear and ring have distinct identities");
        check(plate.equals(PatternCategory.moldKey(new ItemStack(molds, 64, 12))), "mold quantity is not a route");
        check(PatternCategory.moldKey(new ItemStack(molds, 1, 99)).isEmpty(), "NC programmed circuit is not a mold");
        PatternCategory.write(patternTag, "gregtech.forming_press", "Forming press", false, plate, "Plate mold");
        check(PatternCategory.routingUid(pattern).equals("gregtech.forming_press|" + plate),
                "mold is retained in pattern NBT even though it is not a consumable input");
        check(PatternCategory.uid(pattern).equals("gregtech.forming_press"), "original category UID is unchanged");
        check(PatternCategory.routingTitle(pattern).equals("Forming press / Plate mold"), "mapping title names its mold");
        check(PatternCategory.moldMachine("gregtech.forming_press") && PatternCategory.moldMachine("gregtech.extruder")
                && PatternCategory.moldMachine("gregtech.fluid_solidifier"),
                "forming press, extruder and fluid solidifier route by their mold");
        check(!PatternCategory.moldMachine("gregtech.assembler"), "assembler does not route by mold");
        PatternCategory.write(patternTag, "gregtech.fluid_solidifier", "Fluid solidifier", false, plate, "Plate mold");
        check(PatternCategory.routingUid(pattern).equals("gregtech.fluid_solidifier|" + plate),
                "fluid solidifier mold is retained in the routing key");
        check(PatternCategory.routingTitle(pattern).equals("Fluid solidifier / Plate mold"),
                "fluid solidifier mapping title names its mold");
        PatternCategory.write(patternTag, "gregtech.forming_press", "Forming press", false);
        check(PatternCategory.requiresMoldMapping(pattern) && PatternCategory.routingUid(pattern).isEmpty(),
                "legacy forming press pattern without mold metadata cannot auto route");
        PatternCategory.write(patternTag, "gregtech.extruder", "Extruder", false);
        check(PatternCategory.routingUid(pattern).isEmpty(), "legacy extruder pattern cannot fall back to category");
        PatternCategory.write(patternTag, "gregtech.fluid_solidifier", "Fluid solidifier", false);
        check(PatternCategory.requiresMoldMapping(pattern) && PatternCategory.routingUid(pattern).isEmpty(),
                "legacy fluid solidifier pattern cannot fall back to category");
        PatternCategory.write(patternTag, "gregtech.assembler", "Assembler", false);
        check(!PatternCategory.requiresMoldMapping(pattern) && PatternCategory.routingUid(pattern).equals("gregtech.assembler"),
                "recipes without molds keep their existing category route");

        // 模具抓取必须按配方内容（moldKey 认 GT 模具/模头物品）识别，不能再按机器白名单把门——
        // 合金炉的模具配方就是被漏掉的一例。白名单只剩"无模具记录的旧样板禁止回退分类"的用途。
        check(!references(CAPTURE_MIXIN, "com/extrarawstyle/ae2us/PatternCategory", "moldMachine"),
                "mold capture does not consult the machine whitelist");
        check(references(CAPTURE_MIXIN, "com/extrarawstyle/ae2us/PatternCategory", "moldKey"),
                "mold capture still recognizes molds by item content");
        PatternCategory.write(patternTag, "gregtech.alloy_smelter", "Alloy smelter", false, gear, "Gear mold");
        check(PatternCategory.routingUid(pattern).equals("gregtech.alloy_smelter|" + gear),
                "alloy smelter mold is retained in the routing key");
        check(PatternCategory.routingTitle(pattern).equals("Alloy smelter / Gear mold"),
                "alloy smelter mapping title names its mold");
        PatternCategory.write(patternTag, "gregtech.alloy_smelter", "Alloy smelter", false);
        check(!PatternCategory.requiresMoldMapping(pattern)
                && PatternCategory.routingUid(pattern).equals("gregtech.alloy_smelter"),
                "mold-less alloy smelter recipes keep their category route "
                        + "(molds are captured by content; alloy smelter must not join the legacy whitelist)");
        // 编码落签必须走 stillSameRecipe（输入或产物任一侧对上即落签），别再退回只查产物侧。
        check(references(ENCODER_MIXIN, "com/extrarawstyle/ae2us/PatternCategory", "stillSameRecipe"),
                "encoder signing gate routes through stillSameRecipe");
        final PacketBuffer incoming = new PacketBuffer(Unpooled.buffer());
        final PacketBuffer outgoing = new PacketBuffer(Unpooled.buffer());
        try {
            incoming.writeString("gregtech.forming_press");
            incoming.writeString("Forming press");
            incoming.writeString(plate);
            incoming.writeString("Plate mold");
            final PatternUploadNetwork.CategoryMessage message = new PatternUploadNetwork.CategoryMessage();
            message.fromBytes(incoming);
            message.toBytes(outgoing);
            check(outgoing.readString(128).equals("gregtech.forming_press")
                    && outgoing.readString(128).equals("Forming press")
                    && outgoing.readString(128).equals(plate)
                    && outgoing.readString(128).equals("Plate mold") && !outgoing.isReadable(),
                    "HEI category network packet preserves the NC mold identity and title");
        } finally {
            incoming.release();
            outgoing.release();
        }
        System.out.println("Pattern category checks passed (identity, fluid counts, molds, trimming and packets)");
    }

    private static ItemStack fluidPacket(final Item item, final String name, final int amount) {
        final ItemStack stack = new ItemStack(item);
        final NBTTagCompound fluid = new NBTTagCompound();
        fluid.setString("FluidName", name);
        fluid.setInteger("Amount", amount);
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("FluidStack", fluid);
        stack.setTagCompound(tag);
        return stack;
    }

    /** 扫类字节码，看它有没有调用指定类的某个方法（抓取门槛是否还在引用机器白名单）。 */
    private static boolean references(final String className, final String owner, final String method) throws Exception {
        final boolean[] found = {false};
        try (InputStream in = PatternCategorySelfCheck.class.getClassLoader().getResourceAsStream(className)) {
            if (in == null) {
                throw new AssertionError("class not found on classpath: " + className);
            }
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                        final String signature, final String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(final int opcode, final String insnOwner, final String insnName,
                                final String insnDescriptor, final boolean isInterface) {
                            if (owner.equals(insnOwner) && method.equals(insnName)) {
                                found[0] = true;
                            }
                        }
                    };
                }
            }, 0);
        }
        return found[0];
    }

    private static void check(final boolean condition, final String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
