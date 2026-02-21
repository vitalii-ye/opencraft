package opencraft.utils;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftPathResolverTest {

    @Test
    void minecraftDirectoryIsNotNull() {
        Path dir = MinecraftPathResolver.getMinecraftDirectory();
        assertNotNull(dir);
    }

    @Test
    void minecraftDirectoryContainsMinecraftSegment() {
        Path dir = MinecraftPathResolver.getMinecraftDirectory();
        String pathStr = dir.toString().toLowerCase();
        assertTrue(pathStr.contains("minecraft"),
                "Minecraft directory should contain 'minecraft' in path: " + pathStr);
    }

    @Test
    void screenshotsDirectoryIsSubdirectory() {
        Path base = MinecraftPathResolver.getMinecraftDirectory();
        Path screenshots = MinecraftPathResolver.getScreenshotsDirectory();

        assertTrue(screenshots.startsWith(base));
        assertTrue(screenshots.endsWith("screenshots"));
    }

    @Test
    void versionsDirectoryIsSubdirectory() {
        Path base = MinecraftPathResolver.getMinecraftDirectory();
        Path versions = MinecraftPathResolver.getVersionsDirectory();

        assertTrue(versions.startsWith(base));
        assertTrue(versions.endsWith("versions"));
    }

    @Test
    void librariesDirectoryIsSubdirectory() {
        Path base = MinecraftPathResolver.getMinecraftDirectory();
        Path libraries = MinecraftPathResolver.getLibrariesDirectory();

        assertTrue(libraries.startsWith(base));
        assertTrue(libraries.endsWith("libraries"));
    }

    @Test
    void assetsDirectoryIsSubdirectory() {
        Path base = MinecraftPathResolver.getMinecraftDirectory();
        Path assets = MinecraftPathResolver.getAssetsDirectory();

        assertTrue(assets.startsWith(base));
        assertTrue(assets.endsWith("assets"));
    }

    @Test
    void modsDirectoryWithVersionIsSubdirectory() {
        Path base = MinecraftPathResolver.getMinecraftDirectory();
        Path modsDir = MinecraftPathResolver.getModsDirectory("1.21");

        assertTrue(modsDir.startsWith(base));
        assertTrue(modsDir.toString().contains("mods"));
        assertTrue(modsDir.toString().endsWith("1.21"));
    }

    @Test
    void activeModsDirectoryIsSubdirectory() {
        Path base = MinecraftPathResolver.getMinecraftDirectory();
        Path mods = MinecraftPathResolver.getModsDirectory();

        assertTrue(mods.startsWith(base));
        assertTrue(mods.endsWith("mods"));
    }

    @Test
    void shaderpacksDirectoryIsSubdirectory() {
        Path base = MinecraftPathResolver.getMinecraftDirectory();
        Path shaderpacks = MinecraftPathResolver.getShaderpacksDirectory();

        assertTrue(shaderpacks.startsWith(base));
        assertTrue(shaderpacks.endsWith("shaderpacks"));
    }

    @Test
    void fabricVersionsDirectoryMatchesVersionsDirectory() {
        Path versions = MinecraftPathResolver.getVersionsDirectory();
        Path fabricVersions = MinecraftPathResolver.getFabricVersionsDirectory();

        assertEquals(versions, fabricVersions);
    }

    @Test
    void directoryPathStructureForCurrentOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        Path dir = MinecraftPathResolver.getMinecraftDirectory();

        if (osName.contains("mac") || osName.contains("darwin")) {
            assertTrue(dir.toString().contains("Application Support"));
        } else if (osName.contains("win")) {
            assertTrue(dir.toString().contains(".minecraft"));
        } else {
            assertTrue(dir.toString().contains(".minecraft"));
        }
    }
}
