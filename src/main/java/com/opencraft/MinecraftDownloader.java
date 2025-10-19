package com.opencraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MinecraftDownloader {

  private static final HttpClient client = HttpClient.newHttpClient();
  private static final ObjectMapper mapper = new ObjectMapper();

  public static void downloadMinecraft(String manifestUrl, Path baseDir, String versionId) throws IOException, InterruptedException {
    // Load manifest JSON
    JsonNode root = fetchJson(manifestUrl);

    // Save the manifest JSON for the launcher
    Path manifestPath = baseDir.resolve("versions/" + versionId + "/" + versionId + ".json");
    Files.createDirectories(manifestPath.getParent());
    mapper.writeValue(manifestPath.toFile(), root);
    System.out.println("Saved manifest: " + manifestPath);

    // Download main client JAR
    JsonNode downloads = root.path("downloads").path("client");
    if (!downloads.isMissingNode()) {
      String jarUrl = downloads.get("url").asText();
      Path jarPath = baseDir.resolve("versions/" + versionId + "/" + versionId + ".jar");
      downloadFile(jarUrl, jarPath);
    }

    // Collect library paths for classpath
    List<String> classpathEntries = new ArrayList<>();

    // Download libraries
    JsonNode libraries = root.path("libraries");
    for (JsonNode lib : libraries) {
      // Check if this library is allowed for current OS/arch (same logic as
      // MinecraftLauncher)
      if (!isLibraryAllowed(lib))
        continue;

      JsonNode downloadsNode = lib.path("downloads").path("artifact");
      if (!downloadsNode.isMissingNode()) {
        String libUrl = downloadsNode.get("url").asText();
        String path = downloadsNode.get("path").asText();
        Path libPath = baseDir.resolve("libraries").resolve(path);
        downloadFile(libUrl, libPath);
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
          downloadFile(libUrl, libPath);
        }

        // Only add the current platform's native library to classpath
        String nativeKey = getNativeClassifier();
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
    System.out.println("Created libraries_" + versionId + ".txt with " + classpathEntries.size() + " entries");
    System.out.println("Using path separator: " + File.pathSeparator);

    // Download assets index
    JsonNode assetIndex = root.path("assetIndex");
    if (!assetIndex.isMissingNode()) {
      String assetId = assetIndex.get("id").asText();
      String assetUrl = assetIndex.get("url").asText();
      Path assetIndexPath = baseDir.resolve("assets/indexes/" + assetId + ".json");
      downloadFile(assetUrl, assetIndexPath);

      // Download actual asset objects
      downloadAssets(assetIndexPath, baseDir);
    }

    System.out.println("All required files downloaded into: " + baseDir.toAbsolutePath());
  }

  /**
   * Downloads a specific Minecraft version using a MinecraftVersion object
   */
  public static void downloadMinecraft(MinecraftVersionManager.MinecraftVersion version, Path baseDir) throws IOException, InterruptedException {
    downloadMinecraft(version.getUrl(), baseDir, version.getId());
  }

  /**
   * Backward compatibility method - downloads version 1.21
   */
  public static void downloadMinecraft(String manifestUrl, Path baseDir) throws IOException, InterruptedException {
    downloadMinecraft(manifestUrl, baseDir, "1.21");
  }

  private static JsonNode fetchJson(String url) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body());
  }

  private static void downloadFile(String url, Path dest) throws IOException, InterruptedException {
    if (Files.exists(dest))
      return; // skip if already downloaded
    Files.createDirectories(dest.getParent());

    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
    HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    try (InputStream in = response.body()) {
      Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
    }
    System.out.println("Downloaded: " + dest);
  }

  private static void downloadAssets(Path assetIndexPath, Path baseDir) throws IOException, InterruptedException {
    System.out.println("Downloading assets...");
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
        downloadFile(assetUrl, assetPath);
      }

      downloaded++;
      if (downloaded >= 100) {
        System.out.println("Downloaded " + downloaded + "/" + totalAssets + " assets...");
      }
    }

    System.out.println("Downloaded all " + totalAssets + " assets!");
  }

  public static void main(String[] args) throws Exception {
    String manifestUrl = "https://piston-meta.mojang.com/v1/packages/ff7e92039cfb1dca99bad680f278c40edd82f0e1/1.21.json";
    Path baseDir = Path.of("minecraft"); // change to your desired folder
    downloadMinecraft(manifestUrl, baseDir, "1.21");
  }

  private static boolean isLibraryAllowed(JsonNode lib) {
    JsonNode rules = lib.path("rules");
    if (rules.isMissingNode())
      return true;

    for (JsonNode rule : rules) {
      String action = rule.get("action").asText();
      JsonNode os = rule.path("os");

      if (os.isMissingNode()) {
        return "allow".equals(action);
      } else {
        String osName = os.path("name").asText();
        if (matchesCurrentOS(osName)) {
          return "allow".equals(action);
        }
      }
    }
    return true;
  }

  private static boolean matchesCurrentOS(String osName) {
    String currentOS = System.getProperty("os.name").toLowerCase();
    if (currentOS.contains("win") && "windows".equals(osName))
      return true;
    if (currentOS.contains("mac") && "osx".equals(osName))
      return true;
    if (currentOS.contains("nix") || currentOS.contains("nux"))
      return "linux".equals(osName);
    return false;
  }

  private static String getNativeClassifier() {
    String os = System.getProperty("os.name").toLowerCase();
    String arch = System.getProperty("os.arch").toLowerCase();

    if (os.contains("win")) {
      return arch.contains("64") ? "natives-windows-x86_64" : "natives-windows-x86";
    } else if (os.contains("mac")) {
      return arch.contains("aarch64") || arch.contains("arm") ? "natives-macos-arm64" : "natives-macos-x86_64";
    } else if (os.contains("nix") || os.contains("nux")) {
      return arch.contains("64") ? "natives-linux-x86_64" : "natives-linux-x86";
    }
    return null;
  }
}
