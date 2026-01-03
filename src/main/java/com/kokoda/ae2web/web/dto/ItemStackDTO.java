package com.kokoda.ae2web.web.dto;

import appeng.api.stacks.AEItemKey;
import com.kokoda.ae2web.util.IconKeyUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.List;

public class ItemStackDTO {
    private final String type = "item";
    private final String id;
    private final String uniqueId;
    private final String iconKey;  // For NBT-aware icon requests
    private final List<String> tags;
    private final String name;
    private final String translationKey;
    private final long amount;
    private final String namespace;
    private final boolean craftable;

    public ItemStackDTO(AEItemKey key, long amount, String name, String translationKey, boolean craftable) {
        ResourceLocation itemId = key.getId();
        this.id = itemId.toString();
        this.iconKey = IconKeyUtils.iconKey(key);
        this.uniqueId = "item:" + this.iconKey;
        this.namespace = itemId.getNamespace();
        this.tags = key.toStack().getTags()
                .map(TagKey::location)
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
        this.name = name;
        this.translationKey = translationKey;
        this.amount = amount;
        this.craftable = craftable;
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

    public List<String> getTags() {
        return tags;
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

    public String getType() {
        return type;
    }

    public boolean isCraftable() {
        return craftable;
    }
}
