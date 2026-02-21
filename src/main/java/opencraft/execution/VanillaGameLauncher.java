package opencraft.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles the non-UI business logic of launching vanilla Minecraft:
 * file validation, JSON parsing, command assembly, and process start.
 */
public class VanillaGameLauncher {

    private final ProcessManager processManager;

    public VanillaGameLauncher(ProcessManager processManager) {
        this.processManager = processManager;
    }

    /**
     * Validates that all required game files exist for the given version.
     *
     * @throws IOException with a descriptive message if any file is missing
     */
    public void validateFiles(String versionId, Path minecraftDir) throws IOException {
        File librariesFile = minecraftDir.resolve("libraries_" + versionId + ".txt").toFile();
        if (!librariesFile.exists()) {
            throw new IOException(
                "Libraries file not found for version " + versionId + "!\nPlease download this version first.");
        }

        File minecraftJar = minecraftDir.resolve("versions/" + versionId + "/" + versionId + ".jar").toFile();
        if (!minecraftJar.exists()) {
            throw new IOException(
                "Minecraft JAR not found for version " + versionId + "!\nPlease download this version first.");
        }

        File versionJson = minecraftDir.resolve("versions/" + versionId + "/" + versionId + ".json").toFile();
        if (!versionJson.exists()) {
            throw new IOException(
                "Version manifest not found for version " + versionId + "!\nPlease download this version first.");
        }
    }

    /**
     * Builds the launch command, starts the game process, and delivers log lines
     * to the provided {@code logger}.
     *
     * @param username     in-game player name
     * @param versionId    Minecraft version string (e.g. "1.21.1")
     * @param minecraftDir path to the .minecraft directory
     * @param logger       receives stdout/stderr lines from the game process
     * @throws IOException if a required file is missing or the process cannot start
     */
    public void launch(String username, String versionId, Path minecraftDir,
                       Consumer<String> logger) throws IOException {
        validateFiles(versionId, minecraftDir);

        // Read classpath entries
        String librariesPath = Files.readString(
            minecraftDir.resolve("libraries_" + versionId + ".txt")).trim();

        // Read version manifest to get asset index
        ObjectMapper mapper = new ObjectMapper();
        File versionJsonFile = minecraftDir.resolve(
            "versions/" + versionId + "/" + versionId + ".json").toFile();
        JsonNode versionManifest = mapper.readTree(versionJsonFile);
        String assetIndex = versionManifest.path("assetIndex").path("id").asText();

        String osName = System.getProperty("os.name").toLowerCase();

        String minecraftDirStr = minecraftDir.toString();
        String versionJarPath = minecraftDir.resolve(
            "versions/" + versionId + "/" + versionId + ".jar").toString();
        String nativesPath = minecraftDir.resolve("libraries/natives").toString();
        String assetsPath = minecraftDir.resolve("assets").toString();

        LauncherCommandBuilder builder = new LauncherCommandBuilder(
            "net.minecraft.client.main.Main", Paths.get(nativesPath));

        if (osName.contains("mac") || osName.contains("darwin")) {
            builder.addJvmArg("-XstartOnFirstThread");
        }

        builder.addJvmArg(LaunchConstants.MAX_MEMORY);
        builder.addJvmArg(LaunchConstants.MIN_MEMORY);
        builder.addJvmArg(LaunchConstants.FILE_ENCODING);
        builder.addClasspathEntry(librariesPath);
        builder.addClasspathEntry(versionJarPath);
        builder.addGameArg("--version");
        builder.addGameArg(versionId);
        builder.addGameArg("--accessToken");
        builder.addGameArg(LaunchConstants.OFFLINE_ACCESS_TOKEN);
        builder.addGameArg("--uuid");
        builder.addGameArg(LaunchConstants.OFFLINE_UUID);
        builder.addGameArg("--username");
        builder.addGameArg(username);
        builder.addGameArg("--userType");
        builder.addGameArg(LaunchConstants.USER_TYPE);
        builder.addGameArg("--versionType");
        builder.addGameArg("release");
        builder.addGameArg("--gameDir");
        builder.addGameArg(minecraftDirStr);
        builder.addGameArg("--assetsDir");
        builder.addGameArg(assetsPath);
        builder.addGameArg("--assetIndex");
        builder.addGameArg(assetIndex);
        builder.addGameArg("--clientId");
        builder.addGameArg(LaunchConstants.OFFLINE_ACCESS_TOKEN);

        List<String> command = builder.build();
        processManager.startProcess(command, logger, minecraftDir.toFile());
    }
}
