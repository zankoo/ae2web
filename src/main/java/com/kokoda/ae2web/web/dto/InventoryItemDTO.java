package com.kokoda.ae2web.web.dto;

public class InventoryItemDTO {
    private final String type;
    private final String id;
    private final String uniqueId;
    private final String iconKey;
    private final String name;
    private final String translationKey;
    private final long amount;
    private final String namespace;
    private final boolean craftable;

    public InventoryItemDTO(String type, String id, String uniqueId, String iconKey, String name,
                            String translationKey, long amount, String namespace, boolean craftable) {
        this.type = type;
        this.id = id;
        this.uniqueId = uniqueId;
        this.iconKey = iconKey;
        this.name = name;
        this.translationKey = translationKey;
        this.amount = amount;
        this.namespace = namespace;
        this.craftable = craftable;
    }

    public InventoryItemDTO withAmountAndCraftable(long nextAmount, boolean nextCraftable) {
        return new InventoryItemDTO(
                type,
                id,
                uniqueId,
                iconKey,
                name,
                translationKey,
                nextAmount,
                namespace,
                nextCraftable
        );
    }

    public InventoryItemDTO withName(String nextName) {
        return new InventoryItemDTO(
                type,
                id,
                uniqueId,
                iconKey,
                nextName,
                translationKey,
                amount,
                namespace,
                craftable
        );
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getIconKey() {
        return iconKey;
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

    public boolean isCraftable() {
        return craftable;
    }
}
