package opencraft.network;

import opencraft.network.FabricVersionManager.FabricGameVersion;
import opencraft.network.FabricVersionManager.FabricLoaderVersion;
import opencraft.network.FabricVersionManager.FabricVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FabricVersionManagerTest {

    // --- FabricLoaderVersion tests ---

    @Test
    void loaderVersionGetters() {
        FabricLoaderVersion loader = new FabricLoaderVersion("0.15.11", true);
        assertEquals("0.15.11", loader.getVersion());
        assertTrue(loader.isStable());
    }

    @Test
    void unstableLoaderVersion() {
        FabricLoaderVersion loader = new FabricLoaderVersion("0.16.0-beta.1", false);
        assertEquals("0.16.0-beta.1", loader.getVersion());
        assertFalse(loader.isStable());
    }

    @Test
    void loaderVersionToStringStable() {
        FabricLoaderVersion loader = new FabricLoaderVersion("0.15.11", true);
        assertEquals("0.15.11", loader.toString());
    }

    @Test
    void loaderVersionToStringUnstable() {
        FabricLoaderVersion loader = new FabricLoaderVersion("0.16.0-beta.1", false);
        assertEquals("0.16.0-beta.1 (unstable)", loader.toString());
    }

    // --- FabricGameVersion tests ---

    @Test
    void gameVersionGetters() {
        FabricGameVersion game = new FabricGameVersion("1.21", true);
        assertEquals("1.21", game.getVersion());
        assertTrue(game.isStable());
    }

    @Test
    void gameVersionUnstable() {
        FabricGameVersion game = new FabricGameVersion("24w21a", false);
        assertEquals("24w21a", game.getVersion());
        assertFalse(game.isStable());
    }

    // --- FabricVersion tests ---

    @Test
    void fabricVersionId() {
        FabricVersion version = new FabricVersion("1.21", "0.15.11");
        assertEquals("fabric-loader-0.15.11-1.21", version.getVersionId());
    }

    @Test
    void fabricVersionDisplayName() {
        FabricVersion version = new FabricVersion("1.21", "0.15.11");
        assertEquals("1.21 [Fabric]", version.getDisplayName());
    }

    @Test
    void fabricVersionGetters() {
        FabricVersion version = new FabricVersion("1.20.6", "0.15.10");
        assertEquals("1.20.6", version.getGameVersion());
        assertEquals("0.15.10", version.getLoaderVersion());
    }

    @Test
    void fabricVersionProfileUrl() {
        FabricVersion version = new FabricVersion("1.21", "0.15.11");
        String url = version.getProfileUrl();
        assertEquals("https://meta.fabricmc.net/v2/versions/loader/1.21/0.15.11/profile/json", url);
    }

    @Test
    void fabricVersionWithDifferentGameVersions() {
        FabricVersion v1 = new FabricVersion("1.21", "0.15.11");
        FabricVersion v2 = new FabricVersion("1.20.4", "0.15.11");

        assertNotEquals(v1.getVersionId(), v2.getVersionId());
        assertEquals(v1.getLoaderVersion(), v2.getLoaderVersion());
    }

    @Test
    void fabricVersionWithDifferentLoaderVersions() {
        FabricVersion v1 = new FabricVersion("1.21", "0.15.11");
        FabricVersion v2 = new FabricVersion("1.21", "0.15.10");

        assertNotEquals(v1.getVersionId(), v2.getVersionId());
        assertEquals(v1.getGameVersion(), v2.getGameVersion());
    }
}
