package opencraft.model;

/**
 * Represents a complete Fabric version (game version + loader version).
 */
public class FabricVersion {
    private static final String FABRIC_META_URL = "https://meta.fabricmc.net";

    private final String gameVersion;
    private final String loaderVersion;
    private final String displayName;
    private final String versionId;

    public FabricVersion(String gameVersion, String loaderVersion) {
        this.gameVersion = gameVersion;
        this.loaderVersion = loaderVersion;
        this.displayName = gameVersion + " [Fabric]";
        this.versionId = "fabric-loader-" + loaderVersion + "-" + gameVersion;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public String getLoaderVersion() {
        return loaderVersion;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getVersionId() {
        return versionId;
    }

    /**
     * Returns the URL to fetch the complete profile JSON for this Fabric version.
     */
    public String getProfileUrl() {
        return FABRIC_META_URL + "/v2/versions/loader/" + gameVersion + "/" + loaderVersion + "/profile/json";
    }
}
