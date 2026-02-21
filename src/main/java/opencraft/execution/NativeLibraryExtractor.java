package opencraft.execution;

import com.fasterxml.jackson.databind.JsonNode;
import opencraft.utils.PlatformUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles extraction of native libraries from Minecraft version JARs.
 */
public class NativeLibraryExtractor {

  private NativeLibraryExtractor() {
    // utility class — not instantiable
  }

  /**
   * Extracts native libraries from JARs to a version-specific directory.
   *
   * @param versionRoot The version JSON root node
   * @param baseDir     The Minecraft base directory
   * @param versionId   The version ID (used to name the output directory)
   * @return Path to the directory containing the extracted native libraries
   * @throws IOException if extraction fails
   */
  public static Path extractForVersion(JsonNode versionRoot, Path baseDir, String versionId) throws IOException {
    Path nativesDir = baseDir.resolve("libraries/natives/" + versionId);

    // Clean and recreate natives directory
    if (Files.exists(nativesDir)) {
      deleteDirectory(nativesDir);
    }
    Files.createDirectories(nativesDir);

    JsonNode libraries = versionRoot.path("libraries");
    String nativeKey = PlatformUtils.getNativeClassifier();

    if (nativeKey == null) {
      System.err.println("Warning: Could not determine native classifier for current platform");
      return nativesDir;
    }

    System.out.println("Extracting native libraries for: " + nativeKey);

    for (JsonNode lib : libraries) {
      // Check if this library is allowed for current OS
      if (!PlatformUtils.isLibraryAllowed(lib)) {
        continue;
      }

      // Check for native libraries in classifiers
      JsonNode classifiers = lib.path("downloads").path("classifiers");
      if (!classifiers.isMissingNode() && classifiers.has(nativeKey)) {
        JsonNode nativeLib = classifiers.get(nativeKey);
        String path = nativeLib.get("path").asText();
        Path nativeJarPath = baseDir.resolve("libraries").resolve(path);

        if (Files.exists(nativeJarPath)) {
          System.out.println("Extracting natives from: " + nativeJarPath.getFileName());
          extractJar(nativeJarPath, nativesDir);
        } else {
          System.err.println("Warning: Native library not found: " + nativeJarPath);
        }
      }
    }

    System.out.println("Native libraries extracted to: " + nativesDir);
    return nativesDir;
  }

  /**
   * Extracts all files from a JAR/ZIP file to a target directory.
   *
   * @param jarPath   Path to the JAR/ZIP file
   * @param targetDir Directory to extract files into
   * @throws IOException if extraction fails
   */
  public static void extractJar(Path jarPath, Path targetDir) throws IOException {
    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(jarPath.toFile()))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String name = entry.getName();

        // Skip META-INF and directories
        if (name.startsWith("META-INF/") || entry.isDirectory()) {
          continue;
        }

        Path targetFile = targetDir.resolve(name);
        Files.createDirectories(targetFile.getParent());

        try (FileOutputStream fos = new FileOutputStream(targetFile.toFile())) {
          byte[] buffer = new byte[8192];
          int len;
          while ((len = zis.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
          }
        }

        zis.closeEntry();
      }
    }
  }

  /**
   * Recursively deletes a directory and all its contents.
   *
   * @param directory The directory to delete
   * @throws IOException if deletion fails
   */
  public static void deleteDirectory(Path directory) throws IOException {
    if (Files.exists(directory)) {
      Files.walk(directory)
          .sorted((a, b) -> b.compareTo(a)) // Reverse order: files before directories
          .forEach(path -> {
            try {
              Files.delete(path);
            } catch (IOException e) {
              System.err.println("Failed to delete: " + path);
            }
          });
    }
  }

}
