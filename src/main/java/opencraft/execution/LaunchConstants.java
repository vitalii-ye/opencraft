package opencraft.execution;

/**
 * Shared constants for Minecraft/Fabric launch parameters.
 * Centralizes values that were previously duplicated and inconsistent
 * across OpenCraftLauncher and FabricLauncher.
 */
public final class LaunchConstants {

    private LaunchConstants() {}

    public static final String MAX_MEMORY = "-Xmx4G";
    public static final String MIN_MEMORY = "-Xms1G";
    public static final String OFFLINE_UUID = "00000000-0000-0000-0000-000000000000";
    public static final String OFFLINE_ACCESS_TOKEN = "0";
    public static final String USER_TYPE = "legacy";
    public static final String FILE_ENCODING = "-Dfile.encoding=UTF-8";

    public static final String USER_AGENT = "OpenCraft-Launcher/1.0 (github.com/opencraft)";
}
