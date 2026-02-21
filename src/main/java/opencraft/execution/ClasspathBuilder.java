package opencraft.execution;

import com.fasterxml.jackson.databind.JsonNode;
import opencraft.network.FabricDownloader;
import opencraft.utils.MavenCoordinateParser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Builds a classpath list from multiple sources: Fabric libraries, vanilla
 * libraries, a pre-built libraries text file, and a single main JAR.
 *
 * <p>Usage:
 * <pre>
 * List&lt;String&gt; cp = new ClasspathBuilder(fabricDownloader)
 *         .addFromLibrariesFile(librariesFile, baseDir)
 *         .addFabricLibraries(fabricRoot, baseDir)
 *         .addMainJar(mainJar)
 *         .build();
 * </pre>
 */
public class ClasspathBuilder {

    private final List<String> entries = new ArrayList<>();
    private final FabricDownloader fabricDownloader;

    /**
     * Creates a {@code ClasspathBuilder} with the provided {@link FabricDownloader}
     * used for on-demand library downloads.
     */
    public ClasspathBuilder(FabricDownloader fabricDownloader) {
        this.fabricDownloader = fabricDownloader;
    }

    /** Adds classpath entries read from a pre-built libraries text file. */
    public ClasspathBuilder addFromLibrariesFile(Path librariesFile, Path baseDir) {
        if (!Files.exists(librariesFile)) {
            return this;
        }
        try {
            String librariesPath = Files.readString(librariesFile).trim();
            for (String lib : librariesPath.split(File.pathSeparator)) {
                String libPath = lib.trim();
                if (libPath.isEmpty()) {
                    continue;
                }
                if (!libPath.startsWith("/")) {
                    libPath = Paths.get(libPath).toAbsolutePath().toString();
                }
                entries.add(libPath);
            }
        } catch (java.io.IOException e) {
            System.err.println("Warning: could not read libraries file " + librariesFile + ": " + e.getMessage());
        }
        return this;
    }

    /**
     * Adds Fabric library JARs derived from the Fabric version JSON node.
     *
     * <p>Libraries with OS rules that exclude the current platform are skipped.
     * If a library file is missing but has a {@code url} field, an on-demand
     * download is attempted via {@link FabricDownloader#downloadLibrary}.
     */
    public ClasspathBuilder addFabricLibraries(JsonNode fabricRoot, Path baseDir) {
        JsonNode libraries = fabricRoot.path("libraries");
        System.out.println("Processing " + libraries.size() + " Fabric libraries...");

        for (JsonNode lib : libraries) {
            if (!isLibraryAllowed(lib)) {
                continue;
            }

            if (lib.has("name")) {
                String name = lib.get("name").asText();
                System.out.println("Processing library: " + name);
                String[] parts = name.split(":");
                if (parts.length >= 3) {
                    MavenCoordinateParser coords = MavenCoordinateParser.parse(name);
                    Path libPath = baseDir.resolve("libraries").resolve(coords.getRelativePath());

                    if (Files.exists(libPath)) {
                        entries.add(libPath.toAbsolutePath().toString());
                        System.out.println("  Added to classpath: " + libPath);
                    } else {
                        System.err.println("Warning: Fabric library not found: " + libPath);
                        if (lib.has("url")) {
                            String baseUrl = lib.get("url").asText();
                            System.out.println("  Attempting to download missing library: " + name);
                            if (fabricDownloader.downloadLibrary(name, baseUrl)) {
                                if (Files.exists(libPath)) {
                                    entries.add(libPath.toAbsolutePath().toString());
                                    System.out.println("  Successfully downloaded and added: " + name);
                                }
                            }
                        } else {
                            System.err.println("  No download URL available for: " + name);
                        }
                    }
                }
            }

            // Handle libraries with explicit download artifact info (rare for Fabric)
            JsonNode artifactNode = lib.path("downloads").path("artifact");
            if (!artifactNode.isMissingNode()) {
                String path = artifactNode.get("path").asText();
                Path libPath = baseDir.resolve("libraries").resolve(path);
                if (Files.exists(libPath)) {
                    entries.add(libPath.toAbsolutePath().toString());
                }
            }
        }
        return this;
    }

    /**
     * Adds vanilla Minecraft library JARs derived from a version JSON node.
     *
     * <p>Handles artifact downloads, name-only libraries, and native classifiers.
     */
    public ClasspathBuilder addVanillaLibraries(JsonNode versionRoot, Path baseDir) {
        JsonNode libraries = versionRoot.path("libraries");
        for (JsonNode lib : libraries) {
            if (!isLibraryAllowed(lib)) {
                continue;
            }

            JsonNode artifactNode = lib.path("downloads").path("artifact");
            if (!artifactNode.isMissingNode()) {
                String path = artifactNode.get("path").asText();
                Path libPath = baseDir.resolve("libraries").resolve(path);
                if (Files.exists(libPath)) {
                    entries.add(libPath.toAbsolutePath().toString());
                }
            }

            // For libraries without download info (e.g. Fabric-style entries)
            if (artifactNode.isMissingNode() && lib.has("name")) {
                String name = lib.get("name").asText();
                String[] parts = name.split(":");
                if (parts.length >= 3) {
                    MavenCoordinateParser coords = MavenCoordinateParser.parse(name);
                    Path libPath = baseDir.resolve("libraries").resolve(coords.getRelativePath());
                    if (Files.exists(libPath)) {
                        entries.add(libPath.toAbsolutePath().toString());
                    }
                }
            }

            // Native classifier JARs
            JsonNode classifiers = lib.path("downloads").path("classifiers");
            if (!classifiers.isMissingNode()) {
                String nativeKey = getNativeClassifier();
                if (nativeKey != null && classifiers.has(nativeKey)) {
                    String path = classifiers.get(nativeKey).get("path").asText();
                    Path libPath = baseDir.resolve("libraries").resolve(path);
                    if (Files.exists(libPath)) {
                        entries.add(libPath.toAbsolutePath().toString());
                    }
                }
            }
        }
        return this;
    }

    /** Adds a single JAR to the classpath (e.g. the main game JAR). */
    public ClasspathBuilder addMainJar(Path mainJar) {
        if (Files.exists(mainJar)) {
            entries.add(mainJar.toAbsolutePath().toString());
        }
        return this;
    }

    /** Returns an unmodifiable snapshot of the classpath entries collected so far. */
    public List<String> build() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    // -------------------------------------------------------------------------
    // OS / platform helpers (used only for library filtering)
    // -------------------------------------------------------------------------

    private static boolean isLibraryAllowed(JsonNode lib) {
        JsonNode rules = lib.path("rules");
        if (rules.isMissingNode()) {
            return true;
        }
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
        String current = System.getProperty("os.name").toLowerCase();
        if (current.contains("win") && "windows".equals(osName)) return true;
        if (current.contains("mac") && "osx".equals(osName)) return true;
        if (current.contains("nix") || current.contains("nux")) return "linux".equals(osName);
        return false;
    }

    private static String getNativeClassifier() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        if (os.contains("win")) {
            return arch.contains("64") ? "natives-windows-x86_64" : "natives-windows-x86";
        } else if (os.contains("mac")) {
            return arch.contains("aarch64") || arch.contains("arm")
                    ? "natives-macos-arm64" : "natives-macos-x86_64";
        } else if (os.contains("nix") || os.contains("nux")) {
            return arch.contains("64") ? "natives-linux-x86_64" : "natives-linux-x86";
        }
        return null;
    }
}
