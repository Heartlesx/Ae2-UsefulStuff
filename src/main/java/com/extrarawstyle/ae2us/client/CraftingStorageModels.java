package com.extrarawstyle.ae2us.client;

import com.extrarawstyle.ae2us.Ae2us;
import com.extrarawstyle.ae2us.craftstorage.CraftingStorageRegistration;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** 六档高级合成存储器的客户端模型登记。 */
@Mod.EventBusSubscriber(modid = Ae2us.MODID, value = Side.CLIENT)
public final class CraftingStorageModels {
    private CraftingStorageModels() {
    }

    @SubscribeEvent
    public static void models(final ModelRegistryEvent event) {
        for (final Item item : CraftingStorageRegistration.ITEMS) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
