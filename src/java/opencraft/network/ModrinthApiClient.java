package opencraft.network;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client for the Modrinth API to search and download mods/shaders.
 * API Documentation: https://docs.modrinth.com/api/
 */
public class ModrinthApiClient {

    private static final String API_BASE_URL = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "OpenCraft-Launcher/1.0 (github.com/opencraft)";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Represents a mod/shader project from Modrinth.
     */
    public static class ModrinthProject {
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

    /**
     * Represents a specific version of a mod/shader.
     */
    public static class ModrinthVersion {
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

    /**
     * Represents a downloadable file for a mod version.
     */
    public static class ModrinthFile {
        private final String url;
        private final String filename;
        private final boolean primary;
        private final long size;
        private final String sha512;

        public ModrinthFile(String url, String filename, boolean primary, long size, String sha512) {
            this.url = url;
            this.filename = filename;
            this.primary = primary;
            this.size = size;
            this.sha512 = sha512;
        }

        public String getUrl() { return url; }
        public String getFilename() { return filename; }
        public boolean isPrimary() { return primary; }
        public long getSize() { return size; }
        public String getSha512() { return sha512; }
    }

    /**
     * Searches for mods on Modrinth.
     * 
     * @param query       Search query string
     * @param gameVersion The Minecraft version (e.g., "1.21")
     * @param loader      The mod loader (e.g., "fabric")
     * @param limit       Maximum number of results
     * @return List of matching projects
     */
    public static List<ModrinthProject> searchMods(String query, String gameVersion, String loader, int limit)
            throws IOException, InterruptedException {
        return search(query, "mod", gameVersion, loader, limit);
    }

    /**
     * Searches for shaders on Modrinth.
     */
    public static List<ModrinthProject> searchShaders(String query, String gameVersion, int limit)
            throws IOException, InterruptedException {
        return search(query, "shader", gameVersion, null, limit);
    }

    /**
     * Generic search method for Modrinth.
     */
    public static List<ModrinthProject> search(String query, String projectType, 
                                                String gameVersion, String loader, int limit)
            throws IOException, InterruptedException {
        
        StringBuilder urlBuilder = new StringBuilder(API_BASE_URL + "/search?");
        
        // Add query parameter
        if (query != null && !query.isEmpty()) {
            urlBuilder.append("query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8)).append("&");
        }
        
        // Build facets array for filtering
        List<String> facets = new ArrayList<>();
        
        // Project type filter
        if (projectType != null) {
            facets.add("[\"project_type:" + projectType + "\"]");
        }
        
        // Game version filter
        if (gameVersion != null) {
            facets.add("[\"versions:" + gameVersion + "\"]");
        }
        
        // Loader filter (only for mods)
        if (loader != null) {
            facets.add("[\"categories:" + loader + "\"]");
        }
        
        if (!facets.isEmpty()) {
            String facetsJson = "[" + String.join(",", facets) + "]";
            urlBuilder.append("facets=").append(URLEncoder.encode(facetsJson, StandardCharsets.UTF_8)).append("&");
        }
        
        urlBuilder.append("limit=").append(limit);

        String url = urlBuilder.toString();
        System.out.println("Modrinth search URL: " + url);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Modrinth API error: HTTP " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode hits = root.path("hits");

        List<ModrinthProject> projects = new ArrayList<>();
        for (JsonNode hit : hits) {
            projects.add(parseProject(hit));
        }

        return projects;
    }

    /**
     * Gets detailed information about a project.
     */
    public static ModrinthProject getProject(String projectIdOrSlug) throws IOException, InterruptedException {
        String url = API_BASE_URL + "/project/" + URLEncoder.encode(projectIdOrSlug, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Project not found: " + projectIdOrSlug);
        }

        return parseProjectDetails(mapper.readTree(response.body()));
    }

    /**
     * Gets available versions of a project for a specific game version and loader.
     */
    public static List<ModrinthVersion> getProjectVersions(String projectId, String gameVersion, String loader)
            throws IOException, InterruptedException {
        
        StringBuilder urlBuilder = new StringBuilder(API_BASE_URL + "/project/")
                .append(URLEncoder.encode(projectId, StandardCharsets.UTF_8))
                .append("/version?");
        
        if (gameVersion != null) {
            String gameVersionsParam = "[\"" + gameVersion + "\"]";
            urlBuilder.append("game_versions=").append(URLEncoder.encode(gameVersionsParam, StandardCharsets.UTF_8)).append("&");
        }
        
        if (loader != null) {
            String loadersParam = "[\"" + loader + "\"]";
            urlBuilder.append("loaders=").append(URLEncoder.encode(loadersParam, StandardCharsets.UTF_8));
        }

        String url = urlBuilder.toString();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to get versions for project: " + projectId);
        }

        JsonNode root = mapper.readTree(response.body());
        List<ModrinthVersion> versions = new ArrayList<>();
        
        for (JsonNode versionNode : root) {
            versions.add(parseVersion(versionNode));
        }

        return versions;
    }

