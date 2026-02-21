package opencraft.network;

import opencraft.network.ModrinthApiClient.ModrinthFile;
import opencraft.network.ModrinthApiClient.ModrinthProject;
import opencraft.network.ModrinthApiClient.ModrinthVersion;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthApiClientTest {

    // --- ModrinthProject tests ---

    @Test
    void projectGettersReturnCorrectValues() {
        ModrinthProject project = new ModrinthProject(
                "AABBCCDD", "sodium", "Sodium", "A modern rendering engine",
                "mod", "https://example.com/icon.png", 50000000, "jellysquid3",
                Arrays.asList("optimization", "fabric"), Arrays.asList("1.21", "1.20.6"),
                Arrays.asList("fabric", "quilt"));

        assertEquals("AABBCCDD", project.getId());
        assertEquals("sodium", project.getSlug());
        assertEquals("Sodium", project.getTitle());
        assertEquals("A modern rendering engine", project.getDescription());
        assertEquals("mod", project.getProjectType());
        assertEquals("https://example.com/icon.png", project.getIconUrl());
        assertEquals(50000000, project.getDownloads());
        assertEquals("jellysquid3", project.getAuthor());
        assertEquals(2, project.getCategories().size());
        assertEquals(2, project.getGameVersions().size());
        assertEquals(2, project.getLoaders().size());
    }

    @Test
    void projectIsModReturnsTrueForMod() {
        ModrinthProject project = new ModrinthProject(
                "id", "slug", "Title", "Desc", "mod", null, 0, "author",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        assertTrue(project.isMod());
        assertFalse(project.isShader());
    }

    @Test
    void projectIsShaderReturnsTrueForShader() {
        ModrinthProject project = new ModrinthProject(
                "id", "slug", "Title", "Desc", "shader", null, 0, "author",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        assertTrue(project.isShader());
        assertFalse(project.isMod());
    }

    // --- ModrinthVersion tests ---

    @Test
    void versionGettersReturnCorrectValues() {
        ModrinthFile file1 = new ModrinthFile("https://cdn.modrinth.com/file1.jar",
                "sodium-0.5.8.jar", true, 1024000, "abc123sha512");
        ModrinthFile file2 = new ModrinthFile("https://cdn.modrinth.com/file2.jar",
                "sodium-0.5.8-sources.jar", false, 512000, "def456sha512");

        ModrinthVersion version = new ModrinthVersion(
                "ver-id", "proj-id", "Sodium 0.5.8", "0.5.8", "release",
                Arrays.asList("1.21", "1.20.6"), Arrays.asList("fabric"),
                Arrays.asList(file1, file2));

        assertEquals("ver-id", version.getId());
        assertEquals("proj-id", version.getProjectId());
        assertEquals("Sodium 0.5.8", version.getName());
        assertEquals("0.5.8", version.getVersionNumber());
        assertEquals("release", version.getVersionType());
        assertEquals(2, version.getGameVersions().size());
        assertEquals(1, version.getLoaders().size());
        assertEquals(2, version.getFiles().size());
    }

    @Test
    void getPrimaryFileReturnsPrimaryWhenPresent() {
        ModrinthFile primary = new ModrinthFile("url1", "primary.jar", true, 1024, "sha");
        ModrinthFile secondary = new ModrinthFile("url2", "secondary.jar", false, 512, "sha");

        ModrinthVersion version = new ModrinthVersion(
                "id", "pid", "name", "1.0", "release",
                Collections.emptyList(), Collections.emptyList(),
                Arrays.asList(secondary, primary));

        ModrinthFile result = version.getPrimaryFile();
        assertNotNull(result);
        assertEquals("primary.jar", result.getFilename());
        assertTrue(result.isPrimary());
    }

    @Test
    void getPrimaryFileFallsBackToFirstWhenNoPrimary() {
        ModrinthFile file1 = new ModrinthFile("url1", "first.jar", false, 1024, "sha");
        ModrinthFile file2 = new ModrinthFile("url2", "second.jar", false, 512, "sha");

        ModrinthVersion version = new ModrinthVersion(
                "id", "pid", "name", "1.0", "release",
                Collections.emptyList(), Collections.emptyList(),
                Arrays.asList(file1, file2));

        ModrinthFile result = version.getPrimaryFile();
        assertNotNull(result);
        assertEquals("first.jar", result.getFilename());
    }

    @Test
    void getPrimaryFileReturnsNullWhenNoFiles() {
        ModrinthVersion version = new ModrinthVersion(
                "id", "pid", "name", "1.0", "release",
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList());

        assertNull(version.getPrimaryFile());
    }

    // --- ModrinthFile tests ---

    @Test
    void fileGettersReturnCorrectValues() {
        ModrinthFile file = new ModrinthFile(
                "https://cdn.modrinth.com/data/AANobbMI/versions/abc/sodium-0.5.8.jar",
                "sodium-0.5.8.jar", true, 2048000,
                "a1b2c3d4e5f6");

        assertEquals("https://cdn.modrinth.com/data/AANobbMI/versions/abc/sodium-0.5.8.jar", file.getUrl());
        assertEquals("sodium-0.5.8.jar", file.getFilename());
        assertTrue(file.isPrimary());
        assertEquals(2048000, file.getSize());
        assertEquals("a1b2c3d4e5f6", file.getSha512());
    }

    @Test
    void fileNotPrimary() {
        ModrinthFile file = new ModrinthFile("url", "file.jar", false, 100, "hash");
        assertFalse(file.isPrimary());
    }
}
