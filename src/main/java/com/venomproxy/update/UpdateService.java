package com.venomproxy.update;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;

public class UpdateService {
    private final String currentVersion;
    private final GitHubReleaseClient client;
    private final Path downloadDirectory;

    public UpdateService(String currentVersion, GitHubReleaseClient client, Path downloadDirectory) {
        this.currentVersion = currentVersion;
        this.client = client;
        this.downloadDirectory = downloadDirectory;
    }

    public UpdateInfo checkForUpdates() throws Exception {
        GitHubReleaseClient.ReleaseData release = client.fetchLatest();
        SemanticVersion current = SemanticVersion.parse(currentVersion);
        SemanticVersion latest = SemanticVersion.parse(release.tagName());
        Optional<GitHubReleaseClient.AssetData> installer = installerAsset(release);
        return new UpdateInfo(currentVersion, release.tagName(), latest.isNewerThan(current),
                release.body(), release.htmlUrl(),
                installer.map(GitHubReleaseClient.AssetData::name).orElse(""),
                installer.map(GitHubReleaseClient.AssetData::browserDownloadUrl).orElse(""));
    }

    public Path downloadInstaller(UpdateInfo updateInfo, Consumer<Double> progress) throws Exception {
        if (updateInfo == null || updateInfo.downloadUrl() == null || updateInfo.downloadUrl().isBlank()) {
            throw new IllegalArgumentException("No installer asset is available for this release.");
        }
        String fileName = updateInfo.assetName() == null || updateInfo.assetName().isBlank()
                ? "CyvoraX-Setup-" + updateInfo.latestVersion().replaceFirst("^[vV]", "") + ".exe"
                : updateInfo.assetName();
        return client.download(updateInfo.downloadUrl(), downloadDirectory.resolve(fileName), progress);
    }

    public String currentVersion() {
        return currentVersion;
    }

    public Path downloadDirectory() {
        return downloadDirectory;
    }

    private Optional<GitHubReleaseClient.AssetData> installerAsset(GitHubReleaseClient.ReleaseData release) {
        return release.assets().stream()
                .filter(asset -> asset.name().toLowerCase().endsWith(".exe"))
                .filter(asset -> asset.name().toLowerCase().contains("setup"))
                .max(Comparator.comparing(GitHubReleaseClient.AssetData::name))
                .or(() -> release.assets().stream()
                        .filter(asset -> asset.name().toLowerCase().endsWith(".exe"))
                        .findFirst());
    }
}
