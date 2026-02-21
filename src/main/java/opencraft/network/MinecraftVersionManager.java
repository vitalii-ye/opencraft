package opencraft.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import opencraft.model.MinecraftVersion;
import opencraft.model.VersionResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class MinecraftVersionManager implements VersionProvider {
  private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

  private final HttpClient client;
  private final ObjectMapper mapper;

  /**
   * Creates a MinecraftVersionManager using the real HTTP client.
   */
  public MinecraftVersionManager() {
    this(HttpClient.newHttpClient());
  }

  /**
   * Creates a MinecraftVersionManager with a custom HttpClient (for testing).
   */
  public MinecraftVersionManager(HttpClient client) {
    this.client = client;
    this.mapper = new ObjectMapper();
  }

  /**
   * Fetches all available Minecraft versions from the official manifest
   */
  public List<MinecraftVersion> fetchAvailableVersions() throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(VERSION_MANIFEST_URL))
        .GET()
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    JsonNode root = mapper.readTree(response.body());

    List<MinecraftVersion> versions = new ArrayList<>();
    JsonNode versionsArray = root.path("versions");

    for (JsonNode versionNode : versionsArray) {
      String id = versionNode.get("id").asText();
      String type = versionNode.get("type").asText();
      String url = versionNode.get("url").asText();
      String releaseTime = versionNode.get("releaseTime").asText();

      versions.add(new MinecraftVersion(id, type, url, releaseTime));
    }

    return versions;
  }

  /**
   * Fetches versions with ETag support for caching.
   * If etag is provided, includes If-None-Match header.
   */
  public VersionResponse fetchAvailableVersionsWithETag(String ifNoneMatch) throws IOException, InterruptedException {
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(VERSION_MANIFEST_URL))
        .GET();

    // Add If-None-Match header if ETag is provided
    if (ifNoneMatch != null && !ifNoneMatch.isEmpty()) {
      requestBuilder.header("If-None-Match", ifNoneMatch);
    }

    HttpRequest request = requestBuilder.build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    // If 304 Not Modified, return empty list with status code
    if (response.statusCode() == 304) {
      return new VersionResponse(null, ifNoneMatch, 304);
    }

    // Parse response
    JsonNode root = mapper.readTree(response.body());
    List<MinecraftVersion> versions = new ArrayList<>();
    JsonNode versionsArray = root.path("versions");

    for (JsonNode versionNode : versionsArray) {
      String id = versionNode.get("id").asText();
      String type = versionNode.get("type").asText();
      String url = versionNode.get("url").asText();
      String releaseTime = versionNode.get("releaseTime").asText();

      versions.add(new MinecraftVersion(id, type, url, releaseTime));
    }

    // Get ETag from response headers
    String etag = response.headers().firstValue("ETag").orElse(null);

    return new VersionResponse(versions, etag, response.statusCode());
  }

}
