package opencraft.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages Fabric mod loader versions by fetching data from the Fabric Meta API.
 * API Documentation: https://meta.fabricmc.net/
 */
public class FabricVersionManager {

    private static final String FABRIC_META_URL = "https://meta.fabricmc.net";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Represents a Fabric loader version.
     */
    public static class FabricLoaderVersion {
        private final String version;
        private final boolean stable;

        public FabricLoaderVersion(String version, boolean stable) {
            this.version = version;
            this.stable = stable;
        }

        public String getVersion() {
            return version;
        }

        public boolean isStable() {
            return stable;
        }

        @Override
        public String toString() {
            return version + (stable ? "" : " (unstable)");
        }
    }

    /**
     * Represents a game version that supports Fabric.
     */
    public static class FabricGameVersion {
        private final String version;
        private final boolean stable;

        public FabricGameVersion(String version, boolean stable) {
            this.version = version;
            this.stable = stable;
        }

        public String getVersion() {
            return version;
        }

        public boolean isStable() {
            return stable;
        }
    }

    /**
     * Represents a complete Fabric version (game version + loader version).
     */
    public static class FabricVersion {
        private final String gameVersion;
        private final String loaderVersion;
        private final String displayName;
        private final String versionId;

        public FabricVersion(String gameVersion, String loaderVersion) {
            this.gameVersion = gameVersion;
            this.loaderVersion = loaderVersion;
            this.displayName = gameVersion + " [Fabric]";
            this.versionId = "fabric-loader-" + loaderVersion + "-" + gameVersion;
        }

        public String getGameVersion() {
            return gameVersion;
        }

        public String getLoaderVersion() {
            return loaderVersion;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getVersionId() {
            return versionId;
        }

        /**
         * Returns the URL to fetch the complete profile JSON for this Fabric version.
         */
        public String getProfileUrl() {
            return FABRIC_META_URL + "/v2/versions/loader/" + gameVersion + "/" + loaderVersion + "/profile/json";
        }
    }

    /**
     * Fetches all available Fabric loader versions.
     */
    public static List<FabricLoaderVersion> fetchLoaderVersions() throws IOException, InterruptedException {
        String url = FABRIC_META_URL + "/v2/versions/loader";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "OpenCraft-Launcher/1.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch Fabric loader versions: HTTP " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        List<FabricLoaderVersion> versions = new ArrayList<>();

        for (JsonNode node : root) {
            String version = node.path("version").asText();
            boolean stable = node.path("stable").asBoolean(true);
            versions.add(new FabricLoaderVersion(version, stable));
        }

        return versions;
    }

    /**
     * Fetches the latest stable Fabric loader version.
     */
    public static FabricLoaderVersion getLatestStableLoader() throws IOException, InterruptedException {
        List<FabricLoaderVersion> versions = fetchLoaderVersions();
        for (FabricLoaderVersion version : versions) {
            if (version.isStable()) {
                return version;
            }
        }
        // Fallback to first version if no stable found
        return versions.isEmpty() ? null : versions.get(0);
    }

    /**
     * Fetches all game versions that support Fabric.
     */
    public static List<FabricGameVersion> fetchSupportedGameVersions() throws IOException, InterruptedException {
        String url = FABRIC_META_URL + "/v2/versions/game";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "OpenCraft-Launcher/1.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch Fabric game versions: HTTP " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        List<FabricGameVersion> versions = new ArrayList<>();

        for (JsonNode node : root) {
            String version = node.path("version").asText();
            boolean stable = node.path("stable").asBoolean(true);
            versions.add(new FabricGameVersion(version, stable));
        }

        return versions;
    }

    /**
     * Checks if a specific game version supports Fabric.
     */
    public static boolean isGameVersionSupported(String gameVersion) throws IOException, InterruptedException {
        String url = FABRIC_META_URL + "/v2/versions/loader/" + gameVersion;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "OpenCraft-Launcher/1.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return false;
        }

        JsonNode root = mapper.readTree(response.body());
        return root.isArray() && root.size() > 0;
    }

    /**
     * Creates a FabricVersion for a given game version using the latest stable loader.
     */
    public static FabricVersion createFabricVersion(String gameVersion) throws IOException, InterruptedException {
        FabricLoaderVersion loader = getLatestStableLoader();
        if (loader == null) {
            throw new IOException("No Fabric loader versions available");
        }
        return new FabricVersion(gameVersion, loader.getVersion());
    }

    /**
     * Creates a FabricVersion for a given game version and specific loader version.
     */
    public static FabricVersion createFabricVersion(String gameVersion, String loaderVersion) {
        return new FabricVersion(gameVersion, loaderVersion);
    }

    /**
     * Fetches available Fabric loader versions for a specific game version.
     */
    public static List<FabricLoaderVersion> fetchLoadersForGameVersion(String gameVersion) 
            throws IOException, InterruptedException {
        String url = FABRIC_META_URL + "/v2/versions/loader/" + gameVersion;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "OpenCraft-Launcher/1.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch Fabric loaders for " + gameVersion + ": HTTP " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        List<FabricLoaderVersion> versions = new ArrayList<>();

        for (JsonNode node : root) {
            JsonNode loaderNode = node.path("loader");
            String version = loaderNode.path("version").asText();
            boolean stable = loaderNode.path("stable").asBoolean(true);
            versions.add(new FabricLoaderVersion(version, stable));
        }

        return versions;
    }

    /**
     * Fetches the Fabric profile JSON for a specific game and loader version.
     * This JSON contains all the information needed to launch Fabric.
     */
    public static JsonNode fetchProfileJson(String gameVersion, String loaderVersion) 
            throws IOException, InterruptedException {
        String url = FABRIC_META_URL + "/v2/versions/loader/" + gameVersion + "/" + loaderVersion + "/profile/json";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "OpenCraft-Launcher/1.0")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch Fabric profile: HTTP " + response.statusCode());
        }

        return mapper.readTree(response.body());
    }
}
