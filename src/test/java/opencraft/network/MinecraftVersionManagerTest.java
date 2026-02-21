package opencraft.network;

import opencraft.model.MinecraftVersion;
import opencraft.model.VersionResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftVersionManagerTest {

    // --- MinecraftVersion vanilla constructor tests ---

    @Test
    void vanillaVersionHasCorrectId() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00");
        assertEquals("1.21", version.getId());
    }

    @Test
    void vanillaVersionIsNotFabric() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00");
        assertFalse(version.isFabric());
        assertNull(version.getFabricLoaderVersion());
    }

    @Test
    void vanillaVersionDisplayNameIsId() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00");
        assertEquals("1.21", version.getDisplayName());
    }

    @Test
    void vanillaVersionBaseGameVersionEqualsId() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00");
        assertEquals("1.21", version.getBaseGameVersion());
    }

    @Test
    void releaseVersionIsRelease() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00");
        assertTrue(version.isRelease());
        assertFalse(version.isSnapshot());
    }

    @Test
    void snapshotVersionIsSnapshot() {
        MinecraftVersion version = new MinecraftVersion("24w21a", "snapshot",
                "https://example.com/24w21a.json", "2024-05-22T12:00:00+00:00");
        assertTrue(version.isSnapshot());
        assertFalse(version.isRelease());
    }

    // --- MinecraftVersion Fabric constructor tests ---

    @Test
    void fabricVersionHasCompositeId() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00", "0.15.11");
        assertEquals("fabric-loader-0.15.11-1.21", version.getId());
    }

    @Test
    void fabricVersionIsFabric() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00", "0.15.11");
        assertTrue(version.isFabric());
        assertEquals("0.15.11", version.getFabricLoaderVersion());
    }

    @Test
    void fabricVersionDisplayNameShowsFabricTag() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00", "0.15.11");
        assertEquals("1.21 [Fabric]", version.getDisplayName());
    }

    @Test
    void fabricVersionBaseGameVersionIsOriginal() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00", "0.15.11");
        assertEquals("1.21", version.getBaseGameVersion());
    }

    // --- toFabricVersion tests ---

    @Test
    void toFabricVersionCreatesCorrectVersion() {
        MinecraftVersion vanilla = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00");

        MinecraftVersion fabric = vanilla.toFabricVersion("0.15.11");

        assertTrue(fabric.isFabric());
        assertEquals("1.21", fabric.getBaseGameVersion());
        assertEquals("0.15.11", fabric.getFabricLoaderVersion());
        assertEquals("fabric-loader-0.15.11-1.21", fabric.getId());
        assertEquals("1.21 [Fabric]", fabric.getDisplayName());
    }

    // --- toString tests ---

    @Test
    void toStringReturnsDisplayName() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00");
        assertEquals("1.21", version.toString());
    }

    @Test
    void toStringReturnsFabricDisplayName() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00", "0.15.11");
        assertEquals("1.21 [Fabric]", version.toString());
    }

    // --- Accessors tests ---

    @Test
    void gettersReturnCorrectValues() {
        MinecraftVersion version = new MinecraftVersion("1.21", "release",
                "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00");
        assertEquals("release", version.getType());
        assertEquals("https://example.com/1.21.json", version.getUrl());
        assertEquals("2024-06-13T08:32:38+00:00", version.getReleaseTime());
    }

    // --- VersionResponse tests ---

    @Test
    void versionResponseIsNotModifiedWith304() {
        VersionResponse response = new VersionResponse(null, "etag-123", 304);
        assertTrue(response.isNotModified());
        assertNull(response.getVersions());
        assertEquals("etag-123", response.getEtag());
    }

    @Test
    void versionResponseIsNotModifiedFalseWith200() {
        VersionResponse response = new VersionResponse(
                Arrays.asList(new MinecraftVersion("1.21", "release", "url", "time")),
                "etag-456", 200);
        assertFalse(response.isNotModified());
        assertEquals(1, response.getVersions().size());
        assertEquals(200, response.getStatusCode());
    }

    @Test
    void versionResponseEmptyVersionsList() {
        VersionResponse response = new VersionResponse(Collections.emptyList(), null, 200);
        assertFalse(response.isNotModified());
        assertTrue(response.getVersions().isEmpty());
        assertNull(response.getEtag());
    }
}
