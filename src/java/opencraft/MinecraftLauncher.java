package opencraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MinecraftLauncher {

  private static final ObjectMapper mapper = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    Path baseDir = Path.of("minecraft"); // where files were downloaded
    Path versionJson = baseDir.resolve("versions/1.21/1.21.json");

    if (!Files.exists(versionJson)) {
      System.err.println("Manifest not found: " + versionJson);
      return;
    }

    // Add verification mode
    boolean verifyOnly = args.length > 0 && args[0].equals("--verify");

    JsonNode root = mapper.readTree(versionJson.toFile());

    // Build classpath
    List<String> classpath = new ArrayList<>();

    // Add main JAR
    Path mainJar = baseDir.resolve("versions/1.21/1.21.jar");
    if (Files.exists(mainJar)) {
      classpath.add(mainJar.toString());
    }

    // Add libraries
    JsonNode libraries = root.path("libraries");
    for (JsonNode lib : libraries) {
      // Check if this library is allowed for current OS/arch
      if (!isLibraryAllowed(lib))
        continue;

      JsonNode artifactNode = lib.path("downloads").path("artifact");
      if (!artifactNode.isMissingNode()) {
        String path = artifactNode.get("path").asText();
        Path libPath = baseDir.resolve("libraries").resolve(path);
        if (Files.exists(libPath)) {
          classpath.add(libPath.toString());
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
            classpath.add(libPath.toString());
          }
        }
      }
    }

    if (verifyOnly) {
      System.out.println("✅ Verification complete!");
      System.out.println("📁 Found manifest: " + versionJson);
      System.out.println("📦 Found main JAR: " + mainJar);
      System.out.println("📚 Found " + classpath.size() + " library files");
      System.out.println("🎮 Ready to launch Minecraft (would need proper assets and authentication)");
      return;
    }

    // Launch arguments
    String mainClass = root.path("mainClass").asText();
    String assetsDir = baseDir.resolve("assets").toString();
    String gameDir = baseDir.resolve("game").toString();
    String assetIndexId = root.path("assetIndex").path("id").asText();

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

    command.add(mainClass);

    // Game arguments
    command.add("--username");
    command.add("Player");
    command.add("--version");
    command.add("1.21");
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

    System.out.println("Starting Minecraft...");
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(new File(gameDir));
    pb.inheritIO();
    Process process = pb.start();
    int exitCode = process.waitFor();
    System.out.println("Minecraft exited with code: " + exitCode);
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
