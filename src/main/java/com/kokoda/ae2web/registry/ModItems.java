package com.kokoda.ae2web.registry;

import com.kokoda.ae2web.AE2WebMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = 
            DeferredRegister.create(ForgeRegistries.ITEMS, AE2WebMod.MOD_ID);

    public static final RegistryObject<Item> WEB_TERMINAL = ITEMS.register("web_terminal",
            () -> new BlockItem(ModBlocks.WEB_TERMINAL.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
