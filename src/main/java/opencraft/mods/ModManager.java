package opencraft.mods;

import opencraft.network.ModrinthApiClient;
import opencraft.model.ModrinthFile;
import opencraft.model.ModrinthProject;
import opencraft.model.ModrinthVersion;
import opencraft.utils.LogHelper;
import opencraft.utils.MinecraftPathResolver;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Manages mod installation, removal, and discovery for Minecraft.
 * Mods are stored per-version in the mods/{gameVersion}/ directory.
 */
public class ModManager {

    private final Path baseModsDir;
    private final Path activeModsDir;
    private final ModrinthApiClient modrinthClient;

    /**
     * Creates a ModManager using the default Minecraft directory paths.
     */
    public ModManager() {
        this(MinecraftPathResolver.getMinecraftDirectory().resolve("mods"),
             MinecraftPathResolver.getModsDirectory(),
             new ModrinthApiClient());
    }

    /**
     * Creates a ModManager with custom directories for testability.
     *
     * @param baseModsDir   Root directory for per-version mod storage (contains version subdirectories)
     * @param activeModsDir Directory where active mods are placed for the running game
     */
    public ModManager(Path baseModsDir, Path activeModsDir) {
        this(baseModsDir, activeModsDir, new ModrinthApiClient());
    }

    /**
     * Creates a ModManager with custom directories and a custom API client for testability.
     *
     * @param baseModsDir    Root directory for per-version mod storage
     * @param activeModsDir  Directory where active mods are placed for the running game
     * @param modrinthClient Modrinth API client instance
     */
    public ModManager(Path baseModsDir, Path activeModsDir, ModrinthApiClient modrinthClient) {
        this.baseModsDir = baseModsDir;
        this.activeModsDir = activeModsDir;
        this.modrinthClient = modrinthClient;
    }

    /**
     * Returns the mods directory for a specific game version.
     */
    public Path getModsDirectory(String gameVersion) {
        return baseModsDir.resolve(gameVersion);
    }

    /**
     * Returns the active mods directory.
     */
    public Path getActiveModsDirectory() {
        return activeModsDir;
    }

    /**
     * Gets all installed mods for a specific game version.
     */
    public List<InstalledMod> getInstalledMods(String gameVersion) throws IOException {
        Path modsDir = getModsDirectory(gameVersion);
        List<InstalledMod> mods = new ArrayList<>();

        if (!Files.exists(modsDir)) {
            return mods;
        }

        try (Stream<Path> files = Files.list(modsDir)) {
            files.filter(ModManager::isModFile)
                 .forEach(file -> mods.add(InstalledMod.fromFile(file, gameVersion, InstalledMod.ModType.MOD)));
        }

        return mods;
    }

    /**
     * Installs a mod from Modrinth for a specific game version.
     * 
     * @param project     The Modrinth project to install
     * @param gameVersion The game version to install for
     * @param logger      Progress logger
     * @return The installed mod, or null if installation failed
     */
    public InstalledMod installMod(ModrinthProject project, String gameVersion, Consumer<String> logger)
            throws IOException, InterruptedException {
        
        LogHelper.log(logger, "Finding compatible version of " + project.getTitle() + "...");

        // Get versions compatible with the game version and Fabric
        List<ModrinthVersion> versions = modrinthClient.getProjectVersions(
                project.getId(), gameVersion, "fabric");

        if (versions.isEmpty()) {
            throw new IOException("No compatible version found for " + project.getTitle() + 
                    " (Minecraft " + gameVersion + ", Fabric)");
        }

        // Use the first (latest) compatible version
        ModrinthVersion version = versions.get(0);
        ModrinthFile file = version.getPrimaryFile();

        if (file == null) {
            throw new IOException("No downloadable file found for " + project.getTitle());
        }

        // Download to the version-specific mods directory
        Path modsDir = getModsDirectory(gameVersion);
        Files.createDirectories(modsDir);

        Path destination = modsDir.resolve(file.getFilename());
        
        // Check if already installed
        if (Files.exists(destination)) {
            LogHelper.log(logger, project.getTitle() + " is already installed.");
            return InstalledMod.fromFile(destination, gameVersion, InstalledMod.ModType.MOD);
        }

        // Download the mod
        LogHelper.log(logger, "Downloading " + project.getTitle() + " v" + version.getVersionNumber() + "...");
        modrinthClient.downloadFile(file, destination, logger);

        // Also copy to active mods directory for immediate use
        copyToActiveModsDir(destination, logger);

        LogHelper.log(logger, "Installed " + project.getTitle() + " v" + version.getVersionNumber());

        return InstalledMod.fromFile(destination, gameVersion, InstalledMod.ModType.MOD);
    }

