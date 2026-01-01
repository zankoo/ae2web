package com.kokoda.ae2web.registry;

import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.menu.RenameTerminalMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, AE2WebMod.MOD_ID);

    public static final RegistryObject<MenuType<RenameTerminalMenu>> RENAME_TERMINAL =
            MENUS.register("rename_terminal",
                    () -> IForgeMenuType.create(RenameTerminalMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
