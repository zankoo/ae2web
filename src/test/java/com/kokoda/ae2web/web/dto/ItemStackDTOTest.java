package com.kokoda.ae2web.web.dto;

import appeng.api.stacks.AEItemKey;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemStackDTOTest {
    @Test
    void itemTagsContainMinecraftLogs() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        ItemStackDTO dto = new ItemStackDTO(AEItemKey.of(Items.OAK_LOG), 1, "Oak Log", "", false);

        assertThat(dto.getTags()).isNotNull();
        assertThat(dto.getTags()).allMatch(tag -> tag != null && !tag.isBlank());
    }
}
