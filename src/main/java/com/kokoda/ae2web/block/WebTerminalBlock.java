package com.kokoda.ae2web.block;

import com.kokoda.ae2web.blockentity.WebTerminalBlockEntity;
import com.kokoda.ae2web.config.AE2WebConfig;
import com.kokoda.ae2web.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class WebTerminalBlock extends Block implements EntityBlock {

    private static final TagKey<Item> WRENCH_TAG = TagKey.create(Registries.ITEM,
            new ResourceLocation("forge", "tools/wrench"));

    public WebTerminalBlock(Properties properties) {
        super(properties);
    }

    private static void sendTerminalStatus(Player player, WebTerminalBlockEntity terminal) {
        player.sendSystemMessage(Component.literal("[AE2 Web Terminal]").withStyle(ChatFormatting.GOLD));

        // Show display name
        String displayName = terminal.getDisplayNameString();
        player.sendSystemMessage(Component.literal("Name: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(displayName).withStyle(ChatFormatting.WHITE)));

        boolean connected = terminal.isConnectedToGrid();
        ChatFormatting statusColor = connected ? ChatFormatting.GREEN : ChatFormatting.RED;
        player.sendSystemMessage(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(connected ? "Connected" : "Not Connected").withStyle(statusColor)));

        if (!connected) {
            player.sendSystemMessage(Component.literal("Connect to an ME network to enable web access.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        String url = "http://localhost:" + AE2WebConfig.getHttpPort();
        player.sendSystemMessage(Component.literal("Access URL: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(url).withStyle(ChatFormatting.AQUA)));
        player.sendSystemMessage(Component.literal("Terminal ID: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(terminal.getTerminalId()).withStyle(ChatFormatting.YELLOW)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WebTerminalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return type == ModBlockEntities.WEB_TERMINAL.get() 
                ? (lvl, pos, st, be) -> ((WebTerminalBlockEntity) be).tick() 
                : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof WebTerminalBlockEntity terminal)) {
            return InteractionResult.sidedSuccess(false);
        }

        ItemStack heldItem = player.getItemInHand(hand);

        // Shift+right-click with wrench to pick up (like AE2 blocks)
        if (player.isShiftKeyDown() && heldItem.is(WRENCH_TAG)) {
            ItemStack drop = new ItemStack(this);

            CompoundTag beTag = new CompoundTag();
            beTag.putString("terminalId", terminal.getTerminalId());
            beTag.putString("customName", terminal.getCustomName());
            BlockItem.setBlockEntityData(drop, ModBlockEntities.WEB_TERMINAL.get(), beTag);

            if (terminal.getCustomName() != null && !terminal.getCustomName().isBlank()) {
                drop.setHoverName(Component.literal(terminal.getCustomName()));
            }

            if (!player.getInventory().add(drop)) {
                player.drop(drop, false);
            }
            level.removeBlock(pos, false);
            return InteractionResult.sidedSuccess(false);
        }

        // Shift+right-click (no wrench) to open rename GUI
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, terminal, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeUtf(terminal.getCustomName(), 256);
                });
            }
            return InteractionResult.sidedSuccess(false);
        }

        // Normal right-click = show status
        sendTerminalStatus(player, terminal);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WebTerminalBlockEntity terminal) {
                terminal.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
