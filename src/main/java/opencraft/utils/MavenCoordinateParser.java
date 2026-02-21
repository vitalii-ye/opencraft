package opencraft.utils;

import java.nio.file.Path;

/**
 * Parses Maven coordinates of the form {@code group:artifact:version} and
 * resolves them to filesystem paths inside a Maven-style libraries directory.
 *
 * <p>Example: {@code net.fabricmc:fabric-loader:0.15.11} resolves to
 * {@code libraries/net/fabricmc/fabric-loader/0.15.11/fabric-loader-0.15.11.jar}.
 */
public final class MavenCoordinateParser {

    private final String group;
    private final String artifact;
    private final String version;

    private MavenCoordinateParser(String group, String artifact, String version) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
    }

    /**
     * Parses a Maven coordinate string ({@code group:artifact:version}).
     *
     * @param coordinates the Maven coordinate string to parse
     * @return the parsed {@code MavenCoordinateParser} instance
     * @throws IllegalArgumentException if the coordinate string has fewer than 3 parts
     */
    public static MavenCoordinateParser parse(String coordinates) {
        String[] parts = coordinates.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid Maven coordinates: " + coordinates);
        }
        String group = parts[0].replace('.', '/');
        return new MavenCoordinateParser(group, parts[1], parts[2]);
    }

    /** Returns the group path (dots replaced with slashes), e.g. {@code "net/fabricmc"}. */
    public String getGroup() {
        return group;
    }

    /** Returns the artifact ID, e.g. {@code "fabric-loader"}. */
    public String getArtifact() {
        return artifact;
    }

    /** Returns the version string, e.g. {@code "0.15.11"}. */
    public String getVersion() {
        return version;
    }

    /** Returns the standard jar filename, e.g. {@code "fabric-loader-0.15.11.jar"}. */
    public String getFileName() {
        return artifact + "-" + version + ".jar";
    }

    /**
     * Returns the relative path within a Maven repository, e.g.
     * {@code "net/fabricmc/fabric-loader/0.15.11/fabric-loader-0.15.11.jar"}.
     */
    public String getRelativePath() {
        return group + "/" + artifact + "/" + version + "/" + getFileName();
    }

    /**
     * Resolves this coordinate to an absolute {@link Path} under the given
     * {@code librariesDir}.
     *
     * @param librariesDir the root {@code libraries/} directory
     * @return the fully resolved {@link Path} for the jar file
     */
    public Path resolveIn(Path librariesDir) {
        return librariesDir.resolve(group).resolve(artifact).resolve(version).resolve(getFileName());
    }
}
