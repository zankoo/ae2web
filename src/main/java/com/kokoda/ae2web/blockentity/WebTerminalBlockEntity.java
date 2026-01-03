package com.kokoda.ae2web.blockentity;

import appeng.api.networking.*;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingWatcherNode;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.kokoda.ae2web.AE2WebMod;
import com.kokoda.ae2web.menu.RenameTerminalMenu;
import com.kokoda.ae2web.registry.ModBlockEntities;
import com.kokoda.ae2web.registry.ModBlocks;
import com.kokoda.ae2web.web.WebServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
        implements IGridNodeListener<WebTerminalBlockEntity>, IInWorldGridNodeHost, MenuProvider,
        IStorageWatcherNode, ICraftingWatcherNode {

    private static final Capability<IInWorldGridNodeHost> IN_WORLD_GRID_NODE_HOST_CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private IManagedGridNode mainNode;
    private String terminalId;
    private String customName = "";
    private boolean isConnected = false;
    private final LazyOptional<IInWorldGridNodeHost> inWorldGridNodeHostCapability = LazyOptional.of(() -> this);
    private final Set<IStackWatcher> stackWatchers = new HashSet<>();
    private boolean watchingAll = false;

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
                setWatchingAll(false);
                AE2WebMod.LOGGER.info("Web Terminal {} disconnected from ME network", terminalId);
            }
        }

        if (!isConnected) {
            return;
        }

        boolean hasSubscribers = WebServer.getInstance().hasInventorySubscribers(terminalId);
        setWatchingAll(hasSubscribers);
    }

    private void setWatchingAll(boolean enabled) {
        if (watchingAll == enabled) {
            return;
        }
        watchingAll = enabled;
        for (IStackWatcher watcher : stackWatchers) {
            try {
                watcher.setWatchAll(enabled);
            } catch (Exception e) {
                AE2WebMod.LOGGER.debug("Failed to update stack watcher for terminal {}", terminalId, e);
            }
        }
    }

    @Override
    public void updateWatcher(IStackWatcher newWatcher) {
        if (newWatcher == null) {
            return;
        }
        stackWatchers.add(newWatcher);
        newWatcher.setWatchAll(watchingAll);
    }

    @Override
    public void onStackChange(AEKey what, long amount) {
        if (what == null) {
            return;
        }
        if (!watchingAll) {
            return;
        }
        WebServer.getInstance().queueInventoryDelta(terminalId, what, amount, null);
    }

    @Override
    public void onRequestChange(AEKey what) {
        // Ignore crafting request updates for the web inventory view.
    }

    @Override
    public void onCraftableChange(AEKey what) {
        if (what == null) {
            return;
        }
        if (!watchingAll) {
            return;
        }

        boolean craftable = false;
        try {
            ICraftingService service = getCraftingService();
            craftable = service != null && service.isCraftable(what);
        } catch (Exception e) {
            AE2WebMod.LOGGER.debug("Failed to resolve craftable status for terminal {}", terminalId, e);
        }

        WebServer.getInstance().queueInventoryDelta(terminalId, what, null, craftable);
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
                    .setVisualRepresentation(ModBlocks.WEB_TERMINAL.get())
                    .addService(IStorageWatcherNode.class, this)
                    .addService(ICraftingWatcherNode.class, this);
            
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
        // isConnected is set by tick() which already checks mainNode.isReady()
        return isConnected;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String name) {
        this.customName = name != null ? name : "";
        setChanged();
    }

    /**
     * Get human-readable display name as a string (for API/web).
     */
    public String getDisplayNameString() {
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        return String.format("Terminal (%d, %d, %d)",
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }

    // MenuProvider implementation
    @Override
    public Component getDisplayName() {
        return Component.literal("Rename Terminal");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
        return new RenameTerminalMenu(windowId, inv, worldPosition, customName);
    }

    public BlockPos getPosition() {
        return worldPosition;
    }

    public Level getTerminalLevel() {
        return level;
    }

    /**
     * Filter type for network inventory queries.
     */
    public enum KeyFilter {
        ITEMS, FLUIDS, ALL
    }

    /**
     * Get all items stored in the ME network (backward compatible).
     */
    public Map<AEKey, Long> getNetworkInventory() {
        return getNetworkInventory(KeyFilter.ITEMS);
    }

    /**
     * Get stacks from the ME network with filtering.
     */
    public Map<AEKey, Long> getNetworkInventory(KeyFilter filter) {
        if (!isConnectedToGrid()) {
            return Collections.emptyMap();
        }

        try {
            // isConnectedToGrid() guarantees mainNode.isReady(), so grid/storageService are available
            // Use getCachedInventory() for better performance - AE2 maintains this cache
            var storageService = mainNode.getGrid().getStorageService();
            KeyCounter availableStacks = storageService.getCachedInventory();

            Map<AEKey, Long> result = new HashMap<>();
            for (var entry : availableStacks) {
                AEKey key = entry.getKey();
                if (entry.getLongValue() <= 0) continue;

                boolean include = switch (filter) {
                    case ITEMS -> key instanceof AEItemKey;
                    case FLUIDS -> key instanceof AEFluidKey;
                    case ALL -> key instanceof AEItemKey || key instanceof AEFluidKey;
                };

                if (include) {
                    result.put(key, entry.getLongValue());
                }
            }
            AE2WebMod.LOGGER.debug(
                    "Network inventory fetched for terminal {} at {}: {} keys (filtered to {} keys with filter {})",
                    terminalId, worldPosition, availableStacks.size(), result.size(), filter);
            return result;
        } catch (Exception e) {
            AE2WebMod.LOGGER.error("Error getting network inventory", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Get the crafting service for submitting craft requests.
     */
    public ICraftingService getCraftingService() {
        if (!isConnectedToGrid()) {
            return null;
        }
        // isConnectedToGrid() guarantees mainNode.isReady(), so grid is available
        return mainNode.getGrid().getCraftingService();
    }

    public IActionSource getActionSource() {
        if (!isConnectedToGrid()) {
            return null;
        }
        // isConnectedToGrid() guarantees mainNode.isReady(), so node is available
        IGridNode node = mainNode.getNode();
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
        stackWatchers.clear();
        watchingAll = false;
        WebServer.getInstance().unregisterTerminal(this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("terminalId")) {
            terminalId = tag.getString("terminalId");
        }
        if (tag.contains("customName")) {
            customName = tag.getString("customName");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("terminalId", terminalId);
        tag.putString("customName", customName);
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
