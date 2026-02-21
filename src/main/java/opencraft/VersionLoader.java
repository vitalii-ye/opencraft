package opencraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import opencraft.model.MinecraftVersion;
import opencraft.model.VersionResponse;
import opencraft.network.VersionProvider;
import opencraft.network.VersionCacheManager;
import opencraft.utils.MinecraftPathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles the business logic of loading Minecraft versions: reading from cache,
 * validating with ETag, and fetching from the remote manifest in the background.
 * UI concerns (populating combo boxes, showing dialogs) are kept in the caller.
 */
public class VersionLoader {

    private final VersionCacheManager versionCacheManager;
    private final VersionProvider versionManager;

    public VersionLoader(VersionCacheManager versionCacheManager,
                         VersionProvider versionManager) {
        this.versionCacheManager = versionCacheManager;
        this.versionManager = versionManager;
    }

    /**
     * Loads versions using a hybrid strategy:
     * 1. If cache is fresh (within TTL), return it immediately via {@code onVersionsReady}.
     * 2. If cache is stale, provide expired data immediately (if available) then
     *    trigger a background fetch; the result is also delivered via {@code onVersionsReady}.
     *
     * @param onVersionsReady called (possibly multiple times) with the latest version list
     * @param onError         called if the background fetch fails with no cached fallback
     */
    public void loadVersions(Consumer<List<MinecraftVersion>> onVersionsReady,
                             Consumer<Exception> onError) {
        // 1. Try fresh cache first
        List<MinecraftVersion> cachedVersions = versionCacheManager.getCachedVersions();
        if (cachedVersions != null && !cachedVersions.isEmpty()) {
            System.out.println("Loading versions from cache (" + cachedVersions.size() + " versions)");
            onVersionsReady.accept(cachedVersions);
            // Cache is fresh — no background fetch needed
            return;
        }

        // 2. Cache is expired or absent — serve stale data while validating
        if (versionCacheManager.needsValidation()) {
            try {
                List<MinecraftVersion> stale = readCacheDirectly();
                if (stale != null && !stale.isEmpty()) {
                    System.out.println("Loading expired cache while validating (" + stale.size() + " versions)");
                    onVersionsReady.accept(stale);
                }
            } catch (Exception e) {
                System.err.println("Error loading expired cache: " + e.getMessage());
            }
        }

        // 3. Fetch in background; result delivered via callback
        fetchVersionsInBackground(onVersionsReady, onError);
    }

    /**
     * Reads the raw cache file directly, ignoring TTL metadata.
     * Returns {@code null} if the file does not exist or cannot be parsed.
     */
    public List<MinecraftVersion> readCacheDirectly() {
        try {
            Path cacheFile = MinecraftPathResolver.getMinecraftDirectory().resolve("version_cache.json");
            if (!Files.exists(cacheFile)) {
                return null;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(cacheFile.toFile());

            List<MinecraftVersion> versions = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode versionNode : root) {
                    String id = versionNode.get("id").asText();
                    String type = versionNode.get("type").asText();
                    String url = versionNode.get("url").asText();
                    String releaseTime = versionNode.get("releaseTime").asText();
                    versions.add(new MinecraftVersion(id, type, url, releaseTime));
                }
            }

            return versions;
        } catch (Exception e) {
            System.err.println("Error reading cache directly: " + e.getMessage());
            return null;
        }
    }

    /**
     * Fetches versions from the remote manifest on a background thread.
     * Uses ETag validation to avoid unnecessary downloads.
     * Delivers the result (or a cached fallback) via {@code onVersionsReady}.
     * On total failure (no network, no cache), calls {@code onError}.
     */
    public void fetchVersionsInBackground(Consumer<List<MinecraftVersion>> onVersionsReady,
                                          Consumer<Exception> onError) {
        Thread thread = new Thread(() -> {
            try {
                String storedETag = versionCacheManager.getStoredETag();

                VersionResponse response = versionManager.fetchAvailableVersionsWithETag(storedETag);

                List<MinecraftVersion> result;
                if (response.isNotModified()) {
                    System.out.println("Versions not modified (304), using cache");
                    List<MinecraftVersion> cached = readCacheDirectly();
                    if (cached != null) {
                        versionCacheManager.saveToCache(cached, storedETag);
                    }
                    result = cached;
                } else {
                    List<MinecraftVersion> allVersions = response.getVersions();
                    List<MinecraftVersion> releaseVersions = new ArrayList<>();
                    for (MinecraftVersion v : allVersions) {
                        if (v.isRelease()) {
                            releaseVersions.add(v);
                        }
                    }
                    versionCacheManager.saveToCache(releaseVersions, response.getEtag());
                    System.out.println("Fetched " + releaseVersions.size() + " release versions from server");
                    result = releaseVersions;
                }

                if (result != null && !result.isEmpty()) {
                    onVersionsReady.accept(result);
                }

            } catch (IOException | InterruptedException e) {
                System.err.println("Error fetching versions: " + e.getMessage());
                e.printStackTrace();

                // Fallback to cache
                List<MinecraftVersion> cached = readCacheDirectly();
                if (cached != null && !cached.isEmpty()) {
                    System.out.println("Using cached versions as fallback");
                    onVersionsReady.accept(cached);
                } else {
                    onError.accept(e);
                }

                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "VersionLoader-Background");
        thread.setDaemon(true);
        thread.start();
    }
}
