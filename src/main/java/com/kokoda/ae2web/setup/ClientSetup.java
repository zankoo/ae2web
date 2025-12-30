package com.kokoda.ae2web.setup;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.client.PrerenderTrigger;
import com.kokoda.ae2web.client.command.RenderCommand;
import com.kokoda.ae2web.client.render.RenderScheduler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {
    public static void onClientSetup(final FMLClientSetupEvent event) {
        // Register client-side event handlers
        MinecraftForge.EVENT_BUS.register(PrerenderTrigger.class);
        MinecraftForge.EVENT_BUS.register(ClientTickHandler.class);
        MinecraftForge.EVENT_BUS.register(ClientCommandHandler.class);

        AE2WebMod.LOGGER.info("AE2 Web Terminal client setup complete");
    }

    public static class ClientTickHandler {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                RenderScheduler.getInstance().tick();
            }
        }
    }

    public static class ClientCommandHandler {
        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            RenderCommand.register(event.getDispatcher());
            AE2WebMod.LOGGER.info("Registered client commands");
        }
    }
}