    /**
     * Removes an installed mod.
     */
    public boolean removeMod(InstalledMod mod, Consumer<String> logger) {
        try {
            Path filePath = mod.getFilePath();
            
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                LogHelper.log(logger, "Removed " + mod.getDisplayName());
            }

            // Also remove from active mods directory if present
            Path activeModPath = activeModsDir.resolve(mod.getFileName());
            if (Files.exists(activeModPath)) {
                Files.delete(activeModPath);
            }

            return true;
        } catch (IOException e) {
            LogHelper.log(logger, "Failed to remove " + mod.getDisplayName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Syncs mods from a version-specific directory to the active mods directory.
     * This is called before launching the game to ensure the correct mods are loaded.
     */
    public void syncModsForVersion(String gameVersion, Consumer<String> logger) throws IOException {
        Path versionModsDir = getModsDirectory(gameVersion);
        syncModsToDirectory(versionModsDir, activeModsDir, gameVersion, logger);
    }

    /**
     * Syncs mods from a version-specific directory to a target directory.
     * This is called before launching the game to ensure the correct mods are loaded.
     */
    public void syncModsToDirectory(String gameVersion, Path targetDir, Consumer<String> logger) throws IOException {
        Path versionModsDir = getModsDirectory(gameVersion);
        syncModsToDirectory(versionModsDir, targetDir, gameVersion, logger);
    }

    /**
     * Internal method to sync mods from source to target directory.
     */
    private void syncModsToDirectory(Path sourceModsDir, Path targetModsDir, String gameVersion, Consumer<String> logger) throws IOException {
        // Create directories if they don't exist
        Files.createDirectories(sourceModsDir);
        Files.createDirectories(targetModsDir);

        // Clear target mods directory
        try (Stream<Path> files = Files.list(targetModsDir)) {
            files.filter(ModManager::isModFile)
                 .forEach(file -> {
                     try {
                         Files.delete(file);
                     } catch (IOException e) {
                         System.err.println("Failed to delete " + file + ": " + e.getMessage());
                     }
                 });
        }

        // Copy mods from version-specific directory
        if (Files.exists(sourceModsDir)) {
            try (Stream<Path> files = Files.list(sourceModsDir)) {
                files.filter(ModManager::isModFile)
                     .forEach(file -> {
                         try {
                             Path dest = targetModsDir.resolve(file.getFileName());
                             Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                              LogHelper.log(logger, "Synced: " + file.getFileName());
                         } catch (IOException e) {
                             System.err.println("Failed to copy " + file + ": " + e.getMessage());
                         }
                     });
            }
        }

        LogHelper.log(logger, "Mods synced for version " + gameVersion);
    }

    /**
     * Checks if a mod is installed for a specific version.
     */
    public boolean isModInstalled(String modFileName, String gameVersion) {
        Path modPath = getModsDirectory(gameVersion).resolve(modFileName);
        return Files.exists(modPath);
    }

    /**
     * Searches for mods on Modrinth.
     */
    public List<ModrinthProject> searchMods(String query, String gameVersion) 
            throws IOException, InterruptedException {
        return modrinthClient.searchMods(query, gameVersion, "fabric", 20);
    }

    /**
     * Copies a mod file to the active mods directory.
     */
    private void copyToActiveModsDir(Path modFile, Consumer<String> logger) {
        try {
            Files.createDirectories(activeModsDir);
            Path dest = activeModsDir.resolve(modFile.getFileName());
            Files.copy(modFile, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LogHelper.log(logger, "Note: Could not copy to active mods directory: " + e.getMessage());
        }
    }

    /**
     * Checks if a file is a mod file (.jar).
     */
    static boolean isModFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".jar");
    }

}
