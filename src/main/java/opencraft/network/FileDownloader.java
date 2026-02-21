package opencraft.network;

import opencraft.execution.LaunchConstants;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared utility for downloading files over HTTP.
 *
 * <p>The standard {@link LaunchConstants#USER_AGENT} header is sent with every request.
 */
public final class FileDownloader {

    private static final HttpClient client = HttpClient.newHttpClient();

    private FileDownloader() {}

    /**
     * Downloads a file from {@code url} to {@code dest}, replacing it if it already exists.
     *
     * @param url  the URL to download from
     * @param dest the destination path (parent directories are created as needed)
     * @throws IOException          if the HTTP response status is not 200, or on I/O error
     * @throws InterruptedException if the request is interrupted
     */
    public static void download(String url, Path dest) throws IOException, InterruptedException {
        Files.createDirectories(dest.getParent());

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", LaunchConstants.USER_AGENT)
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }

        try (InputStream in = response.body()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
