package com.extrarawstyle.ae2us.client;

import com.extrarawstyle.ae2us.Ae2us;
import com.extrarawstyle.ae2us.parallel.ParallelUnitRegistration;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 高级并行处理单元的客户端模型登记：三态外观（未成型 / 成型未通电 / 成型通电）全走普通 blockstate，
 * 物品图标用未成型模型。跨方块的斜轨连接由贴图自身的平铺相位保证，不需要额外渲染代码。
 */
@Mod.EventBusSubscriber(modid = Ae2us.MODID, value = Side.CLIENT)
public final class ParallelUnitModels {
    private ParallelUnitModels() {
    }

    @SubscribeEvent
    public static void models(final ModelRegistryEvent event) {
        for (final Item item : ParallelUnitRegistration.ITEMS) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
