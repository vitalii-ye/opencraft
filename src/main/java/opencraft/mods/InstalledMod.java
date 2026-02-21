package opencraft.mods;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Represents an installed mod or shader in the Minecraft mods directory.
 */
public class InstalledMod {
    
    private final String fileName;
    private final String displayName;
    private final String version;
    private final Path filePath;
    private final long fileSize;
    private final Instant installedAt;
    private final String gameVersion;
    private final ModType type;

    /**
     * Type of installed content.
     */
    public enum ModType {
        MOD,
        SHADER
    }

    public InstalledMod(String fileName, String displayName, String version, 
                        Path filePath, long fileSize, Instant installedAt, 
                        String gameVersion, ModType type) {
        this.fileName = fileName;
        this.displayName = displayName;
        this.version = version;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.installedAt = installedAt;
        this.gameVersion = gameVersion;
        this.type = type;
    }

    /**
     * Creates an InstalledMod from a file path, extracting information from the filename.
     */
    public static InstalledMod fromFile(Path file, String gameVersion, ModType type) {
        String fileName = file.getFileName().toString();
        String displayName = extractDisplayName(fileName);
        String version = extractVersion(fileName);
        
        long fileSize = 0;
        Instant installedAt = Instant.now();
        
        try {
            fileSize = java.nio.file.Files.size(file);
            installedAt = java.nio.file.Files.getLastModifiedTime(file).toInstant();
        } catch (Exception e) {
            System.err.println("Warning: could not read file metadata for " + file + ": " + e.getMessage());
        }
        
        return new InstalledMod(fileName, displayName, version, file, fileSize, 
                               installedAt, gameVersion, type);
    }

    /**
     * Extracts a display name from a mod filename.
     * Example: "sodium-fabric-0.5.8+mc1.21.jar" -> "Sodium"
     */
    private static String extractDisplayName(String fileName) {
        // Remove .jar extension
        String name = fileName;
        if (name.toLowerCase().endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.toLowerCase().endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        
        // Try to find the mod name part (before version number or loader indicator)
        // Common patterns: name-fabric-version, name-version, name+mc1.21
        
        // Split on common separators
        String[] parts = name.split("[-+_]");
        if (parts.length > 0) {
            // Take first part, capitalize first letter
            String first = parts[0].trim();
            if (!first.isEmpty()) {
                return first.substring(0, 1).toUpperCase() + first.substring(1);
            }
        }
        
        return name;
    }

    /**
     * Extracts version string from a mod filename.
     * Example: "sodium-fabric-0.5.8+mc1.21.jar" -> "0.5.8"
     */
    private static String extractVersion(String fileName) {
        // Look for version pattern: digits.digits.digits
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(fileName);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return "Unknown";
    }

    public String getFileName() {
        return fileName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getVersion() {
        return version;
    }

    public Path getFilePath() {
        return filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    /**
     * Returns human-readable file size.
     */
    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    public Instant getInstalledAt() {
        return installedAt;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public ModType getType() {
        return type;
    }

    public boolean isMod() {
        return type == ModType.MOD;
    }

    public boolean isShader() {
        return type == ModType.SHADER;
    }

    @Override
    public String toString() {
        return displayName + " v" + version;
    }
}
