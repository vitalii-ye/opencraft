package opencraft.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import opencraft.utils.LogHelper;
import opencraft.model.FabricLoaderVersion;
import opencraft.utils.MavenCoordinateParser;
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

    private final ObjectMapper mapper;
    private final HttpClient client;
    private final FabricVersionManager fabricVersionManager;
    private final Path baseDir;

    /**
     * Creates a {@code FabricDownloader} using the default Minecraft directory
     * resolved via {@link MinecraftPathResolver}.
     */
    public FabricDownloader() {
        this(
            new ObjectMapper(),
            HttpClient.newHttpClient(),
            new FabricVersionManager(),
            MinecraftPathResolver.getMinecraftDirectory()
        );
    }

    /**
     * Creates a {@code FabricDownloader} with an explicit base directory,
     * intended for testing or custom installation paths.
     *
     * @param baseDir the Minecraft base directory to use for all file operations
     */
    public FabricDownloader(Path baseDir) {
        this(
            new ObjectMapper(),
            HttpClient.newHttpClient(),
            new FabricVersionManager(),
            baseDir
        );
    }

    /**
     * Full dependency-injection constructor.
     *
     * @param mapper               Jackson ObjectMapper
     * @param client               HTTP client
     * @param fabricVersionManager the Fabric version manager to query
     * @param baseDir              the Minecraft base directory
     */
    public FabricDownloader(ObjectMapper mapper, HttpClient client,
                            FabricVersionManager fabricVersionManager, Path baseDir) {
        this.mapper = mapper;
        this.client = client;
        this.fabricVersionManager = fabricVersionManager;
        this.baseDir = baseDir;
    }

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
    public void downloadFabric(String gameVersion, String loaderVersion, Consumer<String> logger)
            throws IOException, InterruptedException {
        
        String fabricVersionId = "fabric-loader-" + loaderVersion + "-" + gameVersion;
        
        LogHelper.log(logger, "Downloading Fabric " + loaderVersion + " for Minecraft " + gameVersion + "...");

        // Step 1: Fetch and save the Fabric profile JSON
        LogHelper.log(logger, "Fetching Fabric profile...");
        JsonNode profileJson = fabricVersionManager.fetchProfileJson(gameVersion, loaderVersion);
        
        Path versionDir = baseDir.resolve("versions").resolve(fabricVersionId);
        Files.createDirectories(versionDir);
        
        Path profilePath = versionDir.resolve(fabricVersionId + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(profilePath.toFile(), profileJson);
        LogHelper.log(logger, "Saved Fabric profile: " + profilePath);

        // Step 2: Download Fabric libraries
        JsonNode libraries = profileJson.path("libraries");
        int totalLibs = libraries.size();
        int downloaded = 0;

        LogHelper.log(logger, "Downloading " + totalLibs + " Fabric libraries...");

        for (JsonNode lib : libraries) {
            String name = lib.path("name").asText();
            String url = lib.path("url").asText("");
            
            if (name.isEmpty()) {
                continue;
            }

            // Parse Maven coordinates: group:artifact:version
            String[] parts = name.split(":");
            if (parts.length < 3) {
                LogHelper.log(logger, "Skipping invalid library: " + name);
                continue;
            }

            MavenCoordinateParser coords = MavenCoordinateParser.parse(name);
            Path libPath = baseDir.resolve("libraries").resolve(coords.getRelativePath());

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
                downloadUrl += coords.getRelativePath();
            } else {
                // Try Maven Central as fallback
                downloadUrl = "https://repo1.maven.org/maven2/" + coords.getRelativePath();
            }

            // Download the library
            try {
                FileDownloader.download(downloadUrl, libPath);
                downloaded++;
                
                if (downloaded % 5 == 0 || downloaded == totalLibs) {
                    LogHelper.log(logger, "Downloaded " + downloaded + "/" + totalLibs + " libraries...");
                }
            } catch (IOException e) {
                // Try alternative Maven repositories
                boolean success = tryAlternativeRepos(name, libPath, logger);
                if (success) {
                    downloaded++;
                } else {
                    LogHelper.log(logger, "Warning: Failed to download library: " + name + " - " + e.getMessage());
                }
            }
        }

        LogHelper.log(logger, "Fabric " + fabricVersionId + " download complete!");
    }

    /**
     * Downloads Fabric using the latest stable loader for the given game version.
     */
    public void downloadFabric(String gameVersion, Consumer<String> logger)
            throws IOException, InterruptedException {
        
        FabricLoaderVersion loader = fabricVersionManager.getLatestStableLoader();
        if (loader == null) {
            throw new IOException("No Fabric loader versions available");
        }
        
        downloadFabric(gameVersion, loader.getVersion(), logger);
    }

    /**
     * Checks if Fabric is already installed for the given version.
     */
    public boolean isFabricInstalled(String gameVersion, String loaderVersion) {
        String fabricVersionId = "fabric-loader-" + loaderVersion + "-" + gameVersion;
        Path profilePath = baseDir.resolve("versions").resolve(fabricVersionId).resolve(fabricVersionId + ".json");
        return Files.exists(profilePath);
    }

    /**
     * Gets the Fabric version ID for a given game and loader version.
     */
    public String getFabricVersionId(String gameVersion, String loaderVersion) {
        return "fabric-loader-" + loaderVersion + "-" + gameVersion;
    }

    /**
     * Tries to download a library from alternative Maven repositories.
     */
    private boolean tryAlternativeRepos(String mavenCoords, Path destPath, Consumer<String> logger) {
        String[] repos = {
            "https://maven.fabricmc.net/",
            "https://repo1.maven.org/maven2/",
            "https://libraries.minecraft.net/"
        };

        try {
            MavenCoordinateParser coords = MavenCoordinateParser.parse(mavenCoords);
            for (String repo : repos) {
                try {
                    FileDownloader.download(repo + coords.getRelativePath(), destPath);
                    LogHelper.log(logger, "Downloaded from " + repo + ": " + coords.getFileName());
                    return true;
                } catch (IOException | InterruptedException e) {
                    // Try next repo
                }
            }
        } catch (IllegalArgumentException e) {
            return false;
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Fabric library check / download methods (moved from FabricLauncher)
    // -------------------------------------------------------------------------

    /**
     * Checks whether any Fabric libraries referenced in the Fabric version JSON
     * are missing, and triggers a download of all missing libraries if needed.
     *
     * @param fabricVersionId the Fabric version ID (e.g. {@code fabric-loader-0.15.11-1.21})
     */
    public void checkAndDownloadFabricLibraries(String fabricVersionId) {
        try {
            Path fabricJson = baseDir.resolve("versions/" + fabricVersionId + "/" + fabricVersionId + ".json");
            JsonNode root = mapper.readTree(fabricJson.toFile());
            JsonNode libraries = root.path("libraries");

            boolean missingLibraries = false;
            for (JsonNode lib : libraries) {
                if (lib.has("name")) {
                    String name = lib.get("name").asText();
                    String[] parts = name.split(":");
                    if (parts.length >= 3) {
                        MavenCoordinateParser coords = MavenCoordinateParser.parse(name);
                        Path libPath = baseDir.resolve("libraries").resolve(coords.getRelativePath());
                        if (!Files.exists(libPath)) {
                            missingLibraries = true;
                            break;
                        }
                    }
                }
            }

            if (missingLibraries) {
                System.out.println("Some Fabric libraries are missing. Downloading...");
                downloadFabricLibraries(fabricVersionId);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not check/download Fabric libraries: " + e.getMessage());
        }
    }

    /**
     * Downloads all Fabric libraries listed in the Fabric version JSON that
     * include a {@code url} field.
     *
     * @param fabricVersionId the Fabric version ID
     */
    public void downloadFabricLibraries(String fabricVersionId) {
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
                    if (downloadLibrary(name, url)) {
                        downloaded++;
                    }
                }
            }

            System.out.println("Downloaded " + downloaded + " Fabric libraries out of " + total + " total.");
        } catch (Exception e) {
            System.err.println("Error downloading Fabric libraries: " + e.getMessage());
        }
    }

    /**
     * Downloads a single Maven library if it is not already present on disk.
     *
     * @param name    Maven coordinate string ({@code group:artifact:version})
     * @param baseUrl base URL of the Maven repository (e.g. {@code https://maven.fabricmc.net/})
     * @return {@code true} if the library was downloaded, {@code false} if it
     *         already existed or the download failed
     */
    public boolean downloadLibrary(String name, String baseUrl) {
        try {
            MavenCoordinateParser coords;
            try {
                coords = MavenCoordinateParser.parse(name);
            } catch (IllegalArgumentException e) {
                return false;
            }

            Path libPath = baseDir.resolve("libraries").resolve(coords.getRelativePath());

            if (Files.exists(libPath)) {
                return false; // Already exists
            }

            // Build download URL
            String downloadUrl = baseUrl;
            if (!downloadUrl.endsWith("/")) {
                downloadUrl += "/";
            }
            downloadUrl += coords.getRelativePath();

            Files.createDirectories(libPath.getParent());

            HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                try (InputStream in = response.body()) {
                    Files.copy(in, libPath, StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("Downloaded: " + coords.getFileName());
                return true;
            } else {
                System.err.println("Failed to download " + coords.getFileName()
                        + " (HTTP " + response.statusCode() + ")");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error downloading library " + name + ": " + e.getMessage());
            return false;
        }
    }

}
