package com.extrarawstyle.ae2us.client;

import com.extrarawstyle.ae2us.Ae2us;
import com.extrarawstyle.ae2us.storage.StorageCellRegistration;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** 2048M 存储元件的客户端模型登记（普通二维图标，跟原版元件同一路子）。 */
@Mod.EventBusSubscriber(modid = Ae2us.MODID, value = Side.CLIENT)
public final class StorageCellModels {
    private StorageCellModels() {
    }

    @SubscribeEvent
    public static void models(final ModelRegistryEvent event) {
        for (final Item item : new Item[]{StorageCellRegistration.ITEM_CELL, StorageCellRegistration.FLUID_CELL}) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
