package opencraft.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ConfigurationManager {
    private static final String CONFIG_FILE = "launcher_options.txt";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_LAST_VERSION = "lastVersion";
    
    private final Properties properties;
    private final Path configPath;

    public ConfigurationManager() {
        this.properties = new Properties();
        this.configPath = MinecraftPathResolver.getMinecraftDirectory().resolve(CONFIG_FILE);
        load();
    }

    private void load() {
        if (Files.exists(configPath)) {
            try (var reader = Files.newBufferedReader(configPath)) {
                properties.load(reader);
            } catch (IOException e) {
                System.err.println("Failed to load configuration: " + e.getMessage());
            }
        }
    }

    public void save() {
        try (var writer = Files.newBufferedWriter(configPath)) {
            properties.store(writer, "OpenCraft Launcher Options");
        } catch (IOException e) {
            System.err.println("Failed to save configuration: " + e.getMessage());
        }
    }

    public String getUsername() {
        return properties.getProperty(KEY_USERNAME, "OpenCitizen");
    }

    public void setUsername(String username) {
        properties.setProperty(KEY_USERNAME, username);
    }

    public String getLastUsedVersion() {
        return properties.getProperty(KEY_LAST_VERSION);
    }

    public void setLastUsedVersion(String version) {
        properties.setProperty(KEY_LAST_VERSION, version);
    }
}
