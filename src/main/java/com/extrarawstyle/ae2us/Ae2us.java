package com.extrarawstyle.ae2us;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import com.extrarawstyle.ae2us.orebus.OreBusGuiHandler;
import com.extrarawstyle.ae2us.orebus.OreBusRegistration;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid = Ae2us.MODID, name = Ae2us.NAME, version = Ae2us.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:appliedenergistics2;required-after:mixinbooter")
public class Ae2us {

    public static final String MODID = "ae2us";
    public static final String NAME = "AE2 Useful Stuff";
    public static final String VERSION = "1.5.1-dev1";

    @Mod.Instance(MODID)
    public static Ae2us INSTANCE;

    @SidedProxy(clientSide = "com.extrarawstyle.ae2us.client.ClientProxy",
            serverSide = "com.extrarawstyle.ae2us.CommonProxy")
    public static CommonProxy PROXY;

    @Mod.EventHandler
    public void preInit(final FMLPreInitializationEvent event) {
        NetworkHandler.register();
        OreBusRegistration.preInit();
        com.extrarawstyle.ae2us.mirror.MirrorRegistration.preInit();
        com.extrarawstyle.ae2us.alarm.AlarmRegistration.preInit();
        com.extrarawstyle.ae2us.parallel.ParallelUnitRegistration.preInit();
        com.extrarawstyle.ae2us.storage.StorageCellRegistration.preInit();
        com.extrarawstyle.ae2us.craftstorage.CraftingStorageRegistration.preInit();
        com.extrarawstyle.ae2us.maintainer.StockMaintainerRegistration.preInit();
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new OreBusGuiHandler());
        FMLCommonHandler.instance().bus().register(PatternUploadService.class);
        event.getModLog().info("{} {} 已加载", NAME, VERSION);
    }

    @Mod.EventHandler
    public void serverStarting(final FMLServerStartingEvent event) {
        event.registerServerCommand(new LocateCommand());
    }
}
