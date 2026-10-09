package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.orebus.OreExportRules;
import com.extrarawstyle.ae2us.orebus.OreExportScheduler;
import com.extrarawstyle.ae2us.orebus.OreExportTransfer;
import com.extrarawstyle.ae2us.orebus.OreBusNetwork;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.item.AEItemStack;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.oredict.OreDictionary;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Optional;

public final class OreExportSelfCheck {
    public static void main(final String[] args) throws Exception {
        final OreExportRules rules = new OreExportRules();
        check(rules.matchingRows(new String[]{"oreIron"}).length == 0, "empty whitelist exports nothing");
        rules.add(false, "oreIron\nore*\noreIron\n\n ingotCopper ");
        check(rules.list(false).size() == 3, "trim and deduplicate rules");
        check(Arrays.equals(rules.matchingRows(new String[]{"oreIron", "ore"}), new int[]{0, 1}), "multiple tags and overlapping rules");
        rules.add(true, "oreGold");
        check(rules.matchingRows(new String[]{"oreIron", "oreGold"}).length == 0, "blacklist wins across all tags");
        check(Arrays.equals(rules.matchingRows(new String[]{"ingotCopper"}), new int[]{2}), "exact name");
        check(rules.matchingRows(new String[]{"oreiron"}).length == 1, "ore names remain case sensitive");
        rules.add(false, "*Iron*\nore.a+b");
        check(rules.matchingRows(new String[]{"dustIronSmall"}).length == 1, "leading and trailing wildcard");
        check(!OreExportRules.valid("oreIron|oreGold"), "unsupported expressions rejected");
        rules.add(false, "test.name");
        check(rules.matchingRows(new String[]{"testXname"}).length == 0, "regex characters are literal");
        rules.replace(false, 0, "oreTin");
        rules.move(false, 0, 1);
        check(rules.list(false).get(1).equals("oreTin"), "edit and reorder");
        rules.remove(false, 1);
        rules.setMode(OreExportRules.Mode.SEQUENTIAL);
        final StringBuilder many = new StringBuilder();
        for (int i = 0; i < 10000; i++) { many.append("oreTest").append(i).append('\n'); }
        rules.add(false, many.toString());
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(rules.write(), output);
        final NBTTagCompound saved = CompressedStreamTools.readCompressed(new ByteArrayInputStream(output.toByteArray()));
        final OreExportRules restored = new OreExportRules();
        restored.read(saved);
        check(restored.list(false).equals(rules.list(false)) && restored.list(true).equals(rules.list(true))
                && restored.mode() == rules.mode(), "10000 rules survive compressed save/reload without a slot cap");
        check(Arrays.stream(restored.matchingRows(new String[]{"oreTest9999"})).anyMatch(
                row -> restored.list(false).get(row).equals("oreTest9999")), "last rule in a large list remains usable");
        final OreBusNetwork.State page = new OreBusNetwork.State();
        page.window = 23; page.offset = 9992; page.total = 10000; page.whiteCount = 10000;
        page.blackCount = 7; page.budget = 1024; page.redstoneCard = true;
        for (int i = 0; i < 8; i++) { page.names.add("oreTest" + (9992 + i)); }
        final io.netty.buffer.ByteBuf encoded = io.netty.buffer.Unpooled.buffer();
        page.toBytes(encoded);
        check(encoded.readableBytes() < 256, "large rule lists synchronize only one small page");
        final OreBusNetwork.State decoded = new OreBusNetwork.State();
        decoded.fromBytes(encoded);
        check(decoded.names.equals(page.names) && decoded.total == 10000 && decoded.offset == 9992
                && decoded.window == 23 && decoded.redstoneCard && decoded.budget == 1024, "page packet retains count, offset and upgrades");
        encoded.release();

        final int[] vanilla = {1, 8, 32, 64, 96};
        final int[] expected = {11, 86, 342, 683, 1024};
        for (int cards = 0; cards <= 4; cards++) {
            check(OreExportScheduler.budget(cards) == expected[cards] && expected[cards] >= 4 * vanilla[cards], "speed tier " + cards);
        }
        final OreExportScheduler<String> scheduler = new OreExportScheduler<>();
        final List<String> sends = new ArrayList<>();
        final Random random = new Random(48);
        for (int i = 0; i < 4; i++) {
            final int sent = scheduler.export(stock(), OreExportRules.Mode.ROUND_ROBIN, 1024, random, (item, limit) -> { sends.add(item); return limit; });
            check(sent == 1024, "full budget spans many stacks");
        }
        check(sends.equals(Arrays.asList("iron", "gold", "iron", "gold")), "rule round robin persists across cycles");
        scheduler.reset();
        sends.clear();
        scheduler.export(stock(), OreExportRules.Mode.SEQUENTIAL, 1024, random, (item, limit) -> { sends.add(item); return limit; });
        check(sends.equals(Arrays.asList("iron")), "strict mode drains first rule");
        sends.clear();
        scheduler.export(stock(), OreExportRules.Mode.SEQUENTIAL, 1024, random, (item, limit) -> { sends.add(item); return 0; });
        check(sends.equals(Arrays.asList("iron")), "rejected first rule blocks later rules");
        sends.clear();
        final List<OreExportScheduler.Stock<String>> scarce = Arrays.asList(
                new OreExportScheduler.Stock<>("iron", 3, new int[]{0}), new OreExportScheduler.Stock<>("gold", 2000, new int[]{1}));
        check(scheduler.export(scarce, OreExportRules.Mode.SEQUENTIAL, 1024, random,
                (item, limit) -> { sends.add(item); return limit; }) == 1024 && sends.equals(Arrays.asList("iron", "gold")), "advance only when previous rule exhausted");
        sends.clear();
        scheduler.export(stock(), OreExportRules.Mode.SEQUENTIAL, 11, random, (item, limit) -> { sends.add(item); return limit; });
        check(sends.equals(Arrays.asList("iron")), "new stock returns to first rule");
        sends.clear();
        final List<OreExportScheduler.Stock<String>> overlap = Arrays.asList(new OreExportScheduler.Stock<>("iron", 9, new int[]{0, 1}));
        check(scheduler.export(overlap, OreExportRules.Mode.ROUND_ROBIN, 1024, random, (item, limit) -> limit) == 9,
                "overlapping rules do not multiply stock");
        scheduler.reset();
        final List<OreExportScheduler.Stock<String>> variants = Arrays.asList(
                new OreExportScheduler.Stock<>("ironA", 2000, new int[]{0}), new OreExportScheduler.Stock<>("ironB", 2000, new int[]{0}));
        scheduler.export(variants, OreExportRules.Mode.ROUND_ROBIN, 11, random, (item, limit) -> { sends.add(item); return limit; });
        scheduler.export(variants, OreExportRules.Mode.ROUND_ROBIN, 11, random, (item, limit) -> { sends.add(item); return limit; });
        check(sends.equals(Arrays.asList("ironA", "ironB")), "variants within a rule rotate");
        final int[] counts = new int[2];
        for (int i = 0; i < 1000; i++) {
            scheduler.export(stock(), OreExportRules.Mode.RANDOM, 1, random,
                    (item, limit) -> { counts[item.equals("iron") ? 0 : 1]++; return limit; });
        }
        check(counts[0] > 400 && counts[1] > 400, "random selection reaches both rules");
        scheduler.reset();
        check(scheduler.export(stock(), OreExportRules.Mode.ROUND_ROBIN, 11, random,
                (item, limit) -> item.equals("iron") ? 0 : limit) == 11, "round robin skips rejected items");
        memoryCardChecks();
        transferChecks();
        OreStorageSelfCheck.verify();
        com.extrarawstyle.ae2us.orebus.OreBusCardSelfCheck.verify();
        System.out.println("Ore export checks passed (matching, 10000-rule saves, speed, scheduling, memory card copy, powered transfers and rollback)");
    }

