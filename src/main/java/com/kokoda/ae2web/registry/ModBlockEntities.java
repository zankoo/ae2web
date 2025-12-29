package com.kokoda.ae2web.registry;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = 
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AE2WebMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<WebTerminalBlockEntity>> WEB_TERMINAL = 
            BLOCK_ENTITIES.register("web_terminal",
                    () -> BlockEntityType.Builder.of(WebTerminalBlockEntity::new, 
                            ModBlocks.WEB_TERMINAL.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
