package opencraft.model;

/**
 * Represents a Fabric loader version.
 */
public class FabricLoaderVersion {
    private final String version;
    private final boolean stable;

    public FabricLoaderVersion(String version, boolean stable) {
        this.version = version;
        this.stable = stable;
    }

    public String getVersion() {
        return version;
    }

    public boolean isStable() {
        return stable;
    }

    @Override
    public String toString() {
        return version + (stable ? "" : " (unstable)");
    }
}
