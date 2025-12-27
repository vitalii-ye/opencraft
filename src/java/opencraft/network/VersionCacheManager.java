package opencraft.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import opencraft.utils.MinecraftPathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages caching of Minecraft versions with TTL and ETag validation.
 * Uses a hybrid approach:
 * - Use cache for X hours without any check (TTL)
 * - After TTL, use ETag validation to check if data has changed
 */
public class VersionCacheManager {
    private static final String CACHE_FILE = "version_cache.json";
    private static final String METADATA_FILE = "version_cache_metadata.json";
    private static final long CACHE_TTL_HOURS = 6;

    private final Path cacheFilePath;
    private final Path metadataFilePath;
    private final ObjectMapper mapper;

    public VersionCacheManager() {
        Path minecraftDir = MinecraftPathResolver.getMinecraftDirectory();
        this.cacheFilePath = minecraftDir.resolve(CACHE_FILE);
        this.metadataFilePath = minecraftDir.resolve(METADATA_FILE);
        this.mapper = new ObjectMapper();
    }

    /**
     * Metadata stored alongside the cache
     */
    private static class CacheMetadata {
        private long timestamp;
        private String etag;

        public CacheMetadata() {
        }

        public CacheMetadata(long timestamp, String etag) {
            this.timestamp = timestamp;
            this.etag = etag;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public String getEtag() {
            return etag;
        }

        public void setEtag(String etag) {
            this.etag = etag;
        }
    }

    /**
     * Gets cached versions if available and valid (within TTL).
     * Returns null if cache doesn't exist or is invalid.
     */
    public List<MinecraftVersionManager.MinecraftVersion> getCachedVersions() {
        try {
            if (!Files.exists(cacheFilePath) || !Files.exists(metadataFilePath)) {
                return null;
            }

            // Read metadata
            CacheMetadata metadata = mapper.readValue(metadataFilePath.toFile(), CacheMetadata.class);

            // Check if cache is still valid (within TTL)
            long currentTime = Instant.now().toEpochMilli();
            long cacheAge = currentTime - metadata.getTimestamp();
            long ttlMillis = TimeUnit.HOURS.toMillis(CACHE_TTL_HOURS);

            if (cacheAge > ttlMillis) {
                // Cache expired, need to validate with ETag
                return null;
            }

            // Cache is valid, read and return versions
            return readVersionsFromCache();

        } catch (IOException e) {
            System.err.println("Error reading cache: " + e.getMessage());
            return null;
        }
    }

    /**
     * Reads versions from cache file
     */
    private List<MinecraftVersionManager.MinecraftVersion> readVersionsFromCache() throws IOException {
        JsonNode root = mapper.readTree(cacheFilePath.toFile());
        List<MinecraftVersionManager.MinecraftVersion> versions = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode versionNode : root) {
                String id = versionNode.get("id").asText();
                String type = versionNode.get("type").asText();
                String url = versionNode.get("url").asText();
                String releaseTime = versionNode.get("releaseTime").asText();
                versions.add(new MinecraftVersionManager.MinecraftVersion(id, type, url, releaseTime));
            }
        }

        return versions;
    }

    /**
     * Saves versions to cache along with metadata
     */
    public void saveToCache(List<MinecraftVersionManager.MinecraftVersion> versions, String etag) {
        try {
            // Ensure directory exists
            Files.createDirectories(cacheFilePath.getParent());

            // Save versions
            ArrayNode versionsArray = mapper.createArrayNode();
            for (MinecraftVersionManager.MinecraftVersion version : versions) {
                ObjectNode versionNode = mapper.createObjectNode();
                versionNode.put("id", version.getId());
                versionNode.put("type", version.getType());
                versionNode.put("url", version.getUrl());
                versionNode.put("releaseTime", version.getReleaseTime());
                versionsArray.add(versionNode);
            }

            mapper.writerWithDefaultPrettyPrinter().writeValue(cacheFilePath.toFile(), versionsArray);

            // Save metadata
            CacheMetadata metadata = new CacheMetadata(Instant.now().toEpochMilli(), etag);
            mapper.writerWithDefaultPrettyPrinter().writeValue(metadataFilePath.toFile(), metadata);

            System.out.println("Cache saved successfully with " + versions.size() + " versions");

        } catch (IOException e) {
            System.err.println("Error saving cache: " + e.getMessage());
        }
    }

    /**
     * Gets the stored ETag from metadata
     */
    public String getStoredETag() {
        try {
            if (!Files.exists(metadataFilePath)) {
                return null;
            }

            CacheMetadata metadata = mapper.readValue(metadataFilePath.toFile(), CacheMetadata.class);
            return metadata.getEtag();

        } catch (IOException e) {
            System.err.println("Error reading ETag: " + e.getMessage());
            return null;
        }
    }

    /**
     * Checks if cache needs validation (TTL expired but cache exists)
     */
    public boolean needsValidation() {
        try {
            if (!Files.exists(metadataFilePath)) {
                return false;
            }

            CacheMetadata metadata = mapper.readValue(metadataFilePath.toFile(), CacheMetadata.class);
            long currentTime = Instant.now().toEpochMilli();
            long cacheAge = currentTime - metadata.getTimestamp();
            long ttlMillis = TimeUnit.HOURS.toMillis(CACHE_TTL_HOURS);

            return cacheAge > ttlMillis;

        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Clears the cache
     */
    public void clearCache() {
        try {
            Files.deleteIfExists(cacheFilePath);
            Files.deleteIfExists(metadataFilePath);
            System.out.println("Cache cleared");
        } catch (IOException e) {
            System.err.println("Error clearing cache: " + e.getMessage());
        }
    }
}

