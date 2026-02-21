package opencraft.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import opencraft.network.FabricDownloader;
import opencraft.utils.PlatformUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class FabricLauncher {

  private final ObjectMapper mapper;
  private final FabricDownloader fabricDownloader;

  /** Creates a {@code FabricLauncher} using the default Minecraft directory. */
  public FabricLauncher() {
    this.mapper = new ObjectMapper();
    this.fabricDownloader = new FabricDownloader();
  }

  /**
   * Dependency-injection constructor.
   *
   * @param fabricDownloader the downloader used to fetch missing Fabric libraries
   */
  public FabricLauncher(FabricDownloader fabricDownloader) {
    this.mapper = new ObjectMapper();
    this.fabricDownloader = fabricDownloader;
  }

  private static void debug(String msg) {
    System.out.println(msg);
  }

  /**
   * Launch Minecraft with the specified version (supports both vanilla and Fabric)
   */
  public void launchMinecraft(String versionId, String username, Path baseDir) throws Exception {
    Path versionJson = baseDir.resolve("versions/" + versionId + "/" + versionId + ".json");

    if (!Files.exists(versionJson)) {
      throw new IOException("Manifest not found: " + versionJson);
    }

    JsonNode root = mapper.readTree(versionJson.toFile());

    // For Fabric versions, we need to handle inheritance
    String mainClass = "";
    String assetIndexId = "";

    List<String> classpath;

    if (root.has("inheritsFrom")) {
      // This is a Fabric version that inherits from a vanilla version
      String baseVersion = root.get("inheritsFrom").asText();
      Path baseVersionJson = baseDir.resolve("versions/" + baseVersion + "/" + baseVersion + ".json");

      if (!Files.exists(baseVersionJson)) {
        throw new IOException("Base version manifest not found: " + baseVersionJson);
      }

      // Check if Fabric libraries are missing and download them
      fabricDownloader.checkAndDownloadFabricLibraries(versionId);

      // Build classpath using ClasspathBuilder
      Path librariesFile = baseDir.resolve("libraries_" + baseVersion + ".txt");
      Path mainJar = baseDir.resolve("versions/" + baseVersion + "/" + baseVersion + ".jar");

      debug("Adding Fabric libraries to classpath...");
      classpath = new ClasspathBuilder(fabricDownloader)
          .addFromLibrariesFile(librariesFile, baseDir)
          .addFabricLibraries(root, baseDir)
          .addMainJar(mainJar)
          .build();

      // Debug: Print the full classpath
      debug("Full classpath (" + classpath.size() + " entries):");
      for (int i = 0; i < classpath.size(); i++) {
        String path = classpath.get(i);
        boolean exists = Files.exists(Paths.get(path));
        debug(i + ": " + path + " [" + (exists ? "EXISTS" : "MISSING") + "]");
      }

      debug("Main class: " + mainClass);
      debug("Base version: " + baseVersion);

      // Use main class from Fabric (which launches Fabric loader)
      mainClass = root.path("mainClass").asText();

      // Get asset index from base version
      JsonNode baseRoot = mapper.readTree(baseVersionJson.toFile());
      assetIndexId = baseRoot.path("assetIndex").path("id").asText();
    } else {
      // This is a vanilla version - use pre-built libraries file
      Path librariesFile = baseDir.resolve("libraries_" + versionId + ".txt");
      Path mainJar = baseDir.resolve("versions/" + versionId + "/" + versionId + ".jar");

      classpath = new ClasspathBuilder(fabricDownloader)
          .addFromLibrariesFile(librariesFile, baseDir)
          .addMainJar(mainJar)
          .build();

      mainClass = root.path("mainClass").asText();
      assetIndexId = root.path("assetIndex").path("id").asText();

      // Debug: Print vanilla version info
      debug("Vanilla version detected");
      debug("Main class: " + mainClass);
      debug("Asset index: " + assetIndexId);
      debug("Full classpath (" + classpath.size() + " entries):");
      for (int i = 0; i < classpath.size(); i++) {
        String path = classpath.get(i);
        boolean exists = Files.exists(Paths.get(path));
        debug(i + ": " + path + " [" + (exists ? "EXISTS" : "MISSING") + "]");
      }
    }

    // Launch arguments
    String assetsDir = baseDir.resolve("assets").toString();
    String gameDir = baseDir.toString();

    // Create game directory
    Files.createDirectories(Paths.get(gameDir));

    // Extract native libraries
    // For Fabric versions, we need to extract natives from the base version
    JsonNode versionForNatives = root;
    String versionIdForNatives = versionId;

    if (root.has("inheritsFrom")) {
      // Use the base version for native library extraction
      String baseVersion = root.get("inheritsFrom").asText();
      Path baseVersionJson = baseDir.resolve("versions/" + baseVersion + "/" + baseVersion + ".json");
      versionForNatives = mapper.readTree(baseVersionJson.toFile());
      versionIdForNatives = baseVersion;
    }

    Path nativesDir = NativeLibraryExtractor.extractForVersion(versionForNatives, baseDir, versionIdForNatives);
    debug("Natives directory: " + nativesDir);

    // Build command using LauncherCommandBuilder
    LauncherCommandBuilder builder = new LauncherCommandBuilder(mainClass, nativesDir);

    // Add classpath entries
    builder.addClasspathEntries(classpath);

    // On macOS, GLFW requires -XstartOnFirstThread
    if (PlatformUtils.isMac()) {
      builder.addJvmArg("-XstartOnFirstThread");
    }

    // JVM arguments
    builder.addJvmArg(LaunchConstants.MAX_MEMORY);
    builder.addJvmArg("-XX:+UnlockExperimentalVMOptions");
    builder.addJvmArg("-XX:+UseG1GC");

    // Add Fabric-specific JVM arguments if present
    if (root.has("arguments") && root.path("arguments").has("jvm")) {
      JsonNode jvmArgs = root.path("arguments").path("jvm");
      for (JsonNode arg : jvmArgs) {
        if (arg.isTextual()) {
          builder.addJvmArg(arg.asText());
        }
      }
    }

    // Game arguments
    builder.addGameArg("--username");
    builder.addGameArg(username);
    builder.addGameArg("--version");
    builder.addGameArg(versionId);
    builder.addGameArg("--gameDir");
    builder.addGameArg(gameDir);
    builder.addGameArg("--assetsDir");
    builder.addGameArg(assetsDir);
    builder.addGameArg("--assetIndex");
    builder.addGameArg(assetIndexId);
    builder.addGameArg("--uuid");
    builder.addGameArg(LaunchConstants.OFFLINE_UUID);
    builder.addGameArg("--accessToken");
    builder.addGameArg(LaunchConstants.OFFLINE_ACCESS_TOKEN);
    builder.addGameArg("--userType");
    builder.addGameArg(LaunchConstants.USER_TYPE);

    // Add Fabric-specific game arguments if present
    if (root.has("arguments") && root.path("arguments").has("game")) {
      JsonNode gameArgs = root.path("arguments").path("game");
      for (JsonNode arg : gameArgs) {
        if (arg.isTextual()) {
          builder.addGameArg(arg.asText());
        }
      }
    }

    List<String> command = builder.build();

    debug("Starting Minecraft " + versionId + "...");
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(new File(gameDir));

    // On Windows, redirect I/O to prevent process blocking
    if (PlatformUtils.isWindows()) {
      pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
      pb.redirectError(ProcessBuilder.Redirect.INHERIT);
    } else {
      pb.inheritIO();
    }

    Process process = pb.start();

    // Don't wait for process - let it run independently
    // This prevents the launcher from freezing and allows proper game exit
    debug("Minecraft " + versionId + " started successfully");
  }

}
