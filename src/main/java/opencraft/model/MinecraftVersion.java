package opencraft.model;

/**
 * Represents a Minecraft version (vanilla or Fabric)
 */
public class MinecraftVersion {
  private final String id;
  private final String type;
  private final String url;
  private final String releaseTime;
  private final boolean fabric;
  private final String fabricLoaderVersion;
  private final String baseGameVersion;

  public MinecraftVersion(String id, String type, String url, String releaseTime) {
    this.id = id;
    this.type = type;
    this.url = url;
    this.releaseTime = releaseTime;
    this.fabric = false;
    this.fabricLoaderVersion = null;
    this.baseGameVersion = id;
  }

  /**
   * Creates a Fabric version based on a vanilla version.
   */
  public MinecraftVersion(String id, String type, String url, String releaseTime,
                         String fabricLoaderVersion) {
    this.baseGameVersion = id;
    this.id = "fabric-loader-" + fabricLoaderVersion + "-" + id;
    this.type = type;
    this.url = url;
    this.releaseTime = releaseTime;
    this.fabric = true;
    this.fabricLoaderVersion = fabricLoaderVersion;
  }

  public String getId() {
    return id;
  }

  /**
   * Returns the display name for the UI (e.g., "1.21 [Fabric]").
   */
  public String getDisplayName() {
    if (fabric) {
      return baseGameVersion + " [Fabric]";
    }
    return id;
  }

  public String getType() {
    return type;
  }

  public String getUrl() {
    return url;
  }

  public String getReleaseTime() {
    return releaseTime;
  }

  public boolean isFabric() {
    return fabric;
  }

  public String getFabricLoaderVersion() {
    return fabricLoaderVersion;
  }

  /**
   * Returns the base game version (e.g., "1.21" for both vanilla and Fabric).
   */
  public String getBaseGameVersion() {
    return baseGameVersion;
  }

  @Override
  public String toString() {
    return getDisplayName();
  }

  public boolean isRelease() {
    return "release".equals(type);
  }

  public boolean isSnapshot() {
    return "snapshot".equals(type);
  }

  /**
   * Creates a Fabric version from an existing vanilla version.
   */
  public MinecraftVersion toFabricVersion(String loaderVersion) {
    return new MinecraftVersion(this.baseGameVersion, this.type, this.url,
                                this.releaseTime, loaderVersion);
  }
}
