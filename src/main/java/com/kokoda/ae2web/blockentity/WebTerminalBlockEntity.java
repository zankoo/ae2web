package com.kokoda.ae2web.blockentity;

import appeng.api.networking.*;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class WebTerminalBlockEntity extends BlockEntity implements IGridNodeListener<WebTerminalBlockEntity> {

    private IManagedGridNode mainNode;
    private String terminalId;
    private boolean isConnected = false;
    private Map<AEKey, Long> cachedInventory = Collections.emptyMap();
    private long lastInventoryRefresh = 0L;
    private static final long INVENTORY_CACHE_MS = 1000L;

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
                cachedInventory = Collections.emptyMap();
                lastInventoryRefresh = 0L;
                AE2WebMod.LOGGER.info("Web Terminal {} disconnected from ME network", terminalId);
            }
        }
    }

    private void initializeGridNode() {
        try {
            mainNode = GridHelper.createManagedNode(this, this)
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
     * Get all items stored in the ME network
     */
    public Map<AEKey, Long> getNetworkInventory() {
        if (!isConnectedToGrid()) {
            return Collections.emptyMap();
        }

        long now = System.currentTimeMillis();
        if ((now - lastInventoryRefresh) < INVENTORY_CACHE_MS) {
            return new HashMap<>(cachedInventory);
        }

        Map<AEKey, Long> inventory = queryNetworkInventory();
        cachedInventory = new HashMap<>(inventory);
        lastInventoryRefresh = now;
        return new HashMap<>(inventory);
    }

    private Map<AEKey, Long> queryNetworkInventory() {
        try {
            IGrid grid = mainNode.getGrid();
            if (grid == null) {
                return Collections.emptyMap();
            }

            IStorageService storageService = grid.getService(IStorageService.class);
            MEStorage storage = storageService.getInventory();
            return getAvailableItems(storage);
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Error getting network inventory", e);
            return Collections.emptyMap();
        }
    }

    private Map<AEKey, Long> getAvailableItems(MEStorage storage) {
        KeyCounter availableStacks = storage.getAvailableStacks();
        return StreamSupport.stream(availableStacks.spliterator(), false)
                .filter(AEItemKey.class::isInstance)
                .map(AEItemKey.class::cast)
                .map(key -> Map.entry((AEKey) key, availableStacks.get(key)))
                .filter(entry -> entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Get the crafting service for submitting craft requests
     */
    public ICraftingService getCraftingService() {
        if (!isConnectedToGrid()) return null;
        
        try {
            IGrid grid = mainNode.getGrid();
            if (grid != null) {
                return grid.getService(ICraftingService.class);
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
        cachedInventory = Collections.emptyMap();
        lastInventoryRefresh = 0L;
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
        cachedInventory = Collections.emptyMap();
        lastInventoryRefresh = 0L;
    }
}
