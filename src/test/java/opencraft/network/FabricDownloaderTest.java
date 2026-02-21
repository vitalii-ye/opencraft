package opencraft.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FabricDownloaderTest {

    @Test
    void getFabricVersionIdFormatsCorrectly() {
        FabricDownloader downloader = new FabricDownloader();
        String versionId = downloader.getFabricVersionId("1.21", "0.15.11");
        assertEquals("fabric-loader-0.15.11-1.21", versionId);
    }

    @Test
    void getFabricVersionIdWithDifferentVersions() {
        FabricDownloader downloader = new FabricDownloader();
        String versionId = downloader.getFabricVersionId("1.20.4", "0.15.10");
        assertEquals("fabric-loader-0.15.10-1.20.4", versionId);
    }

    @Test
    void isFabricInstalledReturnsFalseWhenNotInstalled(@TempDir Path tempDir) {
        // Use a custom base directory so the check operates on tempDir,
        // which never contains a Fabric installation.
        FabricDownloader downloader = new FabricDownloader(tempDir);
        assertFalse(downloader.isFabricInstalled("99.99.99", "0.0.0"));
    }

    @Test
    void fabricVersionIdFormat() {
        FabricDownloader downloader = new FabricDownloader();
        // Verify the format is consistent: fabric-loader-{loaderVersion}-{gameVersion}
        String id = downloader.getFabricVersionId("1.21", "0.15.11");
        assertTrue(id.startsWith("fabric-loader-"));
        assertTrue(id.contains("0.15.11"));
        assertTrue(id.endsWith("-1.21"));
    }
}
