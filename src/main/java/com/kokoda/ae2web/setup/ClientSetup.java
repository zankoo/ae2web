package com.kokoda.ae2web.setup;

import com.kokoda.ae2web.AE2WebMod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup {
    public static void onClientSetup(final FMLClientSetupEvent event) {
        AE2WebMod.LOGGER.info("AE2 Web Terminal client setup complete");
    }
}
