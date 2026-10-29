package com.extrarawstyle.ae2us.pickup;

import com.extrarawstyle.ae2us.Ae2us;
import com.extrarawstyle.ae2us.FeatureConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 中键拾取：没开任何界面时对着世界里的方块按中键，先从无线终端绑定的网络里取这个物品。
 * <p>
 * 必须监听 {@link MouseEvent}（可取消），不能监听 {@code InputEvent.MouseInputEvent}——
 * 后者没有 {@code @Cancelable}，调 setCanceled 会直接抛异常崩游戏；
 * 而且它在原版中键处理完之后才广播，就算能取消也拦不住原版。
 * <p>
 * <b>为什么不在鼠标事件里直接干活</b>：原版中键由 KeyBinding 在逻辑 tick 里处理
 * （每秒 20 次），而鼠标事件按渲染帧触发（每秒上百次）。在帧里做射线检测会用到
 * 尚未刷新的插值数据，玩家移动/奔跑时视线还没稳定，表现就是"停下来才灵、跑起来要按好几下"。
 * 所以这里只把"中键被按下了"记下来并取消原版，真正的处理放到下一个逻辑 tick，
 * 那时眼位、朝向、插值都已经同步好了——和原版自己的时机一致。
 */
@Mod.EventBusSubscriber(modid = Ae2us.MODID, value = Side.CLIENT)
public final class ItemPickHandler {

    /** 原版中键的按钮号。 */
    private static final int MIDDLE_BUTTON = 2;

    /** 已经按下、等下一个 tick 处理的标记。 */
    private static boolean pending;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMouse(final MouseEvent event) {
        if (!FeatureConfig.middleClickPickupEnabled()) {
            return;
        }
        if (event.getButton() != MIDDLE_BUTTON || !event.isButtonstate()) {
            return;
        }

        final Minecraft minecraft = Minecraft.getMinecraft();
        // 开着界面时中键有别的含义（终端里就是下单），这里不碰。
        if (minecraft.currentScreen != null || minecraft.player == null || minecraft.world == null) {
            return;
        }
        // 创造模式保持原版直接复制。
        if (minecraft.player.capabilities.isCreativeMode) {
            return;
        }
        // 终端不可用（没带、没电、没绑网络）时完全不干预，原版中键照常。
        if (!PickupTerminals.clientUsable(minecraft.player)) {
            return;
        }

        // 到这里才接管：取消这次点击，交给下一个逻辑 tick 处理。
        pending = true;
        cancel(event);
    }

    /** 逻辑 tick 里处理，此时眼位/朝向/插值都已同步，跟原版中键同一时机。 */
    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pending) {
            return;
        }
        pending = false;

        final Minecraft minecraft = Minecraft.getMinecraft();
        if (!FeatureConfig.middleClickPickupEnabled() || minecraft.currentScreen != null
                || minecraft.player == null || minecraft.world == null) {
            return;
        }

        final ItemStack picked = pickStack(minecraft);
        if (picked.isEmpty()) {
            return;
        }
        // 背包已有怎么切、取到放哪，全由服务端决定：它要改两个格子，
        // 客户端单方面改会和服务端脱节。
        PickupNetwork.request(picked);
    }

    /**
     * 当场重算一次视线，不复用 {@code objectMouseOver}。
     * <p>
     * 那个字段是上一个逻辑 tick 算出来的，可能已经过期；
     * 这里按当前眼位与朝向重算，保证打到的东西就是玩家此刻看的那个。
     */
    private static ItemStack pickStack(final Minecraft minecraft) {
        final RayTraceResult target = rayTrace(minecraft);
        if (target == null || target.typeOfHit != RayTraceResult.Type.BLOCK) {
            return ItemStack.EMPTY;
        }
        final ItemStack picked = minecraft.world.getBlockState(target.getBlockPos()).getBlock()
                .getPickBlock(minecraft.world.getBlockState(target.getBlockPos()), target,
                        minecraft.world, target.getBlockPos(), minecraft.player);
        return picked == null ? ItemStack.EMPTY : picked;
    }

    /** 用玩家当前真实的眼位与朝向做一次方块射线。 */
    private static RayTraceResult rayTrace(final Minecraft minecraft) {
        final net.minecraft.util.math.Vec3d eyes = minecraft.player.getPositionEyes(1.0F);
        final net.minecraft.util.math.Vec3d look = minecraft.player.getLook(1.0F);
        // 原版生存可触及 4.5 格、创造 5 格。不读 REACH 属性：1.12.2 没有这个属性。
        final double reach = minecraft.player.capabilities.isCreativeMode ? 5.0D : 4.5D;
        final net.minecraft.util.math.Vec3d end = new net.minecraft.util.math.Vec3d(
                eyes.x + look.x * reach, eyes.y + look.y * reach, eyes.z + look.z * reach);
        return minecraft.world.rayTraceBlocks(eyes, end, false, false, true);
    }

    /**
     * 取消这次鼠标事件。先判可取消性：对不可取消的事件调 setCanceled 会抛
     * UnsupportedOperationException 直接把客户端打崩（实机崩过一次）。
     */
    private static void cancel(final MouseEvent event) {
        if (event.isCancelable()) {
            event.setCanceled(true);
        }
    }
}
