package opencraft.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MinecraftPathResolver {

    public static Path getMinecraftDirectory() {
        String osName = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (osName.contains("mac") || osName.contains("darwin")) {
            return Paths.get(userHome, "Library", "Application Support", "minecraft");
        } else if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                return Paths.get(appData, ".minecraft");
            }
            return Paths.get(userHome, "AppData", "Roaming", ".minecraft");
        } else {
            // Linux and other Unix-like systems
            return Paths.get(userHome, ".minecraft");
        }
    }

    public static Path getScreenshotsDirectory() {
        return getMinecraftDirectory().resolve("screenshots");
    }

    public static Path getVersionsDirectory() {
        return getMinecraftDirectory().resolve("versions");
    }

    public static Path getLibrariesDirectory() {
        return getMinecraftDirectory().resolve("libraries");
    }
    
    public static Path getAssetsDirectory() {
        return getMinecraftDirectory().resolve("assets");
    }

    /**
     * Returns the mods directory for a specific game version.
     * Mods are stored per-version to ensure compatibility.
     */
    public static Path getModsDirectory(String gameVersion) {
        return getMinecraftDirectory().resolve("mods").resolve(gameVersion);
    }

    /**
     * Returns the active mods directory (used by Minecraft at runtime).
     */
    public static Path getModsDirectory() {
        return getMinecraftDirectory().resolve("mods");
    }

    /**
     * Returns the shaderpacks directory.
     */
    public static Path getShaderpacksDirectory() {
        return getMinecraftDirectory().resolve("shaderpacks");
    }

    /**
     * Returns the Fabric versions directory.
     */
    public static Path getFabricVersionsDirectory() {
        return getVersionsDirectory();
    }
}
