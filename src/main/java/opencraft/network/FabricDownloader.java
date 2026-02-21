package opencraft.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import opencraft.utils.MinecraftPathResolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/**
 * Downloads Fabric mod loader files including profile JSON and libraries.
 */
public class FabricDownloader {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Downloads a complete Fabric installation for the given game and loader version.
     * This includes:
     * 1. The Fabric profile JSON (version manifest)
     * 2. All required Fabric libraries
     * 
     * Note: The vanilla Minecraft version must already be downloaded.
     * 
     * @param gameVersion   The Minecraft game version (e.g., "1.21")
     * @param loaderVersion The Fabric loader version (e.g., "0.15.11")
     * @param logger        Optional logger for progress updates
     */
    public static void downloadFabric(String gameVersion, String loaderVersion, Consumer<String> logger)
            throws IOException, InterruptedException {
        
        Path baseDir = MinecraftPathResolver.getMinecraftDirectory();
        String fabricVersionId = "fabric-loader-" + loaderVersion + "-" + gameVersion;
        
        log(logger, "Downloading Fabric " + loaderVersion + " for Minecraft " + gameVersion + "...");

        // Step 1: Fetch and save the Fabric profile JSON
        log(logger, "Fetching Fabric profile...");
        JsonNode profileJson = FabricVersionManager.fetchProfileJson(gameVersion, loaderVersion);
        
        Path versionDir = baseDir.resolve("versions").resolve(fabricVersionId);
        Files.createDirectories(versionDir);
        
        Path profilePath = versionDir.resolve(fabricVersionId + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(profilePath.toFile(), profileJson);
        log(logger, "Saved Fabric profile: " + profilePath);

        // Step 2: Download Fabric libraries
        JsonNode libraries = profileJson.path("libraries");
        int totalLibs = libraries.size();
        int downloaded = 0;

        log(logger, "Downloading " + totalLibs + " Fabric libraries...");

        for (JsonNode lib : libraries) {
            String name = lib.path("name").asText();
            String url = lib.path("url").asText("");
            
            if (name.isEmpty()) {
                continue;
            }

            // Parse Maven coordinates: group:artifact:version
            String[] parts = name.split(":");
            if (parts.length < 3) {
                log(logger, "Skipping invalid library: " + name);
                continue;
            }

            String group = parts[0].replace('.', '/');
            String artifact = parts[1];
            String version = parts[2];
            String fileName = artifact + "-" + version + ".jar";
            
            Path libPath = baseDir.resolve("libraries")
                    .resolve(group)
                    .resolve(artifact)
                    .resolve(version)
                    .resolve(fileName);

            // Skip if already downloaded
            if (Files.exists(libPath)) {
                downloaded++;
                continue;
            }

            // Build download URL
            String downloadUrl;
            if (!url.isEmpty()) {
                // Use the URL from the library definition
                downloadUrl = url;
                if (!downloadUrl.endsWith("/")) {
                    downloadUrl += "/";
                }
                downloadUrl += group + "/" + artifact + "/" + version + "/" + fileName;
            } else {
                // Try Maven Central as fallback
                downloadUrl = "https://repo1.maven.org/maven2/" + 
                        group + "/" + artifact + "/" + version + "/" + fileName;
            }

            // Download the library
            try {
                downloadFile(downloadUrl, libPath, logger);
                downloaded++;
                
                if (downloaded % 5 == 0 || downloaded == totalLibs) {
                    log(logger, "Downloaded " + downloaded + "/" + totalLibs + " libraries...");
                }
            } catch (IOException e) {
                // Try alternative Maven repositories
                boolean success = tryAlternativeRepos(name, libPath, logger);
                if (success) {
                    downloaded++;
                } else {
                    log(logger, "Warning: Failed to download library: " + name + " - " + e.getMessage());
                }
            }
        }

        log(logger, "Fabric " + fabricVersionId + " download complete!");
    }

    /**
     * Downloads Fabric using the latest stable loader for the given game version.
     */
    public static void downloadFabric(String gameVersion, Consumer<String> logger)
            throws IOException, InterruptedException {
        
        FabricVersionManager.FabricLoaderVersion loader = FabricVersionManager.getLatestStableLoader();
        if (loader == null) {
            throw new IOException("No Fabric loader versions available");
        }
        
        downloadFabric(gameVersion, loader.getVersion(), logger);
    }

    /**
     * Checks if Fabric is already installed for the given version.
     */
    public static boolean isFabricInstalled(String gameVersion, String loaderVersion) {
        Path baseDir = MinecraftPathResolver.getMinecraftDirectory();
        String fabricVersionId = "fabric-loader-" + loaderVersion + "-" + gameVersion;
        Path profilePath = baseDir.resolve("versions").resolve(fabricVersionId).resolve(fabricVersionId + ".json");
        return Files.exists(profilePath);
    }

    /**
     * Gets the Fabric version ID for a given game and loader version.
     */
    public static String getFabricVersionId(String gameVersion, String loaderVersion) {
        return "fabric-loader-" + loaderVersion + "-" + gameVersion;
    }

    /**
     * Tries to download a library from alternative Maven repositories.
     */
    private static boolean tryAlternativeRepos(String mavenCoords, Path destPath, Consumer<String> logger) {
        String[] repos = {
            "https://maven.fabricmc.net/",
            "https://repo1.maven.org/maven2/",
            "https://libraries.minecraft.net/"
        };

        String[] parts = mavenCoords.split(":");
        if (parts.length < 3) {
            return false;
        }

        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String fileName = artifact + "-" + version + ".jar";
        String path = group + "/" + artifact + "/" + version + "/" + fileName;

        for (String repo : repos) {
            try {
                downloadFile(repo + path, destPath, null);
                log(logger, "Downloaded from " + repo + ": " + fileName);
                return true;
            } catch (IOException | InterruptedException e) {
                // Try next repo
            }
        }

        return false;
    }

    /**
     * Downloads a file from URL to destination path.
     */
    private static void downloadFile(String url, Path dest, Consumer<String> logger) 
            throws IOException, InterruptedException {
        
        if (Files.exists(dest)) {
            return; // Skip if already downloaded
        }
        
        Files.createDirectories(dest.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "OpenCraft-Launcher/1.0")
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }

        try (InputStream in = response.body()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        } else {
            System.out.println(message);
        }
    }
}
