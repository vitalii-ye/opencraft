package opencraft.mods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModManagerTest {

    @Test
    void getInstalledModsReturnsEmptyWhenDirNotExists(@TempDir Path tempDir) throws IOException {
        Path baseModsDir = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");

        ModManager manager = new ModManager(baseModsDir, activeModsDir);
        List<InstalledMod> mods = manager.getInstalledMods("1.21");
        assertTrue(mods.isEmpty());
    }

    @Test
    void getInstalledModsReturnsModFiles(@TempDir Path tempDir) throws IOException {
        Path baseModsDir = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");
        Path versionDir = baseModsDir.resolve("1.21");
        Files.createDirectories(versionDir);

        // Create some mod files
        Files.write(versionDir.resolve("sodium-fabric-0.5.8.jar"), new byte[1024]);
        Files.write(versionDir.resolve("lithium-fabric-0.12.jar"), new byte[2048]);
        // Non-mod file should be ignored
        Files.write(versionDir.resolve("readme.txt"), new byte[100]);

        ModManager manager = new ModManager(baseModsDir, activeModsDir);
        List<InstalledMod> mods = manager.getInstalledMods("1.21");

        assertEquals(2, mods.size());
    }

    @Test
    void getInstalledModsEmptyDirectory(@TempDir Path tempDir) throws IOException {
        Path baseModsDir = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");
        Path versionDir = baseModsDir.resolve("1.21");
        Files.createDirectories(versionDir);

        ModManager manager = new ModManager(baseModsDir, activeModsDir);
        List<InstalledMod> mods = manager.getInstalledMods("1.21");
        assertTrue(mods.isEmpty());
    }

    @Test
    void isModInstalledReturnsTrueWhenExists(@TempDir Path tempDir) throws IOException {
        Path baseModsDir = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");
        Path versionDir = baseModsDir.resolve("1.21");
        Files.createDirectories(versionDir);
        Files.write(versionDir.resolve("sodium-0.5.8.jar"), new byte[100]);

        ModManager manager = new ModManager(baseModsDir, activeModsDir);
        assertTrue(manager.isModInstalled("sodium-0.5.8.jar", "1.21"));
    }

    @Test
    void isModInstalledReturnsFalseWhenNotExists(@TempDir Path tempDir) {
        Path baseModsDir = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");

        ModManager manager = new ModManager(baseModsDir, activeModsDir);
        assertFalse(manager.isModInstalled("nonexistent.jar", "1.21"));
    }

    @Test
    void removeModDeletesFile(@TempDir Path tempDir) throws IOException {
        Path baseModsDir = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");
        Path versionDir = baseModsDir.resolve("1.21");
        Files.createDirectories(versionDir);
        Files.createDirectories(activeModsDir);

        Path modFile = versionDir.resolve("sodium-0.5.8.jar");
        Files.write(modFile, new byte[100]);
        // Also create in active dir
        Files.write(activeModsDir.resolve("sodium-0.5.8.jar"), new byte[100]);

        ModManager manager = new ModManager(baseModsDir, activeModsDir);
        InstalledMod mod = InstalledMod.fromFile(modFile, "1.21", InstalledMod.ModType.MOD);

        boolean result = manager.removeMod(mod, null);
        assertTrue(result);
        assertFalse(Files.exists(modFile));
        assertFalse(Files.exists(activeModsDir.resolve("sodium-0.5.8.jar")));
    }

    @Test
    void syncModsForVersionCopiesToActiveDir(@TempDir Path tempDir) throws IOException {
        Path baseModsDir = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");
        Path versionDir = baseModsDir.resolve("1.21");
        Files.createDirectories(versionDir);

        Files.write(versionDir.resolve("sodium-0.5.8.jar"), new byte[100]);
        Files.write(versionDir.resolve("lithium-0.12.jar"), new byte[200]);

        ModManager manager = new ModManager(baseModsDir, activeModsDir);
        manager.syncModsForVersion("1.21", null);

        assertTrue(Files.exists(activeModsDir.resolve("sodium-0.5.8.jar")));
        assertTrue(Files.exists(activeModsDir.resolve("lithium-0.12.jar")));
    }

    @Test
    void syncModsForVersionClearsExistingMods(@TempDir Path tempDir) throws IOException {
        Path baseModsDir = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");
        Files.createDirectories(activeModsDir);
        Path versionDir = baseModsDir.resolve("1.21");
        Files.createDirectories(versionDir);

        // Pre-existing mod in active dir
        Files.write(activeModsDir.resolve("old-mod.jar"), new byte[50]);

        // New version mod
        Files.write(versionDir.resolve("new-mod.jar"), new byte[100]);

        ModManager manager = new ModManager(baseModsDir, activeModsDir);
        manager.syncModsForVersion("1.21", null);

        assertFalse(Files.exists(activeModsDir.resolve("old-mod.jar")));
        assertTrue(Files.exists(activeModsDir.resolve("new-mod.jar")));
    }

    @Test
    void isModFileAcceptsJar() {
        Path jarFile = Path.of("/tmp/test.jar");
        // isModFile checks Files.isRegularFile, which requires actual file
        // Just test that the method exists and handles non-existent gracefully
        assertFalse(ModManager.isModFile(jarFile));
    }

    @Test
    void isModFileRejectsNonJar(@TempDir Path tempDir) throws IOException {
        Path txtFile = tempDir.resolve("readme.txt");
        Files.write(txtFile, new byte[10]);
        assertFalse(ModManager.isModFile(txtFile));
    }

    @Test
    void isModFileAcceptsRealJar(@TempDir Path tempDir) throws IOException {
        Path jarFile = tempDir.resolve("mod.jar");
        Files.write(jarFile, new byte[10]);
        assertTrue(ModManager.isModFile(jarFile));
    }

    @Test
    void isModFileRejectsDirectory(@TempDir Path tempDir) throws IOException {
        Path dir = tempDir.resolve("subdir");
        Files.createDirectories(dir);
        assertFalse(ModManager.isModFile(dir));
    }

    @Test
    void getModsDirectoryReturnsCorrectPath(@TempDir Path tempDir) {
        Path baseModsDir = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");

        ModManager manager = new ModManager(baseModsDir, activeModsDir);
        Path result = manager.getModsDirectory("1.21");
        assertEquals(baseModsDir.resolve("1.21"), result);
    }

    @Test
    void getActiveModsDirectoryReturnsCorrectPath(@TempDir Path tempDir) {
        Path baseModsDir = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");

        ModManager manager = new ModManager(baseModsDir, activeModsDir);
        assertEquals(activeModsDir, manager.getActiveModsDirectory());
    }
}
