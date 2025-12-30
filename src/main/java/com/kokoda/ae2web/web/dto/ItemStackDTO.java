package com.kokoda.ae2web.web.dto;

import appeng.api.stacks.AEItemKey;
import net.minecraft.resources.ResourceLocation;

public class ItemStackDTO {
    private final String id;
    private final String name;
    private final String translationKey;
    private final long amount;
    private final String namespace;

    public String materialColor;

    public ItemStackDTO(AEItemKey key, long amount, String name, String translationKey) {
        ResourceLocation itemId = key.getId();
        this.id = itemId.toString();
        this.namespace = itemId.getNamespace();
        this.name = name;
        this.translationKey = translationKey;
        this.amount = amount;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public long getAmount() {
        return amount;
    }

    public String getNamespace() {
        return namespace;
    }
}
