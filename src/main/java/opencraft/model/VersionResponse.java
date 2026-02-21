package opencraft.model;

import java.util.List;

/**
 * Response containing versions and ETag header
 */
public class VersionResponse {
  private final List<MinecraftVersion> versions;
  private final String etag;
  private final int statusCode;

  public VersionResponse(List<MinecraftVersion> versions, String etag, int statusCode) {
    this.versions = versions;
    this.etag = etag;
    this.statusCode = statusCode;
  }

  public List<MinecraftVersion> getVersions() {
    return versions;
  }

  public String getEtag() {
    return etag;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public boolean isNotModified() {
    return statusCode == 304;
  }
}
