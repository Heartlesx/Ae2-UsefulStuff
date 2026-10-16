package com.extrarawstyle.ae2us;

import com.extrarawstyle.ae2us.mixin.PatternEncoderAccessor;

import appeng.api.config.SecurityPermissions;
import appeng.api.config.Upgrades;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPart;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;
import appeng.parts.AEBasePart;
import appeng.parts.misc.PartInterface;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileCableBus;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtechlite.gtlitecore.common.metatileentity.part.PartMachineMECraftingPatternInputHatch;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.IWorldNameable;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class PatternUploadService {

    private static final ResourceLocation FLUID_PACKET = new ResourceLocation("ae2fc", "fluid_packet");
    private static final ResourceLocation FLUID_DROP = new ResourceLocation("ae2fc", "fluid_drop");
    /** GTLCore 的 ME 样板输入总线 / 总成：网格里的机器是 GT 机器壳，真正的接口宿主藏在 MTE 里。 */
    private static final String GTLITE_PATTERN_HATCH =
            "gregtechlite.gtlitecore.common.metatileentity.part.PartMachineMECraftingPatternInputHatch";
    private static final Map<UUID, UndoRecord> LAST_UPLOADS = new HashMap<>();
    private static final Map<UUID, Map<String, TargetInfo.MemberInfo>> LOCATE_LINKS = new HashMap<>();
    private static final Comparator<Target> TARGET_ORDER = Comparator.comparingInt((Target target) -> target.dimension)
            .thenComparingInt(target -> target.pos.getX()).thenComparingInt(target -> target.pos.getY())
            .thenComparingInt(target -> target.pos.getZ()).thenComparingInt(target -> target.side);

    private PatternUploadService() {
    }

    public static List<TargetInfo> list(final EntityPlayerMP player) {
        final IGrid grid = grid(player, "ae2us.upload.unavailable", "ae2us.upload.denied");
        if (grid == null) {
            return null;
        }
        final SlotRestrictedInput output = ((PatternEncoderAccessor) player.openContainer).ae2us$getPatternOutputSlot();
        final ItemStack current = output == null ? ItemStack.EMPTY : output.getStack();
        final ItemStack pattern = details(current, player.world) == null ? ItemStack.EMPTY : current.copy();

        final List<TargetInfo> result = new ArrayList<>();
        for (final Group group : groups(grid, pattern).values()) {
            group.members.sort(TARGET_ORDER);
            final List<TargetInfo.MemberInfo> members = new ArrayList<>(group.members.size());
            for (final Target target : group.members) {
                members.add(new TargetInfo.MemberInfo(target.dimension, target.pos, target.side,
                        target.location(), target.freeSlots, customNamed(target.host)));
            }
            result.add(new TargetInfo(group.key, group.name, group.location,
                    group.members.size(), group.freeSlots, members));
        }
        result.sort(Comparator.comparing((TargetInfo info) -> info.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(info -> info.key));
        return result;
    }

    public static void locate(final EntityPlayerMP player, final String key,
            final int dimension, final BlockPos pos, final int side) {
        final IGrid grid = grid(player, "ae2us.locate.unavailable", "ae2us.locate.denied");
        if (grid == null) {
            return;
        }
        final Group group = groups(grid, ItemStack.EMPTY).get(key);
        if (group == null) {
            status(player, "ae2us.locate.unavailable");
            return;
        }
        for (final Target target : group.members) {
            if (target.dimension == dimension && target.pos.equals(pos) && target.side == side) {
                final String token = UUID.randomUUID().toString();
                final TargetInfo.MemberInfo member = new TargetInfo.MemberInfo(dimension, pos, side,
                        target.location(), target.freeSlots, customNamed(target.host));
                LOCATE_LINKS.computeIfAbsent(player.getUniqueID(), id -> new HashMap<>()).put(token, member);
                final TextComponentString coordinates = new TextComponentString(member.location);
                coordinates.setStyle(new Style().setColor(TextFormatting.AQUA).setUnderlined(true)
                        .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ae2us_locate " + token))
                        .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new TextComponentTranslation("ae2us.locate.hover"))));
                player.sendMessage(new TextComponentTranslation("ae2us.locate.chat", coordinates));
                return;
            }
        }
        status(player, "ae2us.locate.unavailable");
    }

    public static void teleport(final EntityPlayerMP player, final String token) {
        final Map<String, TargetInfo.MemberInfo> links = LOCATE_LINKS.get(player.getUniqueID());
        final TargetInfo.MemberInfo member = links == null ? null : links.get(token);
        if (member == null) {
            player.sendMessage(new TextComponentTranslation("ae2us.locate.expired"));
            return;
        }
        final IGridNode node = locateNode(member);
        if (node == null) {
            player.sendMessage(new TextComponentTranslation("ae2us.locate.unavailable"));
            return;
        }
        final ISecurityGrid security = node.getGrid().getCache(ISecurityGrid.class);
        if (security == null || !security.hasPermission(player, SecurityPermissions.BUILD)) {
            player.sendMessage(new TextComponentTranslation("ae2us.locate.denied"));
            return;
        }
        final double x = member.pos.getX() + 0.5;
        final double y = member.pos.getY() + 1.0;
        final double z = member.pos.getZ() + 0.5;
        if (player.dimension == member.dimension) {
            player.connection.setPlayerLocation(x, y, z, player.rotationYaw, player.rotationPitch);
        } else {
            player.changeDimension(member.dimension,
                    (world, entity, yaw) -> entity.setLocationAndAngles(x, y, z, yaw, entity.rotationPitch));
            if (player.dimension != member.dimension) {
                player.sendMessage(new TextComponentTranslation("ae2us.locate.unavailable"));
            }
        }
    }

    private static IGridNode locateNode(final TargetInfo.MemberInfo member) {
        final WorldServer world = DimensionManager.getWorld(member.dimension, false);
        if (world == null || !world.isBlockLoaded(member.pos)) {
            return null;
        }
        final TileEntity tile = world.getTileEntity(member.pos);
        final IInterfaceHost host;
        if (member.side >= 0 && member.side < 6 && tile instanceof TileCableBus) {
            final IPart part = ((TileCableBus) tile).getPart(AEPartLocation.fromOrdinal(member.side));
            host = part instanceof IGridHost ? hostOf((IGridHost) part) : null;
        } else if (member.side == 6 && tile instanceof IGridHost) {
            host = hostOf((IGridHost) tile);
        } else {
            host = null;
        }
        if (host == null) {
            return null;
        }
        final IGridNode node;
        if (member.side == 6) {
            // 方块接口就是网格宿主；GT 系接口宿主不是 IGridHost，走它暴露的可操作节点。
            node = host instanceof IGridHost ? ((IGridHost) host).getGridNode(AEPartLocation.INTERNAL)
                    : host.getActionableNode();
        } else {
            node = ((IPart) host).getGridNode();
        }
        return node != null && node.isActive() && node.getGrid() != null ? node : null;
    }

    public static void upload(final EntityPlayerMP player, final String key) {
        final Context context = context(player);
        if (context == null) {
            return;
        }
        final Group group = groups(context.grid, context.stack).get(key);
        if (group == null) {
            status(player, "ae2us.upload.unavailable");
            return;
        }
        upload(player, context, group);
    }

    private static void upload(final EntityPlayerMP player, final Context context, final Group group) {
        if (duplicate(group, context.details)) {
            status(player, "ae2us.upload.duplicate");
            return;
        }
        if (group.freeSlots == 0) {
            status(player, "ae2us.upload.unavailable");
            return;
        }

        group.members.sort(TARGET_ORDER);
        for (final Target target : group.members) {
            final int slot = firstFreeSlot(target, context.stack);
            if (slot < 0) {
                continue;
            }

            final IItemHandler source = context.output.getItemHandler();
            final ItemStack moved = source.extractItem(context.output.getSlotIndex(), 1, false);
            if (moved.isEmpty()) {
                status(player, "ae2us.upload.failed");
                return;
            }
            final ItemStack remainder = target.patterns.insertItem(slot, moved, false);
            if (!remainder.isEmpty()) {
                context.output.putStack(moved);
                status(player, "ae2us.upload.failed");
                return;
            }
            LAST_UPLOADS.put(player.getUniqueID(), new UndoRecord(target, slot,
                    target.patterns.getStackInSlot(slot).copy()));
            context.output.onSlotChanged();
            status(player, "ae2us.upload.success");
            return;
        }
        status(player, "ae2us.upload.unavailable");
    }

    public static boolean autoUpload(final EntityPlayerMP player) {
        if (!PatternUploadData.get(player).enabled(player.getUniqueID())) {
            return true;
        }
        final Context context = context(player);
        if (context == null) {
            return false;
        }
        final String uid = PatternCategory.routingUid(context.stack);
        if (uid.isEmpty()) {
            return true;
        }
        final String name = PatternUploadData.get(player).targetName(player.getUniqueID(), uid,
                PatternCategory.requiresMoldMapping(context.stack) ? "" : PatternCategory.title(context.stack));
        if (name.isEmpty()) {
            return true;
        }
        final Group group = groups(context.grid, context.stack).get("name:" + name);
        if (group == null) {
            return true;
        }
        if (group.freeSlots == 0) {
            if (duplicate(group, context.details)) {
                status(player, "ae2us.upload.duplicate");
                return false;
            }
            return true;
        }
        upload(player, context, group);
        return false;
    }

    private static boolean duplicate(final Group group, final ICraftingPatternDetails pattern) {
        for (final Target target : group.members) {
            final int limit = enabledSlots(target);
            for (int slot = 0; slot < limit; slot++) {
                final ItemStack existing = target.patterns.getStackInSlot(slot);
                if (!existing.isEmpty() && sameProduct(pattern,
                        details(existing, target.host.getTileEntity().getWorld()))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasUndo(final EntityPlayerMP player) {
        return LAST_UPLOADS.containsKey(player.getUniqueID());
    }

    public static void undo(final EntityPlayerMP player) {
        final UndoRecord record = LAST_UPLOADS.get(player.getUniqueID());
        if (record == null) {
            status(player, "ae2us.undo.no_record");
            return;
        }
        final IGrid grid = grid(player, "ae2us.undo.unavailable", "ae2us.undo.denied");
        if (grid == null) {
            return;
        }
        final Target target = findTarget(grid, record.dimension, record.pos, record.side);
        if (target == null || record.slot >= enabledSlots(target)) {
            status(player, "ae2us.undo.unavailable");
            return;
        }
        final ItemStack current = target.patterns.getStackInSlot(record.slot);
        if (!ItemStack.areItemStacksEqual(current, record.stack)) {
            LAST_UPLOADS.remove(player.getUniqueID());
            status(player, "ae2us.undo.changed");
            return;
        }

        final SlotRestrictedInput output = ((PatternEncoderAccessor) player.openContainer).ae2us$getPatternOutputSlot();
        final boolean toOutput = output != null && output.getStack().isEmpty() && output.isItemValid(record.stack);
        final IItemHandler inventory = new PlayerMainInvWrapper(player.inventory);
        if (!toOutput && !ItemHandlerHelper.insertItemStacked(inventory, record.stack.copy(), true).isEmpty()) {
            status(player, "ae2us.undo.full");
            return;
        }

        final ItemStack moved = target.patterns.extractItem(record.slot, 1, false);
        if (!ItemStack.areItemStacksEqual(moved, record.stack)) {
            if (!moved.isEmpty()) {
                target.patterns.insertItem(record.slot, moved, false);
            }
            status(player, "ae2us.undo.unavailable");
            return;
        }
        if (toOutput) {
            output.putStack(moved);
            output.onSlotChanged();
        } else {
            final ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, moved, false);
            if (!remainder.isEmpty()) {
                target.patterns.insertItem(record.slot, remainder, false);
                status(player, "ae2us.undo.full");
                return;
            }
        }
        LAST_UPLOADS.remove(player.getUniqueID());
        status(player, "ae2us.undo.success");
    }

    /** 重命名一个接口组：组内所有接口改成同一个新名字；留空表示清除自定义名、恢复默认名。 */
    public static void rename(final EntityPlayerMP player, final String key, final String name) {
        final IGrid grid = grid(player, "ae2us.rename.unavailable", "ae2us.rename.denied");
        if (grid == null) {
            return;
        }
        final Group group = groups(grid, ItemStack.EMPTY).get(key);
        if (group == null) {
            status(player, "ae2us.rename.unavailable");
            return;
        }
        applyRename(player, grid, group.members, group.name, normalizeName(name));
    }

    /** 重命名单个接口（组内成员视图）。 */
    public static void renameMember(final EntityPlayerMP player, final int dimension, final BlockPos pos,
            final int side, final String name) {
        final IGrid grid = grid(player, "ae2us.rename.unavailable", "ae2us.rename.denied");
        if (grid == null) {
            return;
        }
        final Target target = findTarget(grid, dimension, pos, side);
        if (target == null) {
            status(player, "ae2us.rename.unavailable");
            return;
        }
        applyRename(player, grid, Collections.singletonList(target), targetName(target.host),
                normalizeName(name));
    }

    private static void applyRename(final EntityPlayerMP player, final IGrid grid, final List<Target> targets,
            final String oldName, final String newName) {
        if (oldName.equals(newName)) {
            return;
        }
        int applied = 0;
        for (final Target target : targets) {
            if (applyName(target.host, newName)) {
                applied++;
            }
        }
        if (applied == 0) {
            status(player, "ae2us.rename.unsupported");
            return;
        }

        // 映射同步：只动该玩家自己的映射，且只在旧名已无接口使用时改写（成员视图只改一台时旧名还在，不猜）；
        // 名字被清空时新名字会退化成按相邻机器生成的名字，绑定它没有意义，所以只提示不改。
        final PatternUploadData data = PatternUploadData.get(player);
        final List<PatternUploadData.Mapping> mappings = data.mappings(player.getUniqueID());
        int pointing = 0;
        for (final PatternUploadData.Mapping mapping : mappings) {
            if (mapping.name.equals(oldName)) {
                pointing++;
            }
        }
        int synced = 0;
        if (!newName.isEmpty() && pointing > 0 && !groups(grid, ItemStack.EMPTY).containsKey("name:" + oldName)) {
            for (final PatternUploadData.Mapping mapping : mappings) {
                if (mapping.name.equals(oldName)) {
                    data.save(player.getUniqueID(), mapping.uid, mapping.uid, mapping.title, newName);
                    synced++;
                }
            }
        }

        if (newName.isEmpty()) {
            status(player, pointing > 0 ? "ae2us.rename.cleared_stale" : "ae2us.rename.cleared",
                    applied, pointing);
        } else if (synced > 0) {
            status(player, "ae2us.rename.synced", applied, synced);
        } else if (pointing > 0) {
            status(player, "ae2us.rename.stale", applied, pointing);
        } else {
            status(player, "ae2us.rename.success", applied);
        }
    }

    /** 改名：AE2 系宿主走 ICustomNameObject；GTLCore 的宿主把名字写到 GT 机器壳上
     *  （setCustomName，空串=清除）。两者都不支持时返回 false 由调用方如实提示。 */
    private static boolean applyName(final IInterfaceHost host, final String name) {
        if (host instanceof ICustomNameObject) {
            if (name.isEmpty() && host instanceof AEBasePart) {
                clearPartName((AEBasePart) host);
            } else {
                ((ICustomNameObject) host).setCustomName(name);
            }
            host.saveChanges();
            return true;
        }
        if (host.getTileEntity() instanceof MetaTileEntityHolder) {
            ((MetaTileEntityHolder) host.getTileEntity()).setCustomName(name);
            host.saveChanges();
            return true;
        }
        return false;
    }

    /** 部件接口的名字存在部件物品的 display.Name 上，写空串会变成"有名字但名字为空"，清空必须删键。 */
    private static void clearPartName(final AEBasePart part) {
        final ItemStack stack = part.getItemStack();
        final NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return;
        }
        final NBTTagCompound display = tag.getCompoundTag("display");
        display.removeTag("Name");
        if (display.getSize() == 0) {
            tag.removeTag("display");
        }
        if (tag.getSize() == 0) {
            stack.setTagCompound(null);
        }
    }

    static String normalizeName(final String value) {
        final String trimmed = value == null ? "" : value.trim();
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private static boolean customNamed(final IInterfaceHost host) {
        return customNameOf(host) != null;
    }

    /** 宿主的自定义名（没有则 null）：AE2 系读 ICustomNameObject；GTLCore 的宿主不实现它，
     *  名字存在 GT 机器壳上（IWorldNameable，空串按没有处理）——裸转型会 ClassCastException。 */
    private static String customNameOf(final IInterfaceHost host) {
        if (host instanceof ICustomNameObject) {
            final ICustomNameObject named = (ICustomNameObject) host;
            return named.hasCustomInventoryName() && !named.getCustomInventoryName().isEmpty()
                    ? named.getCustomInventoryName() : null;
        }
        final TileEntity tile = host.getTileEntity();
        if (tile instanceof MetaTileEntityHolder) {
            final IWorldNameable named = (IWorldNameable) tile;
            if (named.hasCustomName()) {
                final String name = named.getName();
                return name == null || name.trim().isEmpty() ? null : name;
            }
        }
        return null;
    }

    private static String targetName(final IInterfaceHost host) {
        final String custom = customNameOf(host);
        return custom != null ? custom : host.getInterfaceDuality().getTermName();
    }

    @SubscribeEvent
    public static void onLogout(final PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_UPLOADS.remove(event.player.getUniqueID());
        LOCATE_LINKS.remove(event.player.getUniqueID());
    }

    private static Context context(final EntityPlayerMP player) {
        final IGrid grid = grid(player, "ae2us.upload.unavailable", "ae2us.upload.denied");
        if (grid == null) {
            return null;
        }
        final SlotRestrictedInput output = ((PatternEncoderAccessor) player.openContainer).ae2us$getPatternOutputSlot();
        final ItemStack stack = output == null ? ItemStack.EMPTY : output.getStack();
        final ICraftingPatternDetails details = details(stack, player.world);
        if (details == null || details.getPrimaryOutput() == null) {
            status(player, "ae2us.upload.no_pattern");
            return null;
        }
        return new Context(grid, output, stack.copy(), details);
    }

    private static IGrid grid(final EntityPlayerMP player, final String unavailable, final String denied) {
        if (!(player.openContainer instanceof ContainerPatternEncoder) || !player.openContainer.canInteractWith(player)) {
            status(player, unavailable);
            return null;
        }
        final ContainerPatternEncoder container = (ContainerPatternEncoder) player.openContainer;
        final IGridNode node = container.getNetworkNode();
        if (node == null || !node.isActive() || node.getGrid() == null) {
            status(player, unavailable);
            return null;
        }
        final IGrid grid = node.getGrid();
        final ISecurityGrid security = grid.getCache(ISecurityGrid.class);
        if (!security.hasPermission(player, SecurityPermissions.BUILD)) {
            status(player, denied);
            return null;
        }
        return grid;
    }

    private static Target findTarget(final IGrid grid, final int dimension, final BlockPos pos, final int side) {
        for (final Class<? extends IGridHost> type : grid.getMachinesClasses()) {
            if (!IInterfaceHost.class.isAssignableFrom(type) && !IGregTechTileEntity.class.isAssignableFrom(type)) {
                continue;
            }
            for (final IGridNode node : grid.getMachines(type)) {
                if (!node.isActive() || node.getGrid() != grid) {
                    continue;
                }
                final IInterfaceHost host = hostOf(node.getMachine());
                if (host == null) {
                    continue;
                }
                final TileEntity tile = host.getTileEntity();
                if (tile == null || tile.getWorld() == null) {
                    continue;
                }
                final Target target = new Target(host, tile);
                if (target.dimension == dimension && target.pos.equals(pos) && target.side == side) {
                    return target;
                }
            }
        }
        return null;
    }

    private static Map<String, Group> groups(final IGrid grid, final ItemStack stack) {
        final Map<String, Group> groups = new HashMap<>();
        for (final Class<? extends IGridHost> type : grid.getMachinesClasses()) {
            if (!IInterfaceHost.class.isAssignableFrom(type) && !IGregTechTileEntity.class.isAssignableFrom(type)) {
                continue;
            }
            for (final IGridNode node : grid.getMachines(type)) {
                if (!node.isActive() || node.getGrid() != grid) {
                    continue;
                }
                final IInterfaceHost host = hostOf(node.getMachine());
                if (host == null) {
                    continue;
                }
                final TileEntity tile = host.getTileEntity();
                if (tile == null || tile.getWorld() == null) {
                    continue;
                }
                final Target target = new Target(host, tile);
                final String name = targetName(host);
                final String key = "name:" + name;
                Group group = groups.get(key);
                if (group == null) {
                    group = new Group(key, name, "");
                    groups.put(key, group);
                }
                group.members.add(target);
                target.freeSlots = freeSlots(target, stack);
                group.freeSlots += target.freeSlots;
            }
        }
        return groups;
    }

    /** 从网格节点机器解析接口宿主：AE2（含附属）的节点机器直接就是宿主；GT 系机器挂的是 GT
     *  机器壳（MetaTileEntityHolder），要经 holder → MTE → getPatternInterfaceHost() 才拿得到。
     *  类名闸门保证 GTLCore 不在场时整段桥不会被解析执行（引用是惰性解析的）。 */
    private static IInterfaceHost hostOf(final IGridHost machine) {
        if (machine instanceof IInterfaceHost) {
            return supported((IInterfaceHost) machine) ? (IInterfaceHost) machine : null;
        }
        if (machine instanceof IGregTechTileEntity) {
            final MetaTileEntity mte = ((IGregTechTileEntity) machine).getMetaTileEntity();
            if (mte != null && GTLITE_PATTERN_HATCH.equals(mte.getClass().getName())) {
                return ((PartMachineMECraftingPatternInputHatch) mte).getPatternInterfaceHost();
            }
        }
        return null;
    }

    private static boolean supported(final IInterfaceHost host) {
        if (host instanceof TileInterface || host instanceof PartInterface) {
            return true;
        }
        final String name = host.getClass().getName();
        return name.equals("com.glodblock.github.common.tile.TileDualInterface")
                || name.equals("com.glodblock.github.common.part.PartDualInterface");
    }

    private static int enabledSlots(final Target target) {
        final int expansions = target.host.getInterfaceDuality().getInstalledUpgrades(Upgrades.PATTERN_EXPANSION);
        return Math.min(target.patterns.getSlots(), 9 * (1 + expansions));
    }

    private static int freeSlots(final Target target, final ItemStack stack) {
        int free = 0;
        for (int slot = 0; slot < enabledSlots(target); slot++) {
            if (target.patterns.getStackInSlot(slot).isEmpty()
                    && (stack.isEmpty() || target.patterns.insertItem(slot, stack, true).isEmpty())) {
                free++;
            }
        }
        return free;
    }

    private static int firstFreeSlot(final Target target, final ItemStack stack) {
        for (int slot = 0; slot < enabledSlots(target); slot++) {
            if (target.patterns.getStackInSlot(slot).isEmpty()
                    && target.patterns.insertItem(slot, stack, true).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static ICraftingPatternDetails details(final ItemStack stack, final net.minecraft.world.World world) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ICraftingPatternItem)) {
            return null;
        }
        try {
            final ICraftingPatternDetails details = ((ICraftingPatternItem) stack.getItem()).getPatternForItem(stack, world);
            return details != null && details.getPrimaryOutput() != null ? details : null;
        } catch (final RuntimeException ignored) {
            return null;
        }
    }

    private static boolean sameProduct(final ICraftingPatternDetails a, final ICraftingPatternDetails b) {
        if (a == null || b == null || a.getPrimaryOutput() == null || b.getPrimaryOutput() == null) {
            return false;
        }
        final IAEItemStack left = a.getPrimaryOutput();
        final IAEItemStack right = b.getPrimaryOutput();
        final FluidStack leftFluid = fluid(left.createItemStack());
        final FluidStack rightFluid = fluid(right.createItemStack());
        if (leftFluid != null || rightFluid != null) {
            return leftFluid != null && rightFluid != null && leftFluid.isFluidEqual(rightFluid);
        }
        return left.isSameType(right);
    }

    private static FluidStack fluid(final ItemStack stack) {
        final ResourceLocation item = stack.getItem().getRegistryName();
        final NBTTagCompound tag = stack.getTagCompound();
        if (FLUID_PACKET.equals(item) && tag != null && tag.hasKey("FluidStack", 10)) {
            return FluidStack.loadFluidStackFromNBT(tag.getCompoundTag("FluidStack"));
        }
        if (FLUID_DROP.equals(item) && tag != null && tag.hasKey("Fluid", 8)) {
            final Fluid type = FluidRegistry.getFluid(tag.getString("Fluid"));
            if (type == null) {
                return null;
            }
            final FluidStack result = new FluidStack(type, 1);
            if (tag.hasKey("FluidTag", 10)) {
                result.tag = tag.getCompoundTag("FluidTag");
            }
            return result;
        }
        return null;
    }

    private static void status(final EntityPlayerMP player, final String key, final Object... args) {
        player.sendStatusMessage(new TextComponentTranslation(key, args), true);
    }

    public static final class TargetInfo {
        public static TargetInfo findByName(final List<TargetInfo> targets, final String name,
                final Function<String, String> localize) {
            TargetInfo match = null;
            for (final TargetInfo target : targets) {
                if (target.name.equals(name)) {
                    return target;
                }
            }
            for (final TargetInfo target : targets) {
                if (localize.apply(target.name).equals(name)) {
                    if (match != null) {
                        return null;
                    }
                    match = target;
                }
            }
            return match;
        }

        public final String key;
        public final String name;
        public final String location;
        public final int count;
        public final int freeSlots;
        public final List<MemberInfo> members;

        public TargetInfo(final String key, final String name, final String location, final int count,
                final int freeSlots, final List<MemberInfo> members) {
            this.key = key;
            this.name = name;
            this.location = location;
            this.count = count;
            this.freeSlots = freeSlots;
            this.members = members;
        }

        public static final class MemberInfo {
            public final int dimension;
            public final BlockPos pos;
            public final int side;
            public final String location;
            public final int freeSlots;
            public final boolean custom;

            public MemberInfo(final int dimension, final BlockPos pos, final int side,
                    final String location, final int freeSlots, final boolean custom) {
                this.dimension = dimension;
                this.pos = pos;
                this.side = side;
                this.location = location;
                this.freeSlots = freeSlots;
                this.custom = custom;
            }
        }
    }

    private static final class Context {
        private final IGrid grid;
        private final SlotRestrictedInput output;
        private final ItemStack stack;
        private final ICraftingPatternDetails details;

        private Context(final IGrid grid, final SlotRestrictedInput output, final ItemStack stack,
                final ICraftingPatternDetails details) {
            this.grid = grid;
            this.output = output;
            this.stack = stack;
            this.details = details;
        }
    }

    private static final class Group {
        private final String key;
        private final String name;
        private final String location;
        private final List<Target> members = new ArrayList<>();
        private int freeSlots;

        private Group(final String key, final String name, final String location) {
            this.key = key;
            this.name = name;
            this.location = location;
        }
    }

    private static final class Target {
        private final IInterfaceHost host;
        private final IItemHandler patterns;
        private final int dimension;
        private final BlockPos pos;
        private final int side;
        private int freeSlots;

        private Target(final IInterfaceHost host, final TileEntity tile) {
            this.host = host;
            this.patterns = host.getInterfaceDuality().getPatterns();
            this.dimension = tile.getWorld().provider.getDimension();
            this.pos = tile.getPos();
            this.side = host instanceof AEBasePart ? ((AEBasePart) host).getSide().ordinal() : 6;
        }

        private String location() {
            return dimension + ": " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                    + (side == 6 ? "" : " / " + appeng.api.util.AEPartLocation.fromOrdinal(side).name());
        }
    }

    private static final class UndoRecord {
        private final int dimension;
        private final BlockPos pos;
        private final int side;
        private final int slot;
        private final ItemStack stack;

        private UndoRecord(final Target target, final int slot, final ItemStack stack) {
            this.dimension = target.dimension;
            this.pos = target.pos;
            this.side = target.side;
            this.slot = slot;
            this.stack = stack;
        }
    }
}
