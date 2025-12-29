package com.kokoda.ae2web;

import com.kokoda.ae2web.registry.ModBlockEntities;
import com.kokoda.ae2web.registry.ModBlocks;
import com.kokoda.ae2web.registry.ModItems;
import com.kokoda.ae2web.setup.ClientSetup;
import com.kokoda.ae2web.setup.CommonSetup;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.web.WebServer;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(AE2WebMod.MOD_ID)
public class AE2WebMod {
    public static final String MOD_ID = "ae2web";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AE2WebMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register deferred registries
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        AE2WebConfig.register();

        // Register setup methods
        modEventBus.addListener(this::commonSetup);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> modEventBus.addListener(ClientSetup::onClientSetup));

        // Register ourselves for server and other game events
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("AE2 Web Terminal mod initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        CommonSetup.init(event);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Starting AE2 Web Terminal HTTP server...");
        WebServer.getInstance().start();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Stopping AE2 Web Terminal HTTP server...");
        WebServer.getInstance().stop();
    }
}
