package opencraft.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationManagerTest {

    @Test
    void defaultUsernameWhenNoConfig(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("launcher_options.txt");
        ConfigurationManager config = new ConfigurationManager(configPath);

        assertEquals("OpenCitizen", config.getUsername());
    }

    @Test
    void setAndGetUsername(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("launcher_options.txt");
        ConfigurationManager config = new ConfigurationManager(configPath);

        config.setUsername("TestPlayer");
        assertEquals("TestPlayer", config.getUsername());
    }

    @Test
    void saveAndLoadRoundTrip(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("launcher_options.txt");

        // Save
        ConfigurationManager config1 = new ConfigurationManager(configPath);
        config1.setUsername("SavedPlayer");
        config1.setLastUsedVersion("1.21");
        config1.save();

        // Load
        ConfigurationManager config2 = new ConfigurationManager(configPath);
        assertEquals("SavedPlayer", config2.getUsername());
        assertEquals("1.21", config2.getLastUsedVersion());
    }

    @Test
    void lastVersionIsNullByDefault(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("launcher_options.txt");
        ConfigurationManager config = new ConfigurationManager(configPath);

        assertNull(config.getLastUsedVersion());
    }

    @Test
    void setAndGetLastUsedVersion(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("launcher_options.txt");
        ConfigurationManager config = new ConfigurationManager(configPath);

        config.setLastUsedVersion("1.20.4");
        assertEquals("1.20.4", config.getLastUsedVersion());
    }

    @Test
    void handlesMissingFileGracefully(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("nonexistent/config.txt");
        // Should not throw - just use defaults
        ConfigurationManager config = new ConfigurationManager(configPath);
        assertEquals("OpenCitizen", config.getUsername());
    }

    @Test
    void loadsExistingConfigOnConstruction(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("launcher_options.txt");

        // Write a config file manually
        try (BufferedWriter writer = Files.newBufferedWriter(configPath)) {
            writer.write("username=ManualPlayer\n");
            writer.write("lastVersion=1.19\n");
        }

        ConfigurationManager config = new ConfigurationManager(configPath);
        assertEquals("ManualPlayer", config.getUsername());
        assertEquals("1.19", config.getLastUsedVersion());
    }

    @Test
    void saveCreatesFileIfNotExists(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("new_config.txt");
        assertFalse(Files.exists(configPath));

        ConfigurationManager config = new ConfigurationManager(configPath);
        config.setUsername("NewPlayer");
        config.save();

        assertTrue(Files.exists(configPath));
    }

    @Test
    void overwritesExistingValues(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("launcher_options.txt");

        ConfigurationManager config = new ConfigurationManager(configPath);
        config.setUsername("First");
        config.save();

        config.setUsername("Second");
        config.save();

        ConfigurationManager config2 = new ConfigurationManager(configPath);
        assertEquals("Second", config2.getUsername());
    }
}
