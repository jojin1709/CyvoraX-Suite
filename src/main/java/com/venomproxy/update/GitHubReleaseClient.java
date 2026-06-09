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
    private final URI apiBaseUri;

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
        this.apiBaseUri = URI.create("https://api.github.com");
    }

    GitHubReleaseClient(String owner, String repository, String token, HttpClient client, URI apiBaseUri) {
        this.owner = owner;
        this.repository = repository;
        this.token = token == null ? "" : token.trim();
        this.client = client == null ? HttpClient.newHttpClient() : client;
        this.apiBaseUri = apiBaseUri == null ? URI.create("https://api.github.com") : apiBaseUri;
    }

    public ReleaseData fetchLatest() throws IOException, InterruptedException {
        URI uri = latestReleaseApiUri();
        HttpRequest request = addAuth(HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GitHubReleaseException("GitHub release check failed with HTTP " + response.statusCode(), response.statusCode());
        }
        return parseRelease(response.body());
    }

    public Path download(String url, Path destination, Consumer<Double> progress) throws IOException, InterruptedException {
        return downloadWithProgress(url, destination, download -> {
            if (progress != null) {
                progress.accept(download.progress());
            }
        });
    }

    public Path downloadWithProgress(String url, Path destination, Consumer<DownloadProgress> progress) throws IOException, InterruptedException {
        Files.createDirectories(destination.getParent());
        HttpRequest request = addAuth(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("Accept", "application/octet-stream"))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GitHubReleaseException("Update download failed with HTTP " + response.statusCode(), response.statusCode());
        }
        long length = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        long started = System.nanoTime();
        try (InputStream input = response.body()) {
            Path partial = destination.resolveSibling(destination.getFileName() + ".part");
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            try (var output = Files.newOutputStream(partial)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                    total += read;
                    publishProgress(progress, total, length, started);
                }
            }
            Files.move(partial, destination, StandardCopyOption.REPLACE_EXISTING);
            if (progress != null) {
                progress.accept(new DownloadProgress(total, length, 1.0, speedBytesPerSecond(total, started), 0));
            }
        }
        return destination;
    }

    ReleaseData parseRelease(String json) {
        String tag = extractString(json, "tag_name").orElse("0.0.0");
        String name = extractString(json, "name").orElse(tag);
        String body = extractString(json, "body").orElse("");
        String htmlUrl = extractString(json, "html_url").orElse("");
        String publishedAt = extractString(json, "published_at").orElse("");
        return new ReleaseData(tag, name, body, htmlUrl, publishedAt, latestReleaseApiUri().toString(), extractAssets(json));
    }

    public URI latestReleaseApiUri() {
        return apiBaseUri.resolve("/repos/" + owner + "/" + repository + "/releases/latest");
    }

    private HttpRequest.Builder addAuth(HttpRequest.Builder builder) {
        if (!token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    public boolean isAuthenticated() {
        return !token.isBlank();
    }

    public String owner() {
        return owner;
    }

    public String repository() {
        return repository;
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
        int arrayStart = json.indexOf('[', assetsIndex);
        int arrayEnd = matchingBracket(json, arrayStart, '[', ']');
        if (arrayStart < 0 || arrayEnd < 0) {
            return assets;
        }
        String assetsJson = json.substring(arrayStart + 1, arrayEnd);
        int cursor = 0;
        while (cursor < assetsJson.length()) {
            int objectStart = assetsJson.indexOf('{', cursor);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingBracket(assetsJson, objectStart, '{', '}');
            if (objectEnd < 0) {
                break;
            }
            String object = assetsJson.substring(objectStart, objectEnd + 1);
            String assetName = extractString(object, "name").orElse("release-asset");
            String apiUrl = extractString(object, "url").orElse("");
            String browserUrl = extractString(object, "browser_download_url").orElse("");
            long size = extractLong(object, "size").orElse(0L);
            String digest = extractString(object, "digest")
                    .or(() -> extractString(object, "sha256"))
                    .orElse("");
            assets.add(new AssetData(assetName, apiUrl, browserUrl, size, digest));
            cursor = objectEnd + 1;
        }
        return assets;
    }

    private Optional<Long> extractLong(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
    }

    private int matchingBracket(String text, int start, char open, char close) {
        if (start < 0 || start >= text.length() || text.charAt(start) != open) {
            return -1;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = inString;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void publishProgress(Consumer<DownloadProgress> progress, long downloadedBytes, long totalBytes, long started) {
        if (progress == null) {
            return;
        }
        double speed = speedBytesPerSecond(downloadedBytes, started);
        double ratio = totalBytes <= 0 ? -1 : Math.min(1.0, (double) downloadedBytes / totalBytes);
        long eta = totalBytes <= 0 || speed <= 0 ? -1 : Math.max(0, Math.round((totalBytes - downloadedBytes) / speed));
        progress.accept(new DownloadProgress(downloadedBytes, totalBytes, ratio, speed, eta));
    }

    private double speedBytesPerSecond(long downloadedBytes, long started) {
        double elapsedSeconds = Math.max(0.001, (System.nanoTime() - started) / 1_000_000_000.0);
        return downloadedBytes / elapsedSeconds;
    }

    private String unescape(String value) {
        return value.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public record ReleaseData(String tagName, String name, String body, String htmlUrl, String publishedAt,
                              String apiUrl, List<AssetData> assets) {
        public ReleaseData(String tagName, String name, String body, String htmlUrl, List<AssetData> assets) {
            this(tagName, name, body, htmlUrl, "", "", assets);
        }
    }

    public record AssetData(String name, String apiUrl, String browserDownloadUrl, long sizeBytes, String sha256) {
        public AssetData(String name, String browserDownloadUrl) {
            this(name, "", browserDownloadUrl, 0L, "");
        }

        public String downloadUrl() {
            return apiUrl == null || apiUrl.isBlank() ? browserDownloadUrl : apiUrl;
        }
    }

    public record DownloadProgress(long downloadedBytes, long totalBytes, double progress,
                                   double bytesPerSecond, long etaSeconds) {
    }
}
