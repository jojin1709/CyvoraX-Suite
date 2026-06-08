package com.venomproxy.update;

import com.venomproxy.util.SecretMasker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class UpdateService {
    private static final String PRIVATE_REPOSITORY_MESSAGE =
            "This repository is private. Configure a GitHub token in Updater Settings.";

    private final String currentVersion;
    private final GitHubReleaseClient fixedClient;
    private final UpdaterConfig config;
    private final Path downloadDirectory;
    private final Function<UpdaterConfig.Settings, GitHubReleaseClient> clientFactory;
    private volatile UpdaterDiagnostics lastDiagnostics;

    public UpdateService(String currentVersion, GitHubReleaseClient client, Path downloadDirectory) {
        this.currentVersion = currentVersion;
        this.fixedClient = client;
        this.config = null;
        this.downloadDirectory = downloadDirectory;
        this.clientFactory = settings -> client;
        this.lastDiagnostics = new UpdaterDiagnostics(currentVersion, "Unknown", "Never",
                client != null && client.isAuthenticated() ? "Token configured" : "No token configured",
                "Not checked");
    }

    public UpdateService(String currentVersion, UpdaterConfig config, Path downloadDirectory) {
        this(currentVersion, config, downloadDirectory,
                settings -> new GitHubReleaseClient(settings.owner(), settings.repository(), settings.effectiveToken()));
    }

    UpdateService(String currentVersion, UpdaterConfig config, Path downloadDirectory,
                  Function<UpdaterConfig.Settings, GitHubReleaseClient> clientFactory) {
        this.currentVersion = currentVersion;
        this.fixedClient = null;
        this.config = config;
        this.downloadDirectory = downloadDirectory;
        this.clientFactory = clientFactory;
        this.lastDiagnostics = diagnosticsFrom(config == null ? null : config.load(), "Not checked");
    }

    public UpdateInfo checkForUpdates() throws Exception {
        UpdaterConfig.Settings settings = settings();
        GitHubReleaseClient client = clientFor(settings);
        try {
            GitHubReleaseClient.ReleaseData release = client.fetchLatest();
            SemanticVersion current = SemanticVersion.parse(currentVersion);
            SemanticVersion latest = SemanticVersion.parse(release.tagName());
            Optional<GitHubReleaseClient.AssetData> installer = installerAsset(release);
            UpdateInfo info = new UpdateInfo(currentVersion, release.tagName(), latest.isNewerThan(current),
                    release.body(), release.htmlUrl(),
                    installer.map(GitHubReleaseClient.AssetData::name).orElse(""),
                    installer.map(GitHubReleaseClient.AssetData::browserDownloadUrl).orElse(""));
            recordCheck(info.latestVersion(), "Connected to " + settings.owner() + "/" + settings.repository());
            return info;
        } catch (GitHubReleaseException ex) {
            String message = releaseErrorMessage(settings, ex);
            recordCheck(null, message);
            throw new IOException(message, ex);
        } catch (Exception ex) {
            String message = SecretMasker.maskSecrets("GitHub release check failed: " + safeMessage(ex));
            recordCheck(null, message);
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IOException(message, ex);
        }
    }

    public Path downloadInstaller(UpdateInfo updateInfo, Consumer<Double> progress) throws Exception {
        if (updateInfo == null || updateInfo.downloadUrl() == null || updateInfo.downloadUrl().isBlank()) {
            throw new IllegalArgumentException("No installer asset is available for this release.");
        }
        String fileName = updateInfo.assetName() == null || updateInfo.assetName().isBlank()
                ? "CyvoraX-Setup-" + updateInfo.latestVersion().replaceFirst("^[vV]", "") + ".exe"
                : updateInfo.assetName();
        return clientFor(settings()).download(updateInfo.downloadUrl(), downloadDirectory.resolve(fileName), progress);
    }

    public String currentVersion() {
        return currentVersion;
    }

    public Path downloadDirectory() {
        return downloadDirectory;
    }

    public String repositoryOwner() {
        return settings().owner();
    }

    public String repositoryName() {
        return settings().repository();
    }

    public String maskedToken() {
        return config == null ? "" : config.maskedEffectiveToken();
    }

    public UpdaterDiagnostics diagnostics() {
        if (config == null) {
            return lastDiagnostics;
        }
        lastDiagnostics = diagnosticsFrom(config.load(), lastDiagnostics.repositoryStatus());
        return lastDiagnostics;
    }

    public void saveUpdaterSettings(String owner, String repository, String tokenInput) {
        if (config == null) {
            return;
        }
        config.save(owner, repository, tokenInput);
        lastDiagnostics = diagnosticsFrom(config.load(), "Settings saved");
    }

    public UpdateConnectionResult testConnection() {
        try {
            checkForUpdates();
            return new UpdateConnectionResult(true, "Connected successfully", diagnostics());
        } catch (Exception ex) {
            String message = authenticationFailureMessage(ex);
            return new UpdateConnectionResult(false, message, diagnostics());
        }
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

    private UpdaterConfig.Settings settings() {
        if (config == null) {
            String owner = fixedClient == null ? UpdaterConfig.DEFAULT_OWNER : fixedClient.owner();
            String repository = fixedClient == null ? UpdaterConfig.DEFAULT_REPOSITORY : fixedClient.repository();
            return new UpdaterConfig.Settings(owner, repository, "", "", "Never", "Unknown",
                    lastDiagnostics == null ? "Not checked" : lastDiagnostics.repositoryStatus());
        }
        return config.load();
    }

    private GitHubReleaseClient clientFor(UpdaterConfig.Settings settings) {
        return clientFactory.apply(settings);
    }

    private String releaseErrorMessage(UpdaterConfig.Settings settings, GitHubReleaseException ex) {
        if (!settings.hasToken() && (ex.statusCode() == 401 || ex.statusCode() == 404)) {
            return PRIVATE_REPOSITORY_MESSAGE;
        }
        if (settings.hasToken() && (ex.statusCode() == 401 || ex.statusCode() == 404)) {
            return "Authentication failed. Verify the GitHub token in Updater Settings.";
        }
        return SecretMasker.maskSecrets("GitHub release check failed with HTTP " + ex.statusCode());
    }

    private String authenticationFailureMessage(Exception ex) {
        String message = safeMessage(ex);
        if (message.contains(PRIVATE_REPOSITORY_MESSAGE)) {
            return PRIVATE_REPOSITORY_MESSAGE;
        }
        if (message.toLowerCase(java.util.Locale.ROOT).contains("authentication failed")
                || message.contains("401") || message.contains("404")) {
            return "Authentication failed";
        }
        return SecretMasker.maskSecrets(message);
    }

    private void recordCheck(String latestVersion, String repositoryStatus) {
        String safeStatus = SecretMasker.maskSecrets(repositoryStatus);
        if (config != null) {
            config.recordCheck(latestVersion, safeStatus);
        }
        UpdaterConfig.Settings updated = settings();
        lastDiagnostics = new UpdaterDiagnostics(currentVersion,
                latestVersion == null || latestVersion.isBlank() ? updated.latestVersion() : latestVersion,
                updated.lastUpdateCheck(),
                updated.authenticationStatus(),
                safeStatus);
    }

    private UpdaterDiagnostics diagnosticsFrom(UpdaterConfig.Settings settings, String repositoryStatus) {
        if (settings == null) {
            return new UpdaterDiagnostics(currentVersion, "Unknown", "Never", "No token configured",
                    repositoryStatus == null ? "Not checked" : SecretMasker.maskSecrets(repositoryStatus));
        }
        return new UpdaterDiagnostics(currentVersion, settings.latestVersion(), settings.lastUpdateCheck(),
                settings.authenticationStatus(),
                repositoryStatus == null || repositoryStatus.isBlank()
                        ? settings.repositoryStatus()
                        : SecretMasker.maskSecrets(repositoryStatus));
    }

    private String safeMessage(Exception ex) {
        String message = ex == null ? "" : ex.getMessage();
        return SecretMasker.maskSecrets(message == null || message.isBlank()
                ? ex == null ? "Unknown update error" : ex.getClass().getSimpleName()
                : message);
    }
}
