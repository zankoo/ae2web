package com.kokoda.ae2web.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatComponentUtilsTest {
    @Test
    void copyToClipboardText_setsCopyClickEvent() {
        Component component = ChatComponentUtils.copyToClipboardText("abc", ChatFormatting.YELLOW);

        ClickEvent clickEvent = component.getStyle().getClickEvent();
        assertThat(clickEvent).isNotNull();
        assertThat(clickEvent.getAction()).isEqualTo(ClickEvent.Action.COPY_TO_CLIPBOARD);
        assertThat(clickEvent.getValue()).isEqualTo("abc");
    }
}

