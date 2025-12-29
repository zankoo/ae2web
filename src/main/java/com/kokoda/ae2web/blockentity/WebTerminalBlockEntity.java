package com.kokoda.ae2web.blockentity;

import appeng.api.networking.*;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.registry.ModBlockEntities;
import com.kokoda.ae2web.registry.ModBlocks;
import com.kokoda.ae2web.web.WebServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WebTerminalBlockEntity extends BlockEntity
        implements IGridNodeListener<WebTerminalBlockEntity>, IInWorldGridNodeHost {

    private static final Capability<IInWorldGridNodeHost> IN_WORLD_GRID_NODE_HOST_CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private IManagedGridNode mainNode;
    private String terminalId;
    private boolean isConnected = false;
    private final LazyOptional<IInWorldGridNodeHost> inWorldGridNodeHostCapability = LazyOptional.of(() -> this);

    public WebTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WEB_TERMINAL.get(), pos, state);
        this.terminalId = UUID.randomUUID().toString().substring(0, 8);
    }

    public void tick() {
        if (level == null || level.isClientSide()) return;

        if (mainNode == null) {
            initializeGridNode();
        }

        // Check connection status
        boolean wasConnected = isConnected;
        isConnected = mainNode != null && mainNode.isReady();

        if (wasConnected != isConnected) {
            if (isConnected) {
                WebServer.getInstance().registerTerminal(this);
                AE2WebMod.LOGGER.info("Web Terminal {} connected to ME network at {}", terminalId, worldPosition);
            } else {
                WebServer.getInstance().unregisterTerminal(this);
                AE2WebMod.LOGGER.info("Web Terminal {} disconnected from ME network", terminalId);
            }
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == IN_WORLD_GRID_NODE_HOST_CAPABILITY) {
            return inWorldGridNodeHostCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inWorldGridNodeHostCapability.invalidate();
    }

    private void initializeGridNode() {
        try {
            mainNode = GridHelper.createManagedNode(this, this)
                    .setInWorldNode(true)
                    .setFlags(GridFlags.REQUIRE_CHANNEL)
                    .setIdlePowerUsage(1.0)
                    .setVisualRepresentation(ModBlocks.WEB_TERMINAL.get());
            
            mainNode.create(level, worldPosition);

            // Expose to all directions
            mainNode.setExposedOnSides(EnumSet.allOf(Direction.class));

            AE2WebMod.LOGGER.debug("Grid node initialized for Web Terminal at {}", worldPosition);
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Failed to initialize grid node", e);
        }
    }

    @Override
    public IGridNode getGridNode(Direction direction) {
        if (mainNode == null) {
            return null;
        }
        return mainNode.getNode();
    }

    public boolean isConnectedToGrid() {
        return isConnected && mainNode != null && mainNode.isReady();
    }

    public String getTerminalId() {
        return terminalId;
    }

    public BlockPos getPosition() {
        return worldPosition;
    }

    public Level getTerminalLevel() {
        return level;
    }

    /**
     * Get all items stored in the ME network.
     */
    public Map<AEKey, Long> getNetworkInventory() {
        if (!isConnectedToGrid()) {
            return Collections.emptyMap();
        }

        try {
            IGrid grid = mainNode.getGrid();
            if (grid == null) {
                return Collections.emptyMap();
            }

            var storageService = grid.getStorageService();
            if (storageService == null) {
                return Collections.emptyMap();
            }

            // Use the live inventory view to avoid issues with an empty cached inventory.
            KeyCounter availableStacks = storageService.getInventory().getAvailableStacks();
            Map<AEKey, Long> result = new HashMap<>();
            for (var entry : availableStacks) {
                if (entry.getKey() instanceof AEItemKey && entry.getLongValue() > 0) {
                    result.put(entry.getKey(), entry.getLongValue());
                }
            }
            AE2WebMod.LOGGER.info(
                    "Network inventory fetched for terminal {} at {}: {} keys (filtered to {} item keys)",
                    terminalId, worldPosition, availableStacks.size(), result.size());
            return result;
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Error getting network inventory", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Get the crafting service for submitting craft requests
     */
    public ICraftingService getCraftingService() {
        if (!isConnectedToGrid()) return null;

        try {
            IGrid grid = mainNode.getGrid();
            if (grid != null) {
                return grid.getCraftingService();
            }
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Error getting crafting service", e);
        }
        return null;
    }

    public IActionSource getActionSource() {
        if (mainNode == null) {
            return null;
        }

        IGridNode node = mainNode.getNode();
        if (node == null) {
            return null;
        }

        return IActionSource.ofMachine(() -> node);
    }

    @Override
    public void onSaveChanges(WebTerminalBlockEntity nodeOwner, IGridNode node) {
        setChanged();
    }

    public void onRemoved() {
        if (mainNode != null) {
            mainNode.destroy();
            mainNode = null;
        }
        WebServer.getInstance().unregisterTerminal(this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("terminalId")) {
            terminalId = tag.getString("terminalId");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("terminalId", terminalId);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        onRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (mainNode != null) {
            mainNode.destroy();
            mainNode = null;
        }
        WebServer.getInstance().unregisterTerminal(this);
    }
}
