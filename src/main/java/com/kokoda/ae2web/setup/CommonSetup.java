package com.kokoda.ae2web.setup;

import com.kokoda.ae2web.AE2WebMod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public class CommonSetup {
    public static void init(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            AE2WebMod.LOGGER.info("AE2 Web Terminal common setup complete");
        });
    }
}
