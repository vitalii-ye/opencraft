package opencraft.model;

/**
 * Represents a game version that supports Fabric.
 */
public class FabricGameVersion {
    private final String version;
    private final boolean stable;

    public FabricGameVersion(String version, boolean stable) {
        this.version = version;
        this.stable = stable;
    }

    public String getVersion() {
        return version;
    }

    public boolean isStable() {
        return stable;
    }
}
