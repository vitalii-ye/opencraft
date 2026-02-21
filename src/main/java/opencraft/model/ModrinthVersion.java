package opencraft.model;

import java.util.List;
import opencraft.model.ModrinthFile;

/**
 * Represents a specific version of a mod/shader.
 */
public class ModrinthVersion {
    private final String id;
    private final String projectId;
    private final String name;
    private final String versionNumber;
    private final String versionType; // "release", "beta", "alpha"
    private final List<String> gameVersions;
    private final List<String> loaders;
    private final List<ModrinthFile> files;

    public ModrinthVersion(String id, String projectId, String name, String versionNumber,
                          String versionType, List<String> gameVersions, List<String> loaders,
                          List<ModrinthFile> files) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.versionNumber = versionNumber;
        this.versionType = versionType;
        this.gameVersions = gameVersions;
        this.loaders = loaders;
        this.files = files;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getVersionNumber() { return versionNumber; }
    public String getVersionType() { return versionType; }
    public List<String> getGameVersions() { return gameVersions; }
    public List<String> getLoaders() { return loaders; }
    public List<ModrinthFile> getFiles() { return files; }

    /**
     * Gets the primary download file.
     */
    public ModrinthFile getPrimaryFile() {
        for (ModrinthFile file : files) {
            if (file.isPrimary()) {
                return file;
            }
        }
        return files.isEmpty() ? null : files.get(0);
    }
}
