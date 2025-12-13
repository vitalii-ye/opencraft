package opencraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class FabricLauncher {

  private static final ObjectMapper mapper = new ObjectMapper();
  private static final HttpClient client = HttpClient.newHttpClient();

  /**
   * Launch Minecraft with the specified version (supports both vanilla and
   * Fabric)
   */
  public static void launchMinecraft(String versionId, String username, Path baseDir) throws Exception {
    Path versionJson = baseDir.resolve("versions/" + versionId + "/" + versionId + ".json");

    if (!Files.exists(versionJson)) {
      throw new IOException("Manifest not found: " + versionJson);
    }

    JsonNode root = mapper.readTree(versionJson.toFile());

    // Build classpath
    List<String> classpath = new ArrayList<>();

    // For Fabric versions, we need to handle inheritance
    String mainClass = "";
    String assetIndexId = "";

    if (root.has("inheritsFrom")) {
      // This is a Fabric version that inherits from a vanilla version
      String baseVersion = root.get("inheritsFrom").asText();
      Path baseVersionJson = baseDir.resolve("versions/" + baseVersion + "/" + baseVersion + ".json");

      if (!Files.exists(baseVersionJson)) {
        throw new IOException("Base version manifest not found: " + baseVersionJson);
      }

      // Check if Fabric libraries are missing and download them
      checkAndDownloadFabricLibraries(versionId, baseDir);

      // Use the pre-built libraries file for the base version
      Path librariesFile = baseDir.resolve("libraries_" + baseVersion + ".txt");
      if (Files.exists(librariesFile)) {
        String librariesPath = Files.readString(librariesFile).trim();
        String[] libs = librariesPath.split(File.pathSeparator);
        for (String lib : libs) {
          if (!lib.trim().isEmpty()) {
            // Convert relative paths to absolute paths
            String libPath = lib.trim();
            if (!libPath.startsWith("/")) {
              // Resolve relative to current working directory
              libPath = Paths.get(libPath).toAbsolutePath().toString();
            }
            classpath.add(libPath);
          }
        }
      }

      // Add Fabric libraries first (they need to be loaded before the main JAR)
      System.out.println("Adding Fabric libraries to classpath...");
      addFabricLibrariesToClasspath(root, baseDir, classpath);

      // Add main JAR from base version
      Path mainJar = baseDir.resolve("versions/" + baseVersion + "/" + baseVersion + ".jar");
      if (Files.exists(mainJar)) {
        classpath.add(mainJar.toAbsolutePath().toString());
      }

      // Debug: Print the full classpath
      System.out.println("Full classpath (" + classpath.size() + " entries):");
      for (int i = 0; i < classpath.size(); i++) {
        String path = classpath.get(i);
        boolean exists = Files.exists(Paths.get(path));
        System.out.println(i + ": " + path + " [" + (exists ? "EXISTS" : "MISSING") + "]");
      }
      
      System.out.println("Main class: " + mainClass);
      System.out.println("Base version: " + baseVersion);

      // Use main class from Fabric (which launches Fabric loader)
      mainClass = root.path("mainClass").asText();

      // Get asset index from base version
      JsonNode baseRoot = mapper.readTree(baseVersionJson.toFile());
      assetIndexId = baseRoot.path("assetIndex").path("id").asText();
    } else {
      // This is a vanilla version - use pre-built libraries file
      Path librariesFile = baseDir.resolve("libraries_" + versionId + ".txt");
      if (Files.exists(librariesFile)) {
        String librariesPath = Files.readString(librariesFile).trim();
        String[] libs = librariesPath.split(File.pathSeparator);
        for (String lib : libs) {
          if (!lib.trim().isEmpty()) {
            // Convert relative paths to absolute paths
            String libPath = lib.trim();
            if (!libPath.startsWith("/")) {
              // Resolve relative to current working directory
              libPath = Paths.get(libPath).toAbsolutePath().toString();
            }
            classpath.add(libPath);
          }
        }
      }

      // Add main JAR
      Path mainJar = baseDir.resolve("versions/" + versionId + "/" + versionId + ".jar");
      if (Files.exists(mainJar)) {
        classpath.add(mainJar.toAbsolutePath().toString());
      }

      mainClass = root.path("mainClass").asText();
      assetIndexId = root.path("assetIndex").path("id").asText();
      
      // Debug: Print vanilla version info
      System.out.println("Vanilla version detected");
      System.out.println("Main class: " + mainClass);
      System.out.println("Asset index: " + assetIndexId);
      System.out.println("Full classpath (" + classpath.size() + " entries):");
      for (int i = 0; i < classpath.size(); i++) {
        String path = classpath.get(i);
        boolean exists = Files.exists(Paths.get(path));
        System.out.println(i + ": " + path + " [" + (exists ? "EXISTS" : "MISSING") + "]");
      }
    }

    // Launch arguments
    String assetsDir = baseDir.resolve("assets").toString();
    String gameDir = baseDir.resolve("game").toString();

    // Create game directory
    Files.createDirectories(Paths.get(gameDir));

    List<String> command = new ArrayList<>();
    command.add("java");
    command.add("-cp");
    command.add(String.join(File.pathSeparator, classpath));

    // JVM arguments (simplified)
    command.add("-Xmx2G");
    command.add("-XX:+UnlockExperimentalVMOptions");
    command.add("-XX:+UseG1GC");

    // Add Fabric-specific JVM arguments if present
    if (root.has("arguments") && root.path("arguments").has("jvm")) {
      JsonNode jvmArgs = root.path("arguments").path("jvm");
      for (JsonNode arg : jvmArgs) {
        if (arg.isTextual()) {
          command.add(arg.asText());
        }
      }
    }

    command.add(mainClass);

    // Game arguments
    command.add("--username");
    command.add(username);
    command.add("--version");
    command.add(versionId);
    command.add("--gameDir");
    command.add(gameDir);
    command.add("--assetsDir");
    command.add(assetsDir);
    command.add("--assetIndex");
    command.add(assetIndexId);
    command.add("--uuid");
    command.add("00000000-0000-0000-0000-000000000000");
    command.add("--accessToken");
    command.add("0");
    command.add("--userType");
    command.add("legacy");

    // Add Fabric-specific game arguments if present
    if (root.has("arguments") && root.path("arguments").has("game")) {
      JsonNode gameArgs = root.path("arguments").path("game");
      for (JsonNode arg : gameArgs) {
        if (arg.isTextual()) {
          command.add(arg.asText());
        }
      }
    }

    System.out.println("Starting Minecraft " + versionId + "...");
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(new File(gameDir));
    
    // On Windows, redirect I/O to prevent process blocking
    String osName = System.getProperty("os.name").toLowerCase();
    if (osName.contains("win")) {
      pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
      pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    } else {
      pb.inheritIO();
    }
    
    Process process = pb.start();
    
    // Don't wait for process - let it run independently
    // This prevents the launcher from freezing and allows proper game exit
    System.out.println("Minecraft " + versionId + " started successfully");
  }

  private static void checkAndDownloadFabricLibraries(String fabricVersionId, Path baseDir) {
    try {
      Path fabricJson = baseDir.resolve("versions/" + fabricVersionId + "/" + fabricVersionId + ".json");
      JsonNode root = mapper.readTree(fabricJson.toFile());
      JsonNode libraries = root.path("libraries");

      boolean missingLibraries = false;

      // Check if any Fabric libraries are missing
      for (JsonNode lib : libraries) {
        if (lib.has("name")) {
          String name = lib.get("name").asText();
          String[] parts = name.split(":");
          if (parts.length >= 3) {
            String group = parts[0].replace('.', '/');
            String artifact = parts[1];
            String version = parts[2];
            String fileName = artifact + "-" + version + ".jar";
            Path libPath = baseDir.resolve("libraries").resolve(group).resolve(artifact).resolve(version)
                .resolve(fileName);
            if (!Files.exists(libPath)) {
              missingLibraries = true;
              break;
            }
          }
        }
      }

      if (missingLibraries) {
        System.out.println("Some Fabric libraries are missing. Downloading...");
        downloadFabricLibraries(fabricVersionId, baseDir);
      }
    } catch (Exception e) {
      System.err.println("Warning: Could not check/download Fabric libraries: " + e.getMessage());
    }
  }

  private static void downloadFabricLibraries(String fabricVersionId, Path baseDir) {
    try {
      Path fabricJson = baseDir.resolve("versions/" + fabricVersionId + "/" + fabricVersionId + ".json");
      JsonNode root = mapper.readTree(fabricJson.toFile());
      JsonNode libraries = root.path("libraries");

      System.out.println("Downloading missing Fabric libraries...");

      int downloaded = 0;
      int total = libraries.size();

      for (JsonNode lib : libraries) {
        if (lib.has("name") && lib.has("url")) {
          String name = lib.get("name").asText();
          String url = lib.get("url").asText();

          if (downloadLibrary(name, url, baseDir)) {
            downloaded++;
          }
        }
      }

      System.out.println("Downloaded " + downloaded + " Fabric libraries out of " + total + " total.");
    } catch (Exception e) {
      System.err.println("Error downloading Fabric libraries: " + e.getMessage());
    }
  }

  private static boolean downloadLibrary(String name, String baseUrl, Path baseDir) {
    try {
      String[] parts = name.split(":");
      if (parts.length < 3) {
        return false;
      }

      String group = parts[0].replace('.', '/');
      String artifact = parts[1];
      String version = parts[2];
      String fileName = artifact + "-" + version + ".jar";

      Path libPath = baseDir.resolve("libraries").resolve(group).resolve(artifact).resolve(version).resolve(fileName);

      if (Files.exists(libPath)) {
        return false; // Already exists
      }

      // Build download URL
      String downloadUrl = baseUrl;
      if (!downloadUrl.endsWith("/")) {
        downloadUrl += "/";
      }
      downloadUrl += group + "/" + artifact + "/" + version + "/" + fileName;

      // Download the file
      Files.createDirectories(libPath.getParent());

      HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build();
      HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

      if (response.statusCode() == 200) {
        try (InputStream in = response.body()) {
          Files.copy(in, libPath, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("Downloaded: " + fileName);
        return true;
      } else {
        System.err.println("Failed to download " + fileName + " (HTTP " + response.statusCode() + ")");
        return false;
      }

    } catch (Exception e) {
      System.err.println("Error downloading library " + name + ": " + e.getMessage());
      return false;
    }
  }

  private static void addFabricLibrariesToClasspath(JsonNode fabricRoot, Path baseDir, List<String> classpath) {
    JsonNode libraries = fabricRoot.path("libraries");
    System.out.println("Processing " + libraries.size() + " Fabric libraries...");

    for (JsonNode lib : libraries) {
      // Check if this library is allowed for current OS/arch
      if (!isLibraryAllowed(lib))
        continue;

      // For Fabric libraries, they typically don't have download info, just name
      if (lib.has("name")) {
        String name = lib.get("name").asText();
        System.out.println("Processing library: " + name);
        String[] parts = name.split(":");
        if (parts.length >= 3) {
          String group = parts[0].replace('.', '/');
          String artifact = parts[1];
          String version = parts[2];
          String fileName = artifact + "-" + version + ".jar";
          Path libPath = baseDir.resolve("libraries").resolve(group).resolve(artifact).resolve(version)
              .resolve(fileName);
          if (Files.exists(libPath)) {
            classpath.add(libPath.toAbsolutePath().toString());
            System.out.println("  Added to classpath: " + libPath);
          } else {
            System.err.println("Warning: Fabric library not found: " + libPath);

            // Try to download it if we have a URL
            if (lib.has("url")) {
              String baseUrl = lib.get("url").asText();
              System.out.println("  Attempting to download missing library: " + name);
              if (downloadLibrary(name, baseUrl, baseDir)) {
                // Retry adding to classpath after download
                if (Files.exists(libPath)) {
                  classpath.add(libPath.toAbsolutePath().toString());
                  System.out.println("  Successfully downloaded and added: " + name);
                }
              }
            } else {
              System.err.println("  No download URL available for: " + name);
            }
          }
        }
      }

      // Handle libraries with download info (rare for Fabric, but possible)
      JsonNode artifactNode = lib.path("downloads").path("artifact");
      if (!artifactNode.isMissingNode()) {
        String path = artifactNode.get("path").asText();
        Path libPath = baseDir.resolve("libraries").resolve(path);
        if (Files.exists(libPath)) {
          classpath.add(libPath.toAbsolutePath().toString());
        }
      }
    }
  }

  private static void addLibrariesToClasspath(JsonNode versionRoot, Path baseDir, List<String> classpath) {
    JsonNode libraries = versionRoot.path("libraries");
    for (JsonNode lib : libraries) {
      // Check if this library is allowed for current OS/arch
      if (!isLibraryAllowed(lib))
        continue;

      JsonNode artifactNode = lib.path("downloads").path("artifact");
      if (!artifactNode.isMissingNode()) {
        String path = artifactNode.get("path").asText();
        Path libPath = baseDir.resolve("libraries").resolve(path);
        if (Files.exists(libPath)) {
          classpath.add(libPath.toAbsolutePath().toString());
        }
      }

      // For libraries that don't have download info (like Fabric libraries)
      if (artifactNode.isMissingNode() && lib.has("name")) {
        String name = lib.get("name").asText();
        String[] parts = name.split(":");
        if (parts.length >= 3) {
          String group = parts[0].replace('.', '/');
          String artifact = parts[1];
          String version = parts[2];
          String fileName = artifact + "-" + version + ".jar";
          Path libPath = baseDir.resolve("libraries").resolve(group).resolve(artifact).resolve(version)
              .resolve(fileName);
          if (Files.exists(libPath)) {
            classpath.add(libPath.toAbsolutePath().toString());
          }
        }
      }

      // Native libraries
      JsonNode classifiers = lib.path("downloads").path("classifiers");
      if (!classifiers.isMissingNode()) {
        String nativeKey = getNativeClassifier();
        if (nativeKey != null && classifiers.has(nativeKey)) {
          JsonNode nativeLib = classifiers.get(nativeKey);
          String path = nativeLib.get("path").asText();
          Path libPath = baseDir.resolve("libraries").resolve(path);
          if (Files.exists(libPath)) {
            classpath.add(libPath.toAbsolutePath().toString());
          }
        }
      }
    }
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
