package opencraft.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FabricDownloaderTest {

    @Test
    void getFabricVersionIdFormatsCorrectly() {
        String versionId = FabricDownloader.getFabricVersionId("1.21", "0.15.11");
        assertEquals("fabric-loader-0.15.11-1.21", versionId);
    }

    @Test
    void getFabricVersionIdWithDifferentVersions() {
        String versionId = FabricDownloader.getFabricVersionId("1.20.4", "0.15.10");
        assertEquals("fabric-loader-0.15.10-1.20.4", versionId);
    }

    @Test
    void isFabricInstalledReturnsFalseWhenNotInstalled(@TempDir Path tempDir) {
        // FabricDownloader.isFabricInstalled checks MinecraftPathResolver paths,
        // which won't match our tempDir, so it should return false for
        // any non-existent version
        assertFalse(FabricDownloader.isFabricInstalled("99.99.99", "0.0.0"));
    }

    @Test
    void fabricVersionIdFormat() {
        // Verify the format is consistent: fabric-loader-{loaderVersion}-{gameVersion}
        String id = FabricDownloader.getFabricVersionId("1.21", "0.15.11");
        assertTrue(id.startsWith("fabric-loader-"));
        assertTrue(id.contains("0.15.11"));
        assertTrue(id.endsWith("-1.21"));
    }
}
