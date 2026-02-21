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

public class MinecraftVersionManager {
  private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
  private static final HttpClient client = HttpClient.newHttpClient();
  private static final ObjectMapper mapper = new ObjectMapper();

  /**
   * Represents a Minecraft version (vanilla or Fabric)
   */
  public static class MinecraftVersion {
    private final String id;
    private final String type;
    private final String url;
    private final String releaseTime;
    private final boolean fabric;
    private final String fabricLoaderVersion;
    private final String baseGameVersion;

    public MinecraftVersion(String id, String type, String url, String releaseTime) {
      this.id = id;
      this.type = type;
      this.url = url;
      this.releaseTime = releaseTime;
      this.fabric = false;
      this.fabricLoaderVersion = null;
      this.baseGameVersion = id;
    }

    /**
     * Creates a Fabric version based on a vanilla version.
     */
    public MinecraftVersion(String id, String type, String url, String releaseTime, 
                           String fabricLoaderVersion) {
      this.baseGameVersion = id;
      this.id = "fabric-loader-" + fabricLoaderVersion + "-" + id;
      this.type = type;
      this.url = url;
      this.releaseTime = releaseTime;
      this.fabric = true;
      this.fabricLoaderVersion = fabricLoaderVersion;
    }

    public String getId() {
      return id;
    }

    /**
     * Returns the display name for the UI (e.g., "1.21 [Fabric]").
     */
    public String getDisplayName() {
      if (fabric) {
        return baseGameVersion + " [Fabric]";
      }
      return id;
    }

    public String getType() {
      return type;
    }

    public String getUrl() {
      return url;
    }

    public String getReleaseTime() {
      return releaseTime;
    }

    public boolean isFabric() {
      return fabric;
    }

    public String getFabricLoaderVersion() {
      return fabricLoaderVersion;
    }

    /**
     * Returns the base game version (e.g., "1.21" for both vanilla and Fabric).
     */
    public String getBaseGameVersion() {
      return baseGameVersion;
    }

    @Override
    public String toString() {
      return getDisplayName();
    }

    public boolean isRelease() {
      return "release".equals(type);
    }

    public boolean isSnapshot() {
      return "snapshot".equals(type);
    }

    /**
     * Creates a Fabric version from an existing vanilla version.
     */
    public MinecraftVersion toFabricVersion(String loaderVersion) {
      return new MinecraftVersion(this.baseGameVersion, this.type, this.url, 
                                  this.releaseTime, loaderVersion);
    }
  }

  /**
   * Response containing versions and ETag header
   */
  public static class VersionResponse {
    private final List<MinecraftVersion> versions;
    private final String etag;
    private final int statusCode;

    public VersionResponse(List<MinecraftVersion> versions, String etag, int statusCode) {
      this.versions = versions;
      this.etag = etag;
      this.statusCode = statusCode;
    }

    public List<MinecraftVersion> getVersions() {
      return versions;
    }

    public String getEtag() {
      return etag;
    }

    public int getStatusCode() {
      return statusCode;
    }

    public boolean isNotModified() {
      return statusCode == 304;
    }
  }

  /**
   * Fetches all available Minecraft versions from the official manifest
   */
  public static List<MinecraftVersion> fetchAvailableVersions() throws IOException, InterruptedException {
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
   * Fetches versions with ETag support for caching
   * If etag is provided, includes If-None-Match header
   */
  public static VersionResponse fetchAvailableVersionsWithETag(String ifNoneMatch) throws IOException, InterruptedException {
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

  /**
   * Fetches only release versions (no snapshots or betas)
   */
  public static List<MinecraftVersion> fetchReleaseVersions() throws IOException, InterruptedException {
    List<MinecraftVersion> allVersions = fetchAvailableVersions();
    List<MinecraftVersion> releaseVersions = new ArrayList<>();

    for (MinecraftVersion version : allVersions) {
      if (version.isRelease()) {
        releaseVersions.add(version);
      }
    }

    return releaseVersions;
  }

  /**
   * Gets the latest release version
   */
  public static MinecraftVersion getLatestRelease() throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(VERSION_MANIFEST_URL))
        .GET()
        .build();

    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    JsonNode root = mapper.readTree(response.body());

    JsonNode latest = root.path("latest");
    String latestReleaseId = latest.get("release").asText();

    // Find the version details for the latest release
    JsonNode versionsArray = root.path("versions");
    for (JsonNode versionNode : versionsArray) {
      String id = versionNode.get("id").asText();
      if (latestReleaseId.equals(id)) {
        String type = versionNode.get("type").asText();
        String url = versionNode.get("url").asText();
        String releaseTime = versionNode.get("releaseTime").asText();
        return new MinecraftVersion(id, type, url, releaseTime);
      }
    }

    throw new RuntimeException("Could not find latest release version: " + latestReleaseId);
  }
}
