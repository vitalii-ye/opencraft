package opencraft.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import opencraft.utils.PlatformUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import opencraft.model.MinecraftVersion;
import opencraft.utils.LogHelper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class MinecraftDownloader {

  private final HttpClient client;
  private final ObjectMapper mapper;

  /**
   * Creates a MinecraftDownloader using the real HTTP client.
   */
  public MinecraftDownloader() {
    this(HttpClient.newHttpClient());
  }

  /**
   * Creates a MinecraftDownloader with a custom HttpClient (for testing/DI).
   */
  public MinecraftDownloader(HttpClient client) {
    this.client = client;
    this.mapper = new ObjectMapper();
  }

  public void downloadMinecraft(String manifestUrl, Path baseDir, String versionId)
      throws IOException, InterruptedException {
      downloadMinecraft(manifestUrl, baseDir, versionId, null);
  }

  public void downloadMinecraft(String manifestUrl, Path baseDir, String versionId, Consumer<String> logger)
      throws IOException, InterruptedException {
    // Load manifest JSON
    JsonNode root = fetchJson(manifestUrl);

    // Save the manifest JSON for the launcher
    Path manifestPath = baseDir.resolve("versions/" + versionId + "/" + versionId + ".json");
    Files.createDirectories(manifestPath.getParent());
    mapper.writeValue(manifestPath.toFile(), root);
    LogHelper.log(logger, "Saved manifest: " + manifestPath);

    // Download main client JAR
    JsonNode downloads = root.path("downloads").path("client");
    if (!downloads.isMissingNode()) {
      String jarUrl = downloads.get("url").asText();
      Path jarPath = baseDir.resolve("versions/" + versionId + "/" + versionId + ".jar");
      downloadFile(jarUrl, jarPath, logger);
    }

    // Collect library paths for classpath
    List<String> classpathEntries = new ArrayList<>();

    // Download libraries
    JsonNode libraries = root.path("libraries");
    for (JsonNode lib : libraries) {
      // Check if this library is allowed for current OS/arch (same logic as
      // MinecraftLauncher)
      if (!PlatformUtils.isLibraryAllowed(lib))
        continue;

      JsonNode downloadsNode = lib.path("downloads").path("artifact");
      if (!downloadsNode.isMissingNode()) {
        String libUrl = downloadsNode.get("url").asText();
        String path = downloadsNode.get("path").asText();
        Path libPath = baseDir.resolve("libraries").resolve(path);
        downloadFile(libUrl, libPath, logger);
        classpathEntries.add(libPath.toString());
      }

      // Some libraries have classifiers (native files per OS)
      JsonNode classifiers = lib.path("downloads").path("classifiers");
      if (!classifiers.isMissingNode()) {
        // Download all native libraries for different platforms
        Iterator<String> keys = classifiers.fieldNames();
        while (keys.hasNext()) {
          String key = keys.next();
          JsonNode classifier = classifiers.get(key);
          String libUrl = classifier.get("url").asText();
          String path = classifier.get("path").asText();
          Path libPath = baseDir.resolve("libraries").resolve(path);
          downloadFile(libUrl, libPath, logger);
        }

        // Only add the current platform's native library to classpath
        String nativeKey = PlatformUtils.getNativeClassifier();
        if (nativeKey != null && classifiers.has(nativeKey)) {
          JsonNode nativeLib = classifiers.get(nativeKey);
          String path = nativeLib.get("path").asText();
          Path libPath = baseDir.resolve("libraries").resolve(path);
          classpathEntries.add(libPath.toString());
        }
      }
    }

    // Create libraries.txt with the classpath
    Path librariesTxtPath = baseDir.resolve("libraries_" + versionId + ".txt");
    String classpathString = String.join(File.pathSeparator, classpathEntries);
    Files.writeString(librariesTxtPath, classpathString, StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);
    LogHelper.log(logger, "Created libraries_" + versionId + ".txt with " + classpathEntries.size() + " entries");
    LogHelper.log(logger, "Using path separator: " + File.pathSeparator);

    // Download assets index
    JsonNode assetIndex = root.path("assetIndex");
    if (!assetIndex.isMissingNode()) {
      String assetId = assetIndex.get("id").asText();
      String assetUrl = assetIndex.get("url").asText();
      Path assetIndexPath = baseDir.resolve("assets/indexes/" + assetId + ".json");
      downloadFile(assetUrl, assetIndexPath, logger);

      // Download actual asset objects
      downloadAssets(assetIndexPath, baseDir, logger);
    }

    LogHelper.log(logger, "All required files downloaded into: " + baseDir.toAbsolutePath());
  }

  /**
   * Downloads a specific Minecraft version using a MinecraftVersion object
   */
  public void downloadMinecraft(MinecraftVersion version, Path baseDir)
      throws IOException, InterruptedException {
    downloadMinecraft(version.getUrl(), baseDir, version.getId(), null);
  }

  public void downloadMinecraft(MinecraftVersion version, Path baseDir, Consumer<String> logger)
      throws IOException, InterruptedException {
    downloadMinecraft(version.getUrl(), baseDir, version.getId(), logger);
  }


  private JsonNode fetchJson(String url) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body());
  }

  private void downloadFile(String url, Path dest, Consumer<String> logger) throws IOException, InterruptedException {
    FileDownloader.download(url, dest);
    LogHelper.log(logger, "Downloaded: " + dest);
  }

  private void downloadAssets(Path assetIndexPath, Path baseDir, Consumer<String> logger) throws IOException, InterruptedException {
    LogHelper.log(logger, "Downloading assets...");
    JsonNode assetIndex = mapper.readTree(assetIndexPath.toFile());
    JsonNode objects = assetIndex.path("objects");

    int totalAssets = objects.size();
    int downloaded = 0;

    Iterator<String> keys = objects.fieldNames();
    while (keys.hasNext()) {
      String assetName = keys.next();
      JsonNode assetInfo = objects.get(assetName);
      String hash = assetInfo.get("hash").asText();

      // Assets are stored in subfolders based on the first 2 characters of the hash
      String hashPrefix = hash.substring(0, 2);
      Path assetPath = baseDir.resolve("assets/objects/" + hashPrefix + "/" + hash);

      if (!Files.exists(assetPath)) {
        String assetUrl = "https://resources.download.minecraft.net/" + hashPrefix + "/" + hash;
        downloadFile(assetUrl, assetPath, logger);
      }

      downloaded++;
      if (downloaded % 100 == 0) {
        LogHelper.log(logger, "Downloaded " + downloaded + "/" + totalAssets + " assets...");
      }
    }

    LogHelper.log(logger, "Downloaded all " + totalAssets + " assets!");
  }

}
