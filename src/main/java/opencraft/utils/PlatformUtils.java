package opencraft.utils;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Utility class for OS detection and native library classifier resolution.
 */
public final class PlatformUtils {

    private PlatformUtils() {
        // utility class — not instantiable
    }

    /**
     * Determines whether a Minecraft library should be included on the current platform,
     * based on the "rules" array in the library JSON.
     *
     * @param lib The library JSON node
     * @return {@code true} if the library is allowed for the current OS
     */
    public static boolean isLibraryAllowed(JsonNode lib) {
        JsonNode rules = lib.path("rules");
        if (rules.isMissingNode()) {
            return true;
        }

        for (JsonNode rule : rules) {
            String action = rule.get("action").asText();
            JsonNode os = rule.path("os");

            if (os.isMissingNode()) {
                return "allow".equals(action);
            } else {
                String osName = os.path("name").asText();
                if (matchesCurrentOS(osName)) {
                    return "allow".equals(action);
                }
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if the given Minecraft OS name matches the currently running OS.
     *
     * @param osName A Minecraft-style OS name: {@code "windows"}, {@code "osx"}, or {@code "linux"}
     */
    public static boolean matchesCurrentOS(String osName) {
        String currentOS = System.getProperty("os.name").toLowerCase();
        if (currentOS.contains("win") && "windows".equals(osName)) {
            return true;
        }
        if (currentOS.contains("mac") && "osx".equals(osName)) {
            return true;
        }
        if (currentOS.contains("nix") || currentOS.contains("nux")) {
            return "linux".equals(osName);
        }
        return false;
    }

    /**
     * Returns the native library classifier string for the current platform and architecture,
     * e.g. {@code "natives-macos-arm64"} or {@code "natives-windows-x86_64"}.
     *
     * @return The classifier string, or {@code null} if the platform is unrecognised
     */
    public static String getNativeClassifier() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        if (os.contains("win")) {
            return arch.contains("64") ? "natives-windows-x86_64" : "natives-windows-x86";
        } else if (os.contains("mac")) {
            return arch.contains("aarch64") || arch.contains("arm") ? "natives-macos-arm64" : "natives-macos-x86_64";
        } else if (os.contains("nix") || os.contains("nux")) {
            return arch.contains("64") ? "natives-linux-x86_64" : "natives-linux-x86";
        }
        return null;
    }

    /**
     * Returns {@code true} when running on macOS or OS X.
     */
    public static boolean isMac() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("mac") || os.contains("darwin");
    }

    /**
     * Returns {@code true} when running on Windows.
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Returns {@code true} when running on Linux.
     */
    public static boolean isLinux() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("nix") || os.contains("nux");
    }
}