    /** 记忆卡要能复制两个总线的矿典规则：部件必须声明 downloadSettings/uploadSettings。 */
    private static void memoryCardChecks() throws Exception {
        check(declaresMemoryCardMethods("com/extrarawstyle/ae2us/orebus/PartOreExportBus.class"),
                "ore export bus declares memory card settings methods");
        check(declaresMemoryCardMethods("com/extrarawstyle/ae2us/orebus/PartOreStorageBus.class"),
                "ore storage bus declares memory card settings methods");
        check(!declaresMemoryCardMethods("com/extrarawstyle/ae2us/orebus/OreExportRules.class"),
                "memory card method detector distinguishes missing overrides");
    }

    private static boolean declaresMemoryCardMethods(final String resource) throws Exception {
        final boolean[] found = new boolean[2];
        try (InputStream in = OreExportSelfCheck.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new AssertionError("missing class resource: " + resource);
            }
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                        final String signature, final String[] exceptions) {
                    if ("downloadSettings".equals(name)) {
                        found[0] = true;
                    } else if ("uploadSettings".equals(name)) {
                        found[1] = true;
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return found[0] && found[1];
    }

    private static List<OreExportScheduler.Stock<String>> stock() {
        return Arrays.asList(new OreExportScheduler.Stock<>("iron", 10000, new int[]{0}),
                new OreExportScheduler.Stock<>("gold", 10000, new int[]{1}));
    }

    private static void check(final boolean condition, final String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static void transferChecks() throws Exception {
        Bootstrap.register();
        // Platform needs a side and a mod index, but these fixtures never start a game or load mods.
        final java.lang.reflect.Field sided = net.minecraftforge.fml.common.FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        sided.setAccessible(true);
        sided.set(net.minecraftforge.fml.common.FMLCommonHandler.instance(), java.lang.reflect.Proxy.newProxyInstance(
                OreExportSelfCheck.class.getClassLoader(), new Class<?>[]{net.minecraftforge.fml.common.IFMLSidedHandler.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getSide")) { return net.minecraftforge.fml.relauncher.Side.SERVER; }
                    throw new AssertionError("Unexpected Forge call: " + method.getName());
                }));
        final java.lang.reflect.Field mods = net.minecraftforge.fml.common.Loader.class.getDeclaredField("namedMods");
        mods.setAccessible(true);
        mods.set(net.minecraftforge.fml.common.Loader.instance(), java.util.Collections.emptyMap());
        final ItemStack sample = new ItemStack(Items.IRON_INGOT);
        final OreExportRules actual = new OreExportRules();
        actual.add(false, "ingotIron");
        final String[] names = Arrays.stream(OreDictionary.getOreIDs(sample)).mapToObj(OreDictionary::getOreName).toArray(String[]::new);
        check(actual.matchingRows(names).length == 1, "real Forge ore registration matches an item");
        final NBTTagCompound variant = new NBTTagCompound();
        variant.setString("variant", "tagged");
        sample.setTagCompound(variant);
        final IAEItemStack ae = AEItemStack.fromItemStack(sample);
        final FixtureInventory inventory = new FixtureInventory();
        final IActionSource source = new IActionSource() {
            @Override public Optional<net.minecraft.entity.player.EntityPlayer> player() { return Optional.empty(); }
            @Override public Optional<appeng.api.networking.security.IActionHost> machine() { return Optional.empty(); }
            @Override public <T> Optional<T> context(final Class<T> type) { return Optional.empty(); }
        };
        final double[] energy = {20000};
        final appeng.api.networking.energy.IEnergySource power = (amount, mode, multiplier) -> {
            final double available = Math.min(amount, energy[0]);
            if (mode == Actionable.MODULATE) { energy[0] -= available; }
            return available;
        };
        final ItemStackHandler target = new ItemStackHandler(32);
        final OreExportTransfer transfer = new OreExportTransfer();
        check(transfer.send(new AdaptorItemHandler(target), inventory, power, source, ae, 1024) == 1024,
                "real adaptor and powered extraction transfer 1024 across sixteen stacks");
        check(inventory.count == 8976 && total(target) == 1024 && energy[0] == 18976 && transfer.pending().isEmpty(),
                "normal transfer conserves items and charges energy");
        check(target.getStackInSlot(0).getTagCompound().equals(variant), "item NBT survives transfer");
        energy[0] = 0;
        check(transfer.send(new AdaptorItemHandler(target), inventory, power, source, ae, 1024) == 0 && inventory.count == 8976,
                "no energy cannot extract");
        energy[0] = 20000;
        inventory.denyExtract = true;
        check(transfer.send(new AdaptorItemHandler(target), inventory, power, source, ae, 1024) == 0 && total(target) == 1024,
                "inventory extraction denial is respected");
        inventory.denyExtract = false;
        final ItemStackHandler rejecting = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
                if (simulate) { return ItemStack.EMPTY; }
                return stack.copy();
            }
        };
        check(transfer.send(new AdaptorItemHandler(rejecting), inventory, power, source, ae, 64) == 0
                && inventory.count == 8976 && transfer.pending().isEmpty(), "real insertion rejection rolls back extracted items");
        inventory.denyInsert = true;
        check(transfer.send(new AdaptorItemHandler(rejecting), inventory, power, source, ae, 64) == 0
                && inventory.count == 8912 && transfer.pending().getCount() == 64, "failed rollback stays in escrow");
        final NBTTagCompound saved = new NBTTagCompound();
        transfer.write(saved);
        final OreExportTransfer reloaded = new OreExportTransfer();
        reloaded.read(saved);
        check(reloaded.pending().getCount() == 64 && reloaded.pending().getTagCompound().equals(variant), "escrow count and NBT survive save/reload");
        check(reloaded.send(new AdaptorItemHandler(target), inventory, power, source, ae, 64) == 0, "escrow blocks additional extraction");
        inventory.denyInsert = false;
        check(reloaded.recover(inventory, source) && inventory.count == 8976 && total(target) == 1024,
                "restored escrow returns to network without loss or duplication");
        reloaded.write(saved);
        check(!saved.hasKey("oreExportPending"), "successful recovery clears saved escrow");
        final ItemStackHandler partial = new ItemStackHandler(1);
        partial.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 63));
        final IAEItemStack untagged = AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT));
        check(reloaded.send(new AdaptorItemHandler(partial), inventory, power, source, untagged, 1024) == 1 && inventory.count == 8975,
                "nearly full container extracts only what fits");
    }

    private static int total(final ItemStackHandler inventory) {
        int total = 0;
        for (int i = 0; i < inventory.getSlots(); i++) { total += inventory.getStackInSlot(i).getCount(); }
        return total;
    }

    private static final class FixtureInventory implements IMEInventory<IAEItemStack> {
        long count = 10000;
        boolean denyExtract, denyInsert;
        @Override
        public IAEItemStack injectItems(final IAEItemStack input, final Actionable mode, final IActionSource source) {
            if (this.denyInsert) { return input.copy(); }
            if (mode == Actionable.MODULATE) { this.count += input.getStackSize(); }
            return null;
        }
        @Override
        public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final IActionSource source) {
            if (this.denyExtract || this.count == 0) { return null; }
            final IAEItemStack extracted = request.copy();
            extracted.setStackSize(Math.min(this.count, request.getStackSize()));
            if (mode == Actionable.MODULATE) { this.count -= extracted.getStackSize(); }
            return extracted;
        }
        @Override public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out) { return out; }
        @Override public IStorageChannel<IAEItemStack> getChannel() {
            return new IStorageChannel<IAEItemStack>() {
                @Override public IItemList<IAEItemStack> createList() { throw new AssertionError("unused"); }
                @Override public IAEItemStack createStack(final Object item) { return AEItemStack.fromItemStack((ItemStack) item); }
                @Override public IAEItemStack readFromPacket(final io.netty.buffer.ByteBuf buffer) { throw new AssertionError("unused"); }
                @Override public IAEItemStack createFromNBT(final NBTTagCompound tag) { return AEItemStack.fromNBT(tag); }
            };
        }
    }
}
