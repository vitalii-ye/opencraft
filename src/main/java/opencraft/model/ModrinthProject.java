package opencraft.model;

import java.util.List;

/**
 * Represents a mod/shader project from Modrinth.
 */
public class ModrinthProject {
    private final String id;
    private final String slug;
    private final String title;
    private final String description;
    private final String projectType; // "mod" or "shader"
    private final String iconUrl;
    private final int downloads;
    private final String author;
    private final List<String> categories;
    private final List<String> gameVersions;
    private final List<String> loaders;

    public ModrinthProject(String id, String slug, String title, String description,
                          String projectType, String iconUrl, int downloads, String author,
                          List<String> categories, List<String> gameVersions, List<String> loaders) {
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.projectType = projectType;
        this.iconUrl = iconUrl;
        this.downloads = downloads;
        this.author = author;
        this.categories = categories;
        this.gameVersions = gameVersions;
        this.loaders = loaders;
    }

    public String getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getProjectType() { return projectType; }
    public String getIconUrl() { return iconUrl; }
    public int getDownloads() { return downloads; }
    public String getAuthor() { return author; }
    public List<String> getCategories() { return categories; }
    public List<String> getGameVersions() { return gameVersions; }
    public List<String> getLoaders() { return loaders; }

    public boolean isMod() { return "mod".equals(projectType); }
    public boolean isShader() { return "shader".equals(projectType); }
}
