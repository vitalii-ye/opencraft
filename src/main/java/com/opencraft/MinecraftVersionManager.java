package com.opencraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class MinecraftVersionManager {
    private static final String VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Represents a Minecraft version
     */
    public static class MinecraftVersion {
        private final String id;
        private final String type;
        private final String url;
        private final String releaseTime;

        public MinecraftVersion(String id, String type, String url, String releaseTime) {
            this.id = id;
            this.type = type;
            this.url = url;
            this.releaseTime = releaseTime;
        }

        public String getId() {
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

        @Override
        public String toString() {
            return id + " (" + type + ")";
        }

        public boolean isRelease() {
            return "release".equals(type);
        }

        public boolean isSnapshot() {
            return "snapshot".equals(type);
        }
    }

    /**
     * Fetches all available Minecraft versions from the official manifest
     */
    public static List<MinecraftVersion> fetchAvailableVersions() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(VERSION_MANIFEST_URL))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());

        List<MinecraftVersion> versions = new ArrayList<>();
        JsonNode versionsArray = root.path("versions");

        for (JsonNode versionNode : versionsArray) {
            String id = versionNode.get("id").asText();
            String type = versionNode.get("type").asText();
            String url = versionNode.get("url").asText();
            String releaseTime = versionNode.get("releaseTime").asText();

            versions.add(new MinecraftVersion(id, type, url, releaseTime));
        }

        return versions;
    }

    /**
     * Fetches only release versions (no snapshots or betas)
     */
    public static List<MinecraftVersion> fetchReleaseVersions() throws IOException, InterruptedException {
        List<MinecraftVersion> allVersions = fetchAvailableVersions();
        List<MinecraftVersion> releaseVersions = new ArrayList<>();

        for (MinecraftVersion version : allVersions) {
            if (version.isRelease()) {
                releaseVersions.add(version);
            }
        }

        return releaseVersions;
    }

    /**
     * Gets the latest release version
     */
    public static MinecraftVersion getLatestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(VERSION_MANIFEST_URL))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());

        JsonNode latest = root.path("latest");
        String latestReleaseId = latest.get("release").asText();

        // Find the version details for the latest release
        JsonNode versionsArray = root.path("versions");
        for (JsonNode versionNode : versionsArray) {
            String id = versionNode.get("id").asText();
            if (latestReleaseId.equals(id)) {
                String type = versionNode.get("type").asText();
                String url = versionNode.get("url").asText();
                String releaseTime = versionNode.get("releaseTime").asText();
                return new MinecraftVersion(id, type, url, releaseTime);
            }
        }

        throw new RuntimeException("Could not find latest release version: " + latestReleaseId);
    }
}
