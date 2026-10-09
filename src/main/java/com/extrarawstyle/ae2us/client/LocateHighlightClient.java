package com.extrarawstyle.ae2us.client;

import com.extrarawstyle.ae2us.Ae2us;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 定位高亮：点上传列表里的 ⊙ 之后，在目标接口的位置画一个闪烁的红色线框做提示。
 * <p>
 * 口径：固定 15 秒，从点击定位按钮那刻起算；**可以同时高亮多个接口**，各自独立计时，
 * 重复点击同一个位置只把它重新计时，不会顶掉别的高亮。红色线框、隔墙可见
 * （关闭深度测试）、500ms 亮/灭交替，与 AE2 自带接口终端的高亮观感一致。
 * <p>
 * 纯客户端状态，独立于列表界面存在（关掉终端后继续有效）。目标在别的维度时只是不画，
 * 状态和计时都保留——通过聊天链接传送过去后，剩下的时间里仍然能看到线框。
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Ae2us.MODID, value = Side.CLIENT)
public final class LocateHighlightClient {

    private static final long DURATION_MILLIS = 15_000L;
    private static final long BLINK_MILLIS = 500L;

    private static final List<Highlight> HIGHLIGHTS = new ArrayList<>();

    private LocateHighlightClient() {
    }

    /** 点击定位按钮时调用：同一个位置只重新计时，别的位置追加，可以同时亮着多个。 */
    public static void highlight(final int dimension, final BlockPos pos) {
        highlight(dimension, pos, System.currentTimeMillis());
    }

    /** 计时入口单独开一个，自检里可以直接喂固定时刻。 */
    static void highlight(final int dimension, final BlockPos pos, final long now) {
        for (final Highlight existing : HIGHLIGHTS) {
            if (existing.matches(dimension, pos)) {
                existing.expireAt = now + DURATION_MILLIS;
                return;
            }
        }
        HIGHLIGHTS.add(new Highlight(dimension, pos, now + DURATION_MILLIS));
    }

    /** 清掉过期的，返回当前还剩几个（也只给自检看）。 */
    static int prune(final long now) {
        for (final Iterator<Highlight> iterator = HIGHLIGHTS.iterator(); iterator.hasNext();) {
            if (now >= iterator.next().expireAt) {
                iterator.remove();
            }
        }
        return HIGHLIGHTS.size();
    }

    @SubscribeEvent
    public static void onRenderWorldLast(final RenderWorldLastEvent event) {
        if (HIGHLIGHTS.isEmpty()) {
            return;
        }
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            HIGHLIGHTS.clear();
            return;
        }
        final long now = System.currentTimeMillis();
        final int currentDimension = mc.world.provider.getDimension();
        boolean visible = false;
        for (final Highlight highlight : HIGHLIGHTS) {
            if (now < highlight.expireAt && highlight.dimension == currentDimension) {
                visible = true;
                break;
            }
        }
        prune(now);
        if (!visible || (now / BLINK_MILLIS & 1L) == 0L) {
            return;
        }

        final double x = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * event.getPartialTicks();
        final double y = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * event.getPartialTicks();
        final double z = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * event.getPartialTicks();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-x, -y, -z);
        GlStateManager.glLineWidth(3.0F);
        GlStateManager.color(1.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.disableDepth();
        GlStateManager.disableTexture2D();
        final Tessellator tessellator = Tessellator.getInstance();
        final BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (final Highlight highlight : HIGHLIGHTS) {
            if (highlight.dimension == currentDimension) {
                outline(buffer, highlight.pos.getX(), highlight.pos.getY(), highlight.pos.getZ());
            }
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.glLineWidth(1.0F);
        GlStateManager.popMatrix();
    }

    private static final class Highlight {
        private final int dimension;
        private final BlockPos pos;
        private long expireAt;

        private Highlight(final int dimension, final BlockPos pos, final long expireAt) {
            this.dimension = dimension;
            this.pos = pos;
            this.expireAt = expireAt;
        }

        private boolean matches(final int dimension, final BlockPos pos) {
            return this.dimension == dimension && this.pos.equals(pos);
        }
    }

    private static void outline(final BufferBuilder buffer, final double x, final double y, final double z) {
        final double x2 = x + 1.0;
        final double y2 = y + 1.0;
        final double z2 = z + 1.0;
        line(buffer, x, y, z, x2, y, z);
        line(buffer, x2, y, z, x2, y, z2);
        line(buffer, x2, y, z2, x, y, z2);
        line(buffer, x, y, z2, x, y, z);
        line(buffer, x, y2, z, x2, y2, z);
        line(buffer, x2, y2, z, x2, y2, z2);
        line(buffer, x2, y2, z2, x, y2, z2);
        line(buffer, x, y2, z2, x, y2, z);
        line(buffer, x, y, z, x, y2, z);
        line(buffer, x2, y, z, x2, y2, z);
        line(buffer, x2, y, z2, x2, y2, z2);
        line(buffer, x, y, z2, x, y2, z2);
    }

    private static void line(final BufferBuilder buffer, final double x1, final double y1, final double z1,
            final double x2, final double y2, final double z2) {
        buffer.pos(x1, y1, z1).color(1.0F, 0.0F, 0.0F, 1.0F).endVertex();
        buffer.pos(x2, y2, z2).color(1.0F, 0.0F, 0.0F, 1.0F).endVertex();
    }
}
