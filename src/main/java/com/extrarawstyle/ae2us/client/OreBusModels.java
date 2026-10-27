package com.extrarawstyle.ae2us.client;

import com.extrarawstyle.ae2us.Ae2us;
import com.extrarawstyle.ae2us.orebus.OreBusRegistration;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = Ae2us.MODID, value = Side.CLIENT)
public final class OreBusModels {
    private OreBusModels() { }

    @SubscribeEvent
    public static void models(final ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(OreBusRegistration.STORAGE_BUS, 0,
                new ModelResourceLocation(OreBusRegistration.STORAGE_BUS.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(OreBusRegistration.BUS, 0,
                new ModelResourceLocation(OreBusRegistration.BUS.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(com.extrarawstyle.ae2us.alarm.AlarmRegistration.ITEM, 0,
                new ModelResourceLocation(com.extrarawstyle.ae2us.alarm.AlarmRegistration.ITEM.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                com.extrarawstyle.ae2us.maintainer.StockMaintainerRegistration.ITEM, 0,
                new ModelResourceLocation(
                        com.extrarawstyle.ae2us.maintainer.StockMaintainerRegistration.ITEM.getRegistryName(),
                        "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                com.extrarawstyle.ae2us.mirror.MirrorRegistration.PROVIDER, 0,
                new ModelResourceLocation(
                        com.extrarawstyle.ae2us.mirror.MirrorRegistration.PROVIDER.getRegistryName(),
                        "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                com.extrarawstyle.ae2us.mirror.MirrorRegistration.BINDER, 0,
                new ModelResourceLocation(
                        com.extrarawstyle.ae2us.mirror.MirrorRegistration.BINDER.getRegistryName(),
                        "inventory"));
    }
}
