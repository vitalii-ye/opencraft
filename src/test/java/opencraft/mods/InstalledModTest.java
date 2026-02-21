package opencraft.mods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class InstalledModTest {

    @Test
    void extractsDisplayNameFromJarFilename() {
        InstalledMod mod = new InstalledMod("sodium-fabric-0.5.8+mc1.21.jar",
                "Sodium", "0.5.8", Path.of("/tmp/test.jar"), 1024, Instant.now(), "1.21",
                InstalledMod.ModType.MOD);
        assertEquals("Sodium", mod.getDisplayName());
    }

    @Test
    void extractsVersionFromFilename() {
        InstalledMod mod = new InstalledMod("sodium-fabric-0.5.8+mc1.21.jar",
                "Sodium", "0.5.8", Path.of("/tmp/test.jar"), 1024, Instant.now(), "1.21",
                InstalledMod.ModType.MOD);
        assertEquals("0.5.8", mod.getVersion());
    }

    @Test
    void formattedSizeBytes() {
        InstalledMod mod = new InstalledMod("test.jar", "Test", "1.0",
                Path.of("/tmp/test.jar"), 512, Instant.now(), "1.21", InstalledMod.ModType.MOD);
        assertEquals("512 B", mod.getFormattedSize());
    }

    @Test
    void formattedSizeKilobytes() {
        InstalledMod mod = new InstalledMod("test.jar", "Test", "1.0",
                Path.of("/tmp/test.jar"), 2048, Instant.now(), "1.21", InstalledMod.ModType.MOD);
        assertEquals("2.0 KB", mod.getFormattedSize());
    }

    @Test
    void formattedSizeMegabytes() {
        InstalledMod mod = new InstalledMod("test.jar", "Test", "1.0",
                Path.of("/tmp/test.jar"), 5 * 1024 * 1024, Instant.now(), "1.21",
                InstalledMod.ModType.MOD);
        assertEquals("5.0 MB", mod.getFormattedSize());
    }

    @Test
    void fromFileExtractsInfoFromRealFile(@TempDir Path tempDir) throws IOException {
        Path modFile = tempDir.resolve("sodium-fabric-0.5.8+mc1.21.jar");
        Files.write(modFile, new byte[2048]);

        InstalledMod mod = InstalledMod.fromFile(modFile, "1.21", InstalledMod.ModType.MOD);

        assertEquals("sodium-fabric-0.5.8+mc1.21.jar", mod.getFileName());
        assertEquals("Sodium", mod.getDisplayName());
        assertEquals("0.5.8", mod.getVersion());
        assertEquals(2048, mod.getFileSize());
        assertEquals("1.21", mod.getGameVersion());
        assertEquals(InstalledMod.ModType.MOD, mod.getType());
    }

    @Test
    void fromFileWithNoVersion(@TempDir Path tempDir) throws IOException {
        Path modFile = tempDir.resolve("mymod.jar");
        Files.write(modFile, new byte[100]);

        InstalledMod mod = InstalledMod.fromFile(modFile, "1.20", InstalledMod.ModType.MOD);

        assertEquals("Mymod", mod.getDisplayName());
        assertEquals("Unknown", mod.getVersion());
    }

    @Test
    void fromFileWithZipExtension(@TempDir Path tempDir) throws IOException {
        Path shaderFile = tempDir.resolve("BSL_v8.2.09.zip");
        Files.write(shaderFile, new byte[500]);

        InstalledMod shader = InstalledMod.fromFile(shaderFile, "any", InstalledMod.ModType.SHADER);

        assertEquals("BSL", shader.getDisplayName());
        assertEquals("8.2.09", shader.getVersion());
        assertTrue(shader.isShader());
        assertFalse(shader.isMod());
    }

    @Test
    void isModReturnsTrue() {
        InstalledMod mod = new InstalledMod("test.jar", "Test", "1.0",
                Path.of("/tmp/test.jar"), 100, Instant.now(), "1.21", InstalledMod.ModType.MOD);
        assertTrue(mod.isMod());
        assertFalse(mod.isShader());
    }

    @Test
    void isShaderReturnsTrue() {
        InstalledMod shader = new InstalledMod("test.zip", "Test", "1.0",
                Path.of("/tmp/test.zip"), 100, Instant.now(), "1.21", InstalledMod.ModType.SHADER);
        assertTrue(shader.isShader());
        assertFalse(shader.isMod());
    }

    @Test
    void toStringReturnsDisplayNameAndVersion() {
        InstalledMod mod = new InstalledMod("test.jar", "Sodium", "0.5.8",
                Path.of("/tmp/test.jar"), 100, Instant.now(), "1.21", InstalledMod.ModType.MOD);
        assertEquals("Sodium v0.5.8", mod.toString());
    }

    @Test
    void getFilePathReturnsCorrectPath() {
        Path path = Path.of("/tmp/mods/test.jar");
        InstalledMod mod = new InstalledMod("test.jar", "Test", "1.0",
                path, 100, Instant.now(), "1.21", InstalledMod.ModType.MOD);
        assertEquals(path, mod.getFilePath());
    }

    @Test
    void getInstalledAtReturnsCorrectTime() {
        Instant now = Instant.now();
        InstalledMod mod = new InstalledMod("test.jar", "Test", "1.0",
                Path.of("/tmp/test.jar"), 100, now, "1.21", InstalledMod.ModType.MOD);
        assertEquals(now, mod.getInstalledAt());
    }
}
