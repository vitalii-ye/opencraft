package opencraft.mods;

import opencraft.network.ModrinthApiClient;
import opencraft.network.ModrinthApiClient.ModrinthFile;
import opencraft.network.ModrinthApiClient.ModrinthProject;
import opencraft.network.ModrinthApiClient.ModrinthVersion;
import opencraft.utils.MinecraftPathResolver;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Manages shader installation, removal, and discovery for Minecraft.
 * Shaders require Iris mod which will be auto-installed when needed.
 */
public class ShaderManager {

    private static final String IRIS_PROJECT_SLUG = "iris";
    private static final String SODIUM_PROJECT_SLUG = "sodium"; // Iris dependency

    private final Path shaderpacksDir;
    private final ModManager modManager;

    /**
     * Creates a ShaderManager using default Minecraft directory paths.
     */
    public ShaderManager() {
        this(MinecraftPathResolver.getShaderpacksDirectory(), new ModManager());
    }

    /**
     * Creates a ShaderManager with custom directories for testability.
     *
     * @param shaderpacksDir Directory where shader packs are stored
     * @param modManager     ModManager instance for Iris/Sodium installation
     */
    public ShaderManager(Path shaderpacksDir, ModManager modManager) {
        this.shaderpacksDir = shaderpacksDir;
        this.modManager = modManager;
    }

    /**
     * Gets all installed shader packs.
     */
    public List<InstalledMod> getInstalledShaders() throws IOException {
        List<InstalledMod> shaders = new ArrayList<>();

        if (!Files.exists(shaderpacksDir)) {
            return shaders;
        }

        try (Stream<Path> files = Files.list(shaderpacksDir)) {
            files.filter(ShaderManager::isShaderFile)
                 .forEach(file -> shaders.add(InstalledMod.fromFile(file, "any", InstalledMod.ModType.SHADER)));
        }

        return shaders;
    }

    /**
     * Ensures Iris shader mod is installed for the given game version.
     * Also installs Sodium (Iris dependency) if not present.
     * 
     * @param gameVersion The Minecraft version
     * @param logger      Progress logger
     * @return true if Iris is installed (either already present or newly installed)
     */
    public boolean ensureIrisInstalled(String gameVersion, Consumer<String> logger)
            throws IOException, InterruptedException {
        
        // Check if Iris is already installed
        if (isIrisInstalled(gameVersion)) {
            log(logger, "Iris is already installed for " + gameVersion);
            return true;
        }

        log(logger, "Installing Iris shader mod...");

        // First install Sodium (Iris dependency)
        if (!isSodiumInstalled(gameVersion)) {
            log(logger, "Installing Sodium (Iris dependency)...");
            try {
                ModrinthProject sodiumProject = ModrinthApiClient.getProject(SODIUM_PROJECT_SLUG);
                modManager.installMod(sodiumProject, gameVersion, logger);
            } catch (Exception e) {
                log(logger, "Warning: Could not install Sodium: " + e.getMessage());
                // Continue anyway, Iris might work without it in some versions
            }
        }

        // Install Iris
        try {
            ModrinthProject irisProject = ModrinthApiClient.getIrisProject();
            modManager.installMod(irisProject, gameVersion, logger);
            log(logger, "Iris installed successfully!");
            return true;
        } catch (Exception e) {
            log(logger, "Failed to install Iris: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if Iris is installed for the given game version.
     */
    public boolean isIrisInstalled(String gameVersion) throws IOException {
        List<InstalledMod> mods = modManager.getInstalledMods(gameVersion);
        for (InstalledMod mod : mods) {
            String name = mod.getFileName().toLowerCase();
            if (name.contains("iris")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if Sodium is installed for the given game version.
     */
    public boolean isSodiumInstalled(String gameVersion) throws IOException {
        List<InstalledMod> mods = modManager.getInstalledMods(gameVersion);
        for (InstalledMod mod : mods) {
            String name = mod.getFileName().toLowerCase();
            if (name.contains("sodium")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Installs a shader pack from Modrinth.
     * Automatically installs Iris if not present.
     * 
     * @param shaderProject The shader project to install
     * @param gameVersion   The game version (for Iris installation)
     * @param logger        Progress logger
     * @return The installed shader, or null if installation failed
     */
    public InstalledMod installShader(ModrinthProject shaderProject, String gameVersion, Consumer<String> logger)
            throws IOException, InterruptedException {
        
        // Ensure Iris is installed first
        if (!ensureIrisInstalled(gameVersion, logger)) {
            throw new IOException("Failed to install Iris shader mod. Cannot proceed with shader installation.");
        }

        log(logger, "Finding compatible version of " + shaderProject.getTitle() + "...");

        // Get shader versions (shaders don't usually have loader-specific versions)
        List<ModrinthVersion> versions = ModrinthApiClient.getProjectVersions(
                shaderProject.getId(), gameVersion, null);

        // If no version for specific game version, try without version filter
        if (versions.isEmpty()) {
            versions = ModrinthApiClient.getProjectVersions(shaderProject.getId(), null, null);
        }

        if (versions.isEmpty()) {
            throw new IOException("No downloadable version found for " + shaderProject.getTitle());
        }

        // Use the first (latest) version
        ModrinthVersion version = versions.get(0);
        ModrinthFile file = version.getPrimaryFile();

        if (file == null) {
            throw new IOException("No downloadable file found for " + shaderProject.getTitle());
        }

        // Download to shaderpacks directory
        Files.createDirectories(shaderpacksDir);

        Path destination = shaderpacksDir.resolve(file.getFilename());

        // Check if already installed
        if (Files.exists(destination)) {
            log(logger, shaderProject.getTitle() + " is already installed.");
            return InstalledMod.fromFile(destination, gameVersion, InstalledMod.ModType.SHADER);
        }

        // Download the shader
        log(logger, "Downloading " + shaderProject.getTitle() + " v" + version.getVersionNumber() + "...");
        ModrinthApiClient.downloadFile(file, destination, logger);

        log(logger, "Installed shader: " + shaderProject.getTitle());

        return InstalledMod.fromFile(destination, gameVersion, InstalledMod.ModType.SHADER);
    }

    /**
     * Removes an installed shader.
     */
    public boolean removeShader(InstalledMod shader, Consumer<String> logger) {
        try {
            Path filePath = shader.getFilePath();
            
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log(logger, "Removed shader: " + shader.getDisplayName());
                return true;
            }

            return false;
        } catch (IOException e) {
            log(logger, "Failed to remove shader: " + e.getMessage());
            return false;
        }
    }

    /**
     * Searches for shaders on Modrinth.
     */
    public List<ModrinthProject> searchShaders(String query, String gameVersion) 
            throws IOException, InterruptedException {
        return ModrinthApiClient.searchShaders(query, gameVersion, 20);
    }

    /**
     * Checks if a file is a shader file (.zip or folder).
     */
    static boolean isShaderFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        // Shader packs are usually .zip files
        return Files.isRegularFile(file) && name.endsWith(".zip");
    }

    private static void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        } else {
            System.out.println(message);
        }
    }
}
