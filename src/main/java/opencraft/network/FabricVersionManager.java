package opencraft.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import opencraft.execution.LaunchConstants;
import opencraft.model.FabricLoaderVersion;

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

    private final HttpClient client;
    private final ObjectMapper mapper;

    /**
     * Creates a FabricVersionManager using the real HTTP client.
     */
    public FabricVersionManager() {
        this(HttpClient.newHttpClient());
    }

    /**
     * Creates a FabricVersionManager with a custom HttpClient (for testing/DI).
     */
    public FabricVersionManager(HttpClient client) {
        this.client = client;
        this.mapper = new ObjectMapper();
    }

    /**
     * Fetches all available Fabric loader versions.
     */
    public List<FabricLoaderVersion> fetchLoaderVersions() throws IOException, InterruptedException {
        String url = FABRIC_META_URL + "/v2/versions/loader";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", LaunchConstants.USER_AGENT)
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
    public FabricLoaderVersion getLatestStableLoader() throws IOException, InterruptedException {
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
     * Fetches the Fabric profile JSON for a specific game and loader version.
     * This JSON contains all the information needed to launch Fabric.
     */
    public JsonNode fetchProfileJson(String gameVersion, String loaderVersion)
            throws IOException, InterruptedException {
        String url = FABRIC_META_URL + "/v2/versions/loader/" + gameVersion + "/" + loaderVersion + "/profile/json";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", LaunchConstants.USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch Fabric profile: HTTP " + response.statusCode());
        }

        return mapper.readTree(response.body());
    }
}
