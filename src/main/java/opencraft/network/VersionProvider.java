package opencraft.network;

import opencraft.model.MinecraftVersion;
import opencraft.model.VersionResponse;

import java.io.IOException;
import java.util.List;

/**
 * Abstraction for fetching Minecraft version information from a remote source.
 * Allows callers to depend on this interface rather than the concrete HTTP implementation.
 */
public interface VersionProvider {
    List<MinecraftVersion> fetchAvailableVersions() throws IOException, InterruptedException;
    VersionResponse fetchAvailableVersionsWithETag(String ifNoneMatch) throws IOException, InterruptedException;
}
