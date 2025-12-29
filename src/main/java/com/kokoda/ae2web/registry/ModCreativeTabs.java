package com.kokoda.ae2web.registry;

import com.kokoda.ae2web.AE2WebMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AE2WebMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> AE2_WEB_TAB = CREATIVE_TABS.register("ae2web_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2web"))
                    .icon(() -> new ItemStack(ModItems.WEB_TERMINAL.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.WEB_TERMINAL.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
