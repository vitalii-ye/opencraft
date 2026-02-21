package opencraft.network;

import opencraft.network.MinecraftVersionManager.MinecraftVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VersionCacheManagerTest {

    @Test
    void getCachedVersionsReturnsNullWhenNoCache(@TempDir Path tempDir) {
        VersionCacheManager cache = new VersionCacheManager(tempDir);
        assertNull(cache.getCachedVersions());
    }

    @Test
    void saveAndRetrieve(@TempDir Path tempDir) {
        VersionCacheManager cache = new VersionCacheManager(tempDir);

        List<MinecraftVersion> versions = Arrays.asList(
                new MinecraftVersion("1.21", "release", "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00"),
                new MinecraftVersion("1.20.6", "release", "https://example.com/1.20.6.json", "2024-04-29T15:14:20+00:00")
        );

        cache.saveToCache(versions, "test-etag-123");

        List<MinecraftVersion> retrieved = cache.getCachedVersions();
        assertNotNull(retrieved);
        assertEquals(2, retrieved.size());
        assertEquals("1.21", retrieved.get(0).getId());
        assertEquals("release", retrieved.get(0).getType());
        assertEquals("1.20.6", retrieved.get(1).getId());
    }

    @Test
    void getStoredETag(@TempDir Path tempDir) {
        VersionCacheManager cache = new VersionCacheManager(tempDir);

        List<MinecraftVersion> versions = Arrays.asList(
                new MinecraftVersion("1.21", "release", "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00")
        );

        cache.saveToCache(versions, "etag-abc-def");

        assertEquals("etag-abc-def", cache.getStoredETag());
    }

    @Test
    void getStoredETagReturnsNullWhenNoMetadata(@TempDir Path tempDir) {
        VersionCacheManager cache = new VersionCacheManager(tempDir);
        assertNull(cache.getStoredETag());
    }

    @Test
    void needsValidationReturnsFalseWhenNoCache(@TempDir Path tempDir) {
        VersionCacheManager cache = new VersionCacheManager(tempDir);
        assertFalse(cache.needsValidation());
    }

    @Test
    void needsValidationReturnsFalseWhenCacheFresh(@TempDir Path tempDir) {
        VersionCacheManager cache = new VersionCacheManager(tempDir);

        List<MinecraftVersion> versions = Arrays.asList(
                new MinecraftVersion("1.21", "release", "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00")
        );

        cache.saveToCache(versions, "etag");
        assertFalse(cache.needsValidation());
    }

    @Test
    void clearCache(@TempDir Path tempDir) {
        VersionCacheManager cache = new VersionCacheManager(tempDir);

        List<MinecraftVersion> versions = Arrays.asList(
                new MinecraftVersion("1.21", "release", "https://example.com/1.21.json", "2024-06-13T08:32:38+00:00")
        );

        cache.saveToCache(versions, "etag");
        assertNotNull(cache.getCachedVersions());

        cache.clearCache();
        assertNull(cache.getCachedVersions());
        assertNull(cache.getStoredETag());
    }

    @Test
    void corruptJsonReturnsNull(@TempDir Path tempDir) throws IOException {
        // Create corrupt cache files
        Files.writeString(tempDir.resolve("version_cache.json"), "not valid json {{{");
        Files.writeString(tempDir.resolve("version_cache_metadata.json"), "also corrupt");

        VersionCacheManager cache = new VersionCacheManager(tempDir);
        assertNull(cache.getCachedVersions());
    }

    @Test
    void savedVersionsPreserveAllFields(@TempDir Path tempDir) {
        VersionCacheManager cache = new VersionCacheManager(tempDir);

        MinecraftVersion version = new MinecraftVersion(
                "1.21", "release",
                "https://piston-meta.mojang.com/v1/packages/abc/1.21.json",
                "2024-06-13T08:32:38+00:00"
        );

        cache.saveToCache(Arrays.asList(version), "etag-123");

        List<MinecraftVersion> retrieved = cache.getCachedVersions();
        assertNotNull(retrieved);
        assertEquals(1, retrieved.size());

        MinecraftVersion v = retrieved.get(0);
        assertEquals("1.21", v.getId());
        assertEquals("release", v.getType());
        assertEquals("https://piston-meta.mojang.com/v1/packages/abc/1.21.json", v.getUrl());
        assertEquals("2024-06-13T08:32:38+00:00", v.getReleaseTime());
    }

    @Test
    void emptyVersionListSavesAndLoads(@TempDir Path tempDir) {
        VersionCacheManager cache = new VersionCacheManager(tempDir);

        cache.saveToCache(Arrays.asList(), "empty-etag");

        List<MinecraftVersion> retrieved = cache.getCachedVersions();
        assertNotNull(retrieved);
        assertTrue(retrieved.isEmpty());
    }
}
