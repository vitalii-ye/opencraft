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
 * Manages mod installation, removal, and discovery for Minecraft.
 * Mods are stored per-version in the mods/{gameVersion}/ directory.
 */
public class ModManager {

    /**
     * Gets all installed mods for a specific game version.
     */
    public static List<InstalledMod> getInstalledMods(String gameVersion) throws IOException {
        Path modsDir = MinecraftPathResolver.getModsDirectory(gameVersion);
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
     * Gets all installed mods in the active mods directory.
     */
    public static List<InstalledMod> getActiveInstalledMods() throws IOException {
        Path modsDir = MinecraftPathResolver.getModsDirectory();
        List<InstalledMod> mods = new ArrayList<>();

        if (!Files.exists(modsDir)) {
            return mods;
        }

        try (Stream<Path> files = Files.list(modsDir)) {
            files.filter(ModManager::isModFile)
                 .forEach(file -> mods.add(InstalledMod.fromFile(file, "active", InstalledMod.ModType.MOD)));
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
    public static InstalledMod installMod(ModrinthProject project, String gameVersion, Consumer<String> logger)
            throws IOException, InterruptedException {
        
        log(logger, "Finding compatible version of " + project.getTitle() + "...");

        // Get versions compatible with the game version and Fabric
        List<ModrinthVersion> versions = ModrinthApiClient.getProjectVersions(
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
        Path modsDir = MinecraftPathResolver.getModsDirectory(gameVersion);
        Files.createDirectories(modsDir);

        Path destination = modsDir.resolve(file.getFilename());
        
        // Check if already installed
        if (Files.exists(destination)) {
            log(logger, project.getTitle() + " is already installed.");
            return InstalledMod.fromFile(destination, gameVersion, InstalledMod.ModType.MOD);
        }

        // Download the mod
        log(logger, "Downloading " + project.getTitle() + " v" + version.getVersionNumber() + "...");
        ModrinthApiClient.downloadFile(file, destination, logger);

        // Also copy to active mods directory for immediate use
        copyToActiveModsDir(destination, logger);

        log(logger, "Installed " + project.getTitle() + " v" + version.getVersionNumber());

        return InstalledMod.fromFile(destination, gameVersion, InstalledMod.ModType.MOD);
    }

    /**
     * Installs a mod from a specific version.
     */
    public static InstalledMod installModVersion(ModrinthVersion version, String gameVersion, Consumer<String> logger)
            throws IOException, InterruptedException {
        
        ModrinthFile file = version.getPrimaryFile();
        if (file == null) {
            throw new IOException("No downloadable file found");
        }

        Path modsDir = MinecraftPathResolver.getModsDirectory(gameVersion);
        Files.createDirectories(modsDir);

        Path destination = modsDir.resolve(file.getFilename());
        
        log(logger, "Downloading " + file.getFilename() + "...");
        ModrinthApiClient.downloadFile(file, destination, logger);

        copyToActiveModsDir(destination, logger);

        return InstalledMod.fromFile(destination, gameVersion, InstalledMod.ModType.MOD);
    }

    /**
     * Removes an installed mod.
     */
    public static boolean removeMod(InstalledMod mod, Consumer<String> logger) {
        try {
            Path filePath = mod.getFilePath();
            
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log(logger, "Removed " + mod.getDisplayName());
            }

            // Also remove from active mods directory if present
            Path activeModPath = MinecraftPathResolver.getModsDirectory().resolve(mod.getFileName());
            if (Files.exists(activeModPath)) {
                Files.delete(activeModPath);
            }

            return true;
        } catch (IOException e) {
            log(logger, "Failed to remove " + mod.getDisplayName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Syncs mods from a version-specific directory to the active mods directory.
     * This is called before launching the game to ensure the correct mods are loaded.
     */
    public static void syncModsForVersion(String gameVersion, Consumer<String> logger) throws IOException {
        Path versionModsDir = MinecraftPathResolver.getModsDirectory(gameVersion);
        Path activeModsDir = MinecraftPathResolver.getModsDirectory();
        syncModsToDirectory(versionModsDir, activeModsDir, gameVersion, logger);
    }

    /**
     * Syncs mods from a version-specific directory to a target directory.
     * This is called before launching the game to ensure the correct mods are loaded.
     */
    public static void syncModsToDirectory(String gameVersion, Path targetDir, Consumer<String> logger) throws IOException {
        Path versionModsDir = MinecraftPathResolver.getModsDirectory(gameVersion);
        syncModsToDirectory(versionModsDir, targetDir, gameVersion, logger);
    }

    /**
     * Internal method to sync mods from source to target directory.
     */
    private static void syncModsToDirectory(Path sourceModsDir, Path targetModsDir, String gameVersion, Consumer<String> logger) throws IOException {
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
                             log(logger, "Synced: " + file.getFileName());
                         } catch (IOException e) {
                             System.err.println("Failed to copy " + file + ": " + e.getMessage());
                         }
                     });
            }
        }

        log(logger, "Mods synced for version " + gameVersion);
    }

    /**
     * Checks if a mod is installed for a specific version.
     */
    public static boolean isModInstalled(String modFileName, String gameVersion) {
        Path modPath = MinecraftPathResolver.getModsDirectory(gameVersion).resolve(modFileName);
        return Files.exists(modPath);
    }

    /**
     * Searches for mods on Modrinth.
     */
    public static List<ModrinthProject> searchMods(String query, String gameVersion) 
            throws IOException, InterruptedException {
        return ModrinthApiClient.searchMods(query, gameVersion, "fabric", 20);
    }

    /**
     * Copies a mod file to the active mods directory.
     */
    private static void copyToActiveModsDir(Path modFile, Consumer<String> logger) {
        try {
            Path activeDir = MinecraftPathResolver.getModsDirectory();
            Files.createDirectories(activeDir);
            Path dest = activeDir.resolve(modFile.getFileName());
            Files.copy(modFile, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log(logger, "Note: Could not copy to active mods directory: " + e.getMessage());
        }
    }

    /**
     * Checks if a file is a mod file (.jar).
     */
    private static boolean isModFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".jar");
    }

    private static void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        } else {
            System.out.println(message);
        }
    }
}
