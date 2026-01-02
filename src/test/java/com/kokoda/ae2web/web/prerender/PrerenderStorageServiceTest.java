package com.kokoda.ae2web.web.prerender;

import com.kokoda.ae2web.config.AE2WebConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrerenderStorageServiceTest {
    private PrerenderStorageService storage;

    @BeforeEach
    void setUp() {
        storage = PrerenderStorageService.getInstance();
        storage.initialize();
        storage.clearAll();
    }

    @Test
    void saveIconRejectsInvalidPngMagic() {
        String iconKey = "minecraft:stone@h1_test";
        byte[] notPng = "nope".getBytes();

        assertThat(storage.saveIcon(iconKey, notPng, null, null)).isFalse();
        assertThat(storage.hasIcon(iconKey)).isFalse();
        assertThat(storage.getIcon(iconKey)).isNull();
    }

    @Test
    void saveIconRejectsTooLargePng() {
        String iconKey = "minecraft:stone@h1_big";

        int max = AE2WebConfig.getPrerenderMaxUploadSize();
        byte[] png = new byte[max + 1];
        System.arraycopy(minimalPngHeader(), 0, png, 0, minimalPngHeader().length);

        assertThat(storage.saveIcon(iconKey, png, null, null)).isFalse();
        assertThat(storage.hasIcon(iconKey)).isFalse();
    }

    @Test
    void saveIconStoresIconAndMetadata() {
        String iconKey = "minecraft:stone@h1_ok";
        byte[] png = minimalPngHeader();

        assertThat(storage.saveIcon(iconKey, png, "Stone", List.of("Stone"))).isTrue();

        assertThat(storage.hasIcon(iconKey)).isTrue();
        assertThat(storage.getIcon(iconKey)).isEqualTo(png);
        assertThat(storage.getTranslation(iconKey)).isEqualTo("Stone");
        assertThat(storage.getTooltip(iconKey)).containsExactly("Stone");
    }

    @Test
    void markSeenPersistsToSeenJsonOnShutdownMaintenance() throws Exception {
        String iconKey = "minecraft:stone@h1_seen";
        storage.markSeen(List.of(iconKey));
        storage.shutdownMaintenance();

        String root = System.getProperty("ae2web.prerender.storageRoot");
        assertThat(root).isNotBlank();
        Path seenJson = Path.of(root).resolve("seen.json");
        assertThat(seenJson).exists();
        assertThat(Files.readString(seenJson)).contains(iconKey);
    }

    @Test
    void claimItemsForRenderingTracksLocksPerClient() {
        List<String> ids = List.of("minecraft:stone@h1_a", "minecraft:dirt@h1_b");

        var first = storage.claimItemsForRendering(ids, "c1");
        assertThat(first.claimed()).containsExactlyElementsOf(ids);
        assertThat(first.alreadyRendered()).isZero();
        assertThat(first.lockedBySelf()).isZero();
        assertThat(first.lockedByOthers()).isZero();

        var secondSameClient = storage.claimItemsForRendering(ids, "c1");
        assertThat(secondSameClient.claimed()).containsExactlyElementsOf(ids);
        assertThat(secondSameClient.lockedBySelf()).isEqualTo(ids.size());
        assertThat(secondSameClient.lockedByOthers()).isZero();

        var otherClient = storage.claimItemsForRendering(ids, "c2");
        assertThat(otherClient.claimed()).isEmpty();
        assertThat(otherClient.lockedByOthers()).isEqualTo(ids.size());
    }

    @Test
    void saveIconMakesItemAppearAlreadyRenderedForClaiming() {
        String iconKey = "minecraft:stone@h1_rendered";
        assertThat(storage.saveIcon(iconKey, minimalPngHeader(), null, null)).isTrue();

        var claim = storage.claimItemsForRendering(List.of(iconKey), "c1");
        assertThat(claim.claimed()).isEmpty();
        assertThat(claim.alreadyRendered()).isEqualTo(1);
    }

    @Test
    void saveIconAcceptsPathsWithSlashes() {
        String iconKey = "minecraft:foo/bar@h1_ok";
        assertThat(storage.saveIcon(iconKey, minimalPngHeader(), null, null)).isTrue();
        assertThat(storage.hasIcon(iconKey)).isTrue();
    }

    @Test
    void saveIconRejectsInvalidIconKeyFormat() {
        assertThat(storage.saveIcon("minecraft:stone", minimalPngHeader(), null, null)).isFalse();
    }

    @Test
    void base64RoundTripSanityForFixtures() {
        byte[] png = minimalPngHeader();
        String encoded = Base64.getEncoder().encodeToString(png);
        assertThat(Base64.getDecoder().decode(encoded)).isEqualTo(png);
    }

    private static byte[] minimalPngHeader() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
    }
}

