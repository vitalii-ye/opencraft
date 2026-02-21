package opencraft.mods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShaderManagerTest {

    private ModManager createModManager(Path tempDir) {
        return new ModManager(tempDir.resolve("mods"), tempDir.resolve("active_mods"));
    }

    @Test
    void getInstalledShadersReturnsEmptyWhenDirNotExists(@TempDir Path tempDir) throws IOException {
        Path shaderpacksDir = tempDir.resolve("shaderpacks");
        ShaderManager manager = new ShaderManager(shaderpacksDir, createModManager(tempDir));

        List<InstalledMod> shaders = manager.getInstalledShaders();
        assertTrue(shaders.isEmpty());
    }

    @Test
    void getInstalledShadersReturnsShaderFiles(@TempDir Path tempDir) throws IOException {
        Path shaderpacksDir = tempDir.resolve("shaderpacks");
        Files.createDirectories(shaderpacksDir);

        Files.write(shaderpacksDir.resolve("BSL_v8.2.09.zip"), new byte[1024]);
        Files.write(shaderpacksDir.resolve("Complementary_v4.7.zip"), new byte[2048]);
        // Non-shader file should be ignored
        Files.write(shaderpacksDir.resolve("readme.txt"), new byte[100]);

        ShaderManager manager = new ShaderManager(shaderpacksDir, createModManager(tempDir));
        List<InstalledMod> shaders = manager.getInstalledShaders();

        assertEquals(2, shaders.size());
    }

    @Test
    void getInstalledShadersEmptyDirectory(@TempDir Path tempDir) throws IOException {
        Path shaderpacksDir = tempDir.resolve("shaderpacks");
        Files.createDirectories(shaderpacksDir);

        ShaderManager manager = new ShaderManager(shaderpacksDir, createModManager(tempDir));
        List<InstalledMod> shaders = manager.getInstalledShaders();
        assertTrue(shaders.isEmpty());
    }

    @Test
    void removeShaderDeletesFile(@TempDir Path tempDir) throws IOException {
        Path shaderpacksDir = tempDir.resolve("shaderpacks");
        Files.createDirectories(shaderpacksDir);

        Path shaderFile = shaderpacksDir.resolve("BSL_v8.2.09.zip");
        Files.write(shaderFile, new byte[100]);

        ShaderManager manager = new ShaderManager(shaderpacksDir, createModManager(tempDir));
        InstalledMod shader = InstalledMod.fromFile(shaderFile, "any", InstalledMod.ModType.SHADER);

        boolean result = manager.removeShader(shader, null);
        assertTrue(result);
        assertFalse(Files.exists(shaderFile));
    }

    @Test
    void removeShaderReturnsFalseWhenFileNotExists(@TempDir Path tempDir) {
        Path shaderpacksDir = tempDir.resolve("shaderpacks");

        ShaderManager manager = new ShaderManager(shaderpacksDir, createModManager(tempDir));
        InstalledMod shader = new InstalledMod("nonexistent.zip", "Test", "1.0",
                shaderpacksDir.resolve("nonexistent.zip"), 0,
                java.time.Instant.now(), "any", InstalledMod.ModType.SHADER);

        boolean result = manager.removeShader(shader, null);
        assertFalse(result);
    }

    @Test
    void isIrisInstalledReturnsFalseWhenNoMods(@TempDir Path tempDir) throws IOException {
        Path shaderpacksDir = tempDir.resolve("shaderpacks");
        ModManager modManager = createModManager(tempDir);

        ShaderManager manager = new ShaderManager(shaderpacksDir, modManager);
        assertFalse(manager.isIrisInstalled("1.21"));
    }

    @Test
    void isIrisInstalledReturnsTrueWhenIrisPresent(@TempDir Path tempDir) throws IOException {
        Path modsBase = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");
        Path versionDir = modsBase.resolve("1.21");
        Files.createDirectories(versionDir);
        Files.write(versionDir.resolve("iris-mc1.21-1.7.0.jar"), new byte[100]);

        ModManager modManager = new ModManager(modsBase, activeModsDir);
        Path shaderpacksDir = tempDir.resolve("shaderpacks");
        ShaderManager manager = new ShaderManager(shaderpacksDir, modManager);

        assertTrue(manager.isIrisInstalled("1.21"));
    }

    @Test
    void isSodiumInstalledReturnsFalseWhenNoMods(@TempDir Path tempDir) throws IOException {
        Path shaderpacksDir = tempDir.resolve("shaderpacks");
        ModManager modManager = createModManager(tempDir);

        ShaderManager manager = new ShaderManager(shaderpacksDir, modManager);
        assertFalse(manager.isSodiumInstalled("1.21"));
    }

    @Test
    void isSodiumInstalledReturnsTrueWhenSodiumPresent(@TempDir Path tempDir) throws IOException {
        Path modsBase = tempDir.resolve("mods");
        Path activeModsDir = tempDir.resolve("active_mods");
        Path versionDir = modsBase.resolve("1.21");
        Files.createDirectories(versionDir);
        Files.write(versionDir.resolve("sodium-fabric-0.5.8+mc1.21.jar"), new byte[100]);

        ModManager modManager = new ModManager(modsBase, activeModsDir);
        Path shaderpacksDir = tempDir.resolve("shaderpacks");
        ShaderManager manager = new ShaderManager(shaderpacksDir, modManager);

        assertTrue(manager.isSodiumInstalled("1.21"));
    }

    @Test
    void isShaderFileAcceptsZip(@TempDir Path tempDir) throws IOException {
        Path zipFile = tempDir.resolve("shader.zip");
        Files.write(zipFile, new byte[10]);
        assertTrue(ShaderManager.isShaderFile(zipFile));
    }

    @Test
    void isShaderFileRejectsJar(@TempDir Path tempDir) throws IOException {
        Path jarFile = tempDir.resolve("mod.jar");
        Files.write(jarFile, new byte[10]);
        assertFalse(ShaderManager.isShaderFile(jarFile));
    }

    @Test
    void isShaderFileRejectsTxt(@TempDir Path tempDir) throws IOException {
        Path txtFile = tempDir.resolve("readme.txt");
        Files.write(txtFile, new byte[10]);
        assertFalse(ShaderManager.isShaderFile(txtFile));
    }

    @Test
    void isShaderFileRejectsDirectory(@TempDir Path tempDir) throws IOException {
        Path dir = tempDir.resolve("shaderdir");
        Files.createDirectories(dir);
        assertFalse(ShaderManager.isShaderFile(dir));
    }

    @Test
    void isShaderFileRejectsNonExistent() {
        Path nonExistent = Path.of("/tmp/nonexistent_shader.zip");
        assertFalse(ShaderManager.isShaderFile(nonExistent));
    }
}
