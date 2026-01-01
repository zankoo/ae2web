package com.kokoda.ae2web.web.dto;

import appeng.api.stacks.AEFluidKey;
import com.kokoda.ae2web.util.IconKeyUtils;
import net.minecraft.resources.ResourceLocation;

public class FluidStackDTO {
    private final String type = "fluid";
    private final String id;
    private final String uniqueId;
    private final String iconKey;  // For NBT-aware icon requests
    private final String name;
    private final String translationKey;
    private final long amount;
    private final String namespace;
    private final boolean craftable;

    public FluidStackDTO(AEFluidKey key, long amount, String name, String translationKey, boolean craftable) {
        ResourceLocation fluidId = key.getId();
        this.id = fluidId.toString();
        this.iconKey = IconKeyUtils.iconKey(key);
        this.uniqueId = "fluid:" + this.iconKey;
        this.namespace = fluidId.getNamespace();
        this.name = name;
        this.translationKey = translationKey;
        this.amount = amount;
        this.craftable = craftable;
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
