package com.venomproxy.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitHubReleaseClient {
    private final HttpClient client;
    private final String owner;
    private final String repository;
    private final String token;

    public GitHubReleaseClient(String owner, String repository) {
        this(owner, repository, System.getenv("CYVORAX_GITHUB_TOKEN"));
    }

    public GitHubReleaseClient(String owner, String repository, String token) {
        this.owner = owner;
        this.repository = repository;
        this.token = token == null ? "" : token.trim();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public ReleaseData fetchLatest() throws IOException, InterruptedException {
        URI uri = URI.create("https://api.github.com/repos/" + owner + "/" + repository + "/releases/latest");
        HttpRequest request = addAuth(HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub release check failed with HTTP " + response.statusCode());
        }
        return parseRelease(response.body());
    }

    public Path download(String url, Path destination, Consumer<Double> progress) throws IOException, InterruptedException {
        Files.createDirectories(destination.getParent());
        HttpRequest request = addAuth(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("Accept", "application/octet-stream"))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Update download failed with HTTP " + response.statusCode());
        }
        long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        try (InputStream input = response.body()) {
            if (length <= 0 || progress == null) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                if (progress != null) {
                    progress.accept(1.0);
                }
                return destination;
            }
            Path partial = destination.resolveSibling(destination.getFileName() + ".part");
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            try (var output = Files.newOutputStream(partial)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                    total += read;
                    progress.accept(Math.min(1.0, (double) total / length));
                }
            }
            Files.move(partial, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return destination;
    }

    ReleaseData parseRelease(String json) {
        String tag = extractString(json, "tag_name").orElse("0.0.0");
        String name = extractString(json, "name").orElse(tag);
        String body = extractString(json, "body").orElse("");
        String htmlUrl = extractString(json, "html_url").orElse("");
        return new ReleaseData(tag, name, body, htmlUrl, extractAssets(json));
    }

    private HttpRequest.Builder addAuth(HttpRequest.Builder builder) {
        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private Optional<String> extractString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Optional.of(unescape(matcher.group(1))) : Optional.empty();
    }

    private List<AssetData> extractAssets(String json) {
        int assetsIndex = json.indexOf("\"assets\"");
        if (assetsIndex < 0) {
            return List.of();
        }
        List<AssetData> assets = new ArrayList<>();
        Pattern downloadPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL);
        Matcher matcher = downloadPattern.matcher(json.substring(assetsIndex));
        while (matcher.find()) {
            String before = json.substring(assetsIndex, assetsIndex + matcher.start());
            String name = lastNameBefore(before).orElse("release-asset");
            assets.add(new AssetData(unescape(name), unescape(matcher.group(1))));
        }
        return assets;
    }

    private Optional<String> lastNameBefore(String text) {
        Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL);
        Matcher matcher = namePattern.matcher(text);
        String value = null;
        while (matcher.find()) {
            value = matcher.group(1);
        }
        return value == null ? Optional.empty() : Optional.of(value);
    }

    private String unescape(String value) {
        return value.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public record ReleaseData(String tagName, String name, String body, String htmlUrl, List<AssetData> assets) {
    }

    public record AssetData(String name, String browserDownloadUrl) {
    }
}
