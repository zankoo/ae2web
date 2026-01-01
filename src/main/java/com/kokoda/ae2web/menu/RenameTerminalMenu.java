package com.kokoda.ae2web.menu;

import com.kokoda.ae2web.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class RenameTerminalMenu extends AbstractContainerMenu {
    private final BlockPos blockPos;
    private final String currentName;

    // Server-side constructor
    public RenameTerminalMenu(int windowId, Inventory inv, BlockPos pos, String currentName) {
        super(ModMenus.RENAME_TERMINAL.get(), windowId);
        this.blockPos = pos;
        this.currentName = currentName != null ? currentName : "";
    }

    // Client-side constructor (from network packet)
    public RenameTerminalMenu(int windowId, Inventory inv, FriendlyByteBuf buf) {
        super(ModMenus.RENAME_TERMINAL.get(), windowId);
        this.blockPos = buf.readBlockPos();
        this.currentName = buf.readUtf(256);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public String getCurrentName() {
        return currentName;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(
                blockPos.getX() + 0.5,
                blockPos.getY() + 0.5,
                blockPos.getZ() + 0.5) < 64;
    }
}
