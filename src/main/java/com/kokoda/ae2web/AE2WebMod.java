package com.kokoda.ae2web;

import com.kokoda.ae2web.registry.ModBlockEntities;
import com.kokoda.ae2web.registry.ModBlocks;
import com.kokoda.ae2web.registry.ModCreativeTabs;
import com.kokoda.ae2web.registry.ModItems;
import com.kokoda.ae2web.registry.ModMenus;
import com.kokoda.ae2web.setup.ClientSetup;
import com.kokoda.ae2web.setup.CommonSetup;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.network.PacketHandler;
import com.kokoda.ae2web.network.ServerInfoPacket;
import com.kokoda.ae2web.web.WebServer;
import com.kokoda.ae2web.web.handlers.CraftingHandler;
import com.kokoda.ae2web.web.prerender.PrerenderStorageService;
import com.kokoda.ae2web.server.command.TokenCommand;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
        ModCreativeTabs.register(modEventBus);
        ModMenus.register(modEventBus);
        AE2WebConfig.register();

        // Register setup methods
        modEventBus.addListener(this::commonSetup);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> modEventBus.addListener(ClientSetup::onClientSetup));

        // Register ourselves for server and other game events
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("AE2 Web Terminal mod initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.init();
        });
        CommonSetup.init(event);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Starting AE2 Web Terminal HTTP server...");
        WebServer.getInstance().start();
        PrerenderStorageService.getInstance().initialize();
        PrerenderStorageService.getInstance().startMaintenance();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Stopping AE2 Web Terminal HTTP server...");
        CraftingHandler.shutdownExecutor();
        PrerenderStorageService.getInstance().shutdownMaintenance();
        WebServer.getInstance().stop();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketHandler.sendToPlayer(player, ServerInfoPacket.forPlayer(player.getUUID()));
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TokenCommand.register(event.getDispatcher());
    }
}
