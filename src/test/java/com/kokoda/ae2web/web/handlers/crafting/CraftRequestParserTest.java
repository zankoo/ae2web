package com.kokoda.ae2web.web.handlers.crafting;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CraftRequestParserTest {
    @Test
    void parsesValidItemWithDefaultAmount() throws Exception {
        CraftRequestParser.CraftRequest req = CraftRequestParser.parse("{\"type\":\"item\",\"id\":\"minecraft:stone\"}");
        assertThat(req.keyType()).isEqualTo(CraftRequestParser.CraftKeyType.ITEM);
        assertThat(req.id()).isEqualTo("minecraft:stone");
        assertThat(req.amount()).isEqualTo(1L);
        assertThat(req.cpuName()).isNull();
        assertThat(req.terminalId()).isNull();
    }

    @Test
    void parsesOptionalFieldsAndTrimsType() throws Exception {
        CraftRequestParser.CraftRequest req = CraftRequestParser.parse("{\"type\":\" items \",\"id\":\"minecraft:stone\",\"amount\":2,\"cpuName\":\"\",\"terminalId\":\"t1\"}");
        assertThat(req.keyType()).isEqualTo(CraftRequestParser.CraftKeyType.ITEM);
        assertThat(req.amount()).isEqualTo(2L);
        assertThat(req.cpuName()).isNull();
        assertThat(req.terminalId()).isEqualTo("t1");
    }

    @Test
    void rejectsBlankBodyInvalidJsonAndMissingFields() {
        assertThatThrownBy(() -> CraftRequestParser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CraftRequestParser.parse("{"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CraftRequestParser.parse("{\"type\":\"item\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CraftRequestParser.parse("{\"id\":\"minecraft:stone\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidTypeAndAmount() {
        assertThatThrownBy(() -> CraftRequestParser.parse("{\"type\":\"nope\",\"id\":\"minecraft:stone\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CraftRequestParser.parse("{\"type\":\"item\",\"id\":\"minecraft:stone\",\"amount\":0}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CraftRequestParser.parse("{\"type\":\"item\",\"id\":\"minecraft:stone\",\"amount\":1000001}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesFluidAmountAsBucketsAndConvertsToMilliBuckets() throws Exception {
        CraftRequestParser.CraftRequest req = CraftRequestParser.parse("{\"type\":\"fluid\",\"id\":\"minecraft:water\",\"amount\":64}");
        assertThat(req.keyType()).isEqualTo(CraftRequestParser.CraftKeyType.FLUID);
        assertThat(req.id()).isEqualTo("minecraft:water");
        assertThat(req.amount()).isEqualTo(64_000L);
    }

    @Test
    void parsesFluidWithDefaultAmountAsOneBucket() throws Exception {
        CraftRequestParser.CraftRequest req = CraftRequestParser.parse("{\"type\":\"fluid\",\"id\":\"minecraft:water\"}");
        assertThat(req.keyType()).isEqualTo(CraftRequestParser.CraftKeyType.FLUID);
        assertThat(req.amount()).isEqualTo(1_000L);
    }
}
