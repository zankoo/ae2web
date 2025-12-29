package com.kokoda.ae2web.web.dto;

import appeng.api.stacks.AEItemKey;
import net.minecraft.resources.ResourceLocation;

public class ItemStackDTO {
    private final String id;
    private final String name;
    private final long amount;
    private final String namespace;

    public ItemStackDTO(AEItemKey key, long amount) {
        ResourceLocation itemId = key.getId();
        this.id = itemId.toString();
        this.namespace = itemId.getNamespace();
        this.name = key.getDisplayName().getString();
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getAmount() {
        return amount;
    }

    public String getNamespace() {
        return namespace;
    }
}