    /**
     * Downloads a mod/shader file to the specified destination.
     */
    public static void downloadFile(ModrinthFile file, Path destination, Consumer<String> logger)
            throws IOException, InterruptedException {
        
        if (logger != null) {
            logger.accept("Downloading " + file.getFilename() + "...");
        }

        Files.createDirectories(destination.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(file.getUrl()))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download file: HTTP " + response.statusCode());
        }

        try (InputStream in = response.body()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }

        if (logger != null) {
            logger.accept("Downloaded: " + file.getFilename());
        }
    }

    /**
     * Gets the Iris shader mod project (for auto-installation).
     */
    public static ModrinthProject getIrisProject() throws IOException, InterruptedException {
        return getProject("iris");
    }

    /**
     * Gets the Fabric API mod project (commonly required dependency).
     */
    public static ModrinthProject getFabricApiProject() throws IOException, InterruptedException {
        return getProject("fabric-api");
    }

    // Helper methods for parsing JSON responses

    private static ModrinthProject parseProject(JsonNode node) {
        List<String> categories = new ArrayList<>();
        for (JsonNode cat : node.path("categories")) {
            categories.add(cat.asText());
        }

        List<String> gameVersions = new ArrayList<>();
        for (JsonNode ver : node.path("versions")) {
            gameVersions.add(ver.asText());
        }

        // Display categories as loaders for search results
        List<String> loaders = new ArrayList<>();
        for (JsonNode cat : node.path("display_categories")) {
            loaders.add(cat.asText());
        }

        return new ModrinthProject(
                node.path("project_id").asText(),
                node.path("slug").asText(),
                node.path("title").asText(),
                node.path("description").asText(),
                node.path("project_type").asText(),
                node.path("icon_url").asText(null),
                node.path("downloads").asInt(0),
                node.path("author").asText("Unknown"),
                categories,
                gameVersions,
                loaders
        );
    }

    private static ModrinthProject parseProjectDetails(JsonNode node) {
        List<String> categories = new ArrayList<>();
        for (JsonNode cat : node.path("categories")) {
            categories.add(cat.asText());
        }

        List<String> gameVersions = new ArrayList<>();
        for (JsonNode ver : node.path("game_versions")) {
            gameVersions.add(ver.asText());
        }

        List<String> loaders = new ArrayList<>();
        for (JsonNode loader : node.path("loaders")) {
            loaders.add(loader.asText());
        }

        return new ModrinthProject(
                node.path("id").asText(),
                node.path("slug").asText(),
                node.path("title").asText(),
                node.path("description").asText(),
                node.path("project_type").asText(),
                node.path("icon_url").asText(null),
                node.path("downloads").asInt(0),
                node.path("team").asText("Unknown"),
                categories,
                gameVersions,
                loaders
        );
    }

    private static ModrinthVersion parseVersion(JsonNode node) {
        List<String> gameVersions = new ArrayList<>();
        for (JsonNode ver : node.path("game_versions")) {
            gameVersions.add(ver.asText());
        }

        List<String> loaders = new ArrayList<>();
        for (JsonNode loader : node.path("loaders")) {
            loaders.add(loader.asText());
        }

        List<ModrinthFile> files = new ArrayList<>();
        for (JsonNode fileNode : node.path("files")) {
            files.add(new ModrinthFile(
                    fileNode.path("url").asText(),
                    fileNode.path("filename").asText(),
                    fileNode.path("primary").asBoolean(false),
                    fileNode.path("size").asLong(0),
                    fileNode.path("hashes").path("sha512").asText("")
            ));
        }

        return new ModrinthVersion(
                node.path("id").asText(),
                node.path("project_id").asText(),
                node.path("name").asText(),
                node.path("version_number").asText(),
                node.path("version_type").asText(),
                gameVersions,
                loaders,
                files
        );
    }
}
