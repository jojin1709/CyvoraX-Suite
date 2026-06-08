package com.venomproxy.update;

import com.venomproxy.util.SecretMasker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

public class UpdaterConfig {
    public static final String ENV_TOKEN = "CYVORAX_GITHUB_TOKEN";
    public static final String DEFAULT_OWNER = "jojin1709";
    public static final String DEFAULT_REPOSITORY = "CyvoraX-Suite";
    public static final String TOKEN_PROPERTY = "github.token";
    private static final String OWNER_PROPERTY = "repository.owner";
    private static final String REPOSITORY_PROPERTY = "repository.name";
    private static final String EXCLUDE_BACKUPS_PROPERTY = "backup.exclude";
    private static final String LAST_CHECK_PROPERTY = "lastUpdateCheck";
    private static final String LATEST_VERSION_PROPERTY = "latestVersion";
    private static final String REPOSITORY_STATUS_PROPERTY = "repositoryStatus";

    private final Path configPath;
    private final Supplier<Map<String, String>> environmentSupplier;

    public UpdaterConfig(Path appDirectory) {
        this(appDirectory.resolve("config").resolve("updater.properties"), System::getenv);
    }

    public UpdaterConfig(Path configPath, Map<String, String> environment) {
        this(configPath, () -> environment == null ? Map.of() : environment);
    }

    public UpdaterConfig(Path configPath, Supplier<Map<String, String>> environmentSupplier) {
        this.configPath = configPath;
        this.environmentSupplier = environmentSupplier == null ? Map::of : environmentSupplier;
    }

    public synchronized Settings load() {
        Properties properties = loadProperties();
        Map<String, String> environment = environmentSupplier.get();
        String envToken = (environment == null ? Map.<String, String>of() : environment)
                .getOrDefault(ENV_TOKEN, "").trim();
        String localToken = properties.getProperty(TOKEN_PROPERTY, "").trim();
        String owner = clean(properties.getProperty(OWNER_PROPERTY, DEFAULT_OWNER), DEFAULT_OWNER);
        String repository = clean(properties.getProperty(REPOSITORY_PROPERTY, DEFAULT_REPOSITORY), DEFAULT_REPOSITORY);
        return new Settings(owner, repository, envToken, localToken,
                properties.getProperty(LAST_CHECK_PROPERTY, "Never"),
                properties.getProperty(LATEST_VERSION_PROPERTY, "Unknown"),
                properties.getProperty(REPOSITORY_STATUS_PROPERTY, "Not checked"));
    }

    public synchronized void save(String owner, String repository, String tokenInput) {
        Properties properties = loadProperties();
        properties.setProperty(OWNER_PROPERTY, clean(owner, DEFAULT_OWNER));
        properties.setProperty(REPOSITORY_PROPERTY, clean(repository, DEFAULT_REPOSITORY));
        properties.setProperty(EXCLUDE_BACKUPS_PROPERTY, "true");
        String token = tokenInput == null ? "" : tokenInput.trim();
        if (!isMaskedToken(token)) {
            if (token.isBlank()) {
                properties.remove(TOKEN_PROPERTY);
            } else {
                properties.setProperty(TOKEN_PROPERTY, token);
            }
        }
        store(properties);
    }

    public synchronized void recordCheck(String latestVersion, String repositoryStatus) {
        Properties properties = loadProperties();
        properties.setProperty(LAST_CHECK_PROPERTY, Instant.now().toString());
        if (latestVersion != null && !latestVersion.isBlank()) {
            properties.setProperty(LATEST_VERSION_PROPERTY, latestVersion);
        }
        properties.setProperty(REPOSITORY_STATUS_PROPERTY, SecretMasker.maskSecrets(repositoryStatus));
        store(properties);
    }

    public synchronized String maskedEffectiveToken() {
        return SecretMasker.maskToken(load().effectiveToken());
    }

    public Path configPath() {
        return configPath;
    }

    public static boolean isMaskedToken(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.endsWith("************")
                || trimmed.equals("************")
                || trimmed.contains("************");
    }

    private Properties loadProperties() {
        ensureConfigFile();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read updater configuration", ex);
        }
    }

    private void ensureConfigFile() {
        try {
            Files.createDirectories(configPath.getParent());
            if (!Files.exists(configPath)) {
                Properties defaults = new Properties();
                defaults.setProperty(OWNER_PROPERTY, DEFAULT_OWNER);
                defaults.setProperty(REPOSITORY_PROPERTY, DEFAULT_REPOSITORY);
                defaults.setProperty(EXCLUDE_BACKUPS_PROPERTY, "true");
                try (OutputStream output = Files.newOutputStream(configPath)) {
                    defaults.store(output, "CyvoraX updater configuration - local only, never commit tokens");
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not initialize updater configuration", ex);
        }
    }

    private void store(Properties properties) {
        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "CyvoraX updater configuration - local only, never commit tokens");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write updater configuration", ex);
        }
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record Settings(String owner, String repository, String environmentToken, String localToken,
                           String lastUpdateCheck, String latestVersion, String repositoryStatus) {
        public String effectiveToken() {
            return environmentToken == null || environmentToken.isBlank() ? nullToEmpty(localToken) : environmentToken.trim();
        }

        public boolean hasToken() {
            return !effectiveToken().isBlank();
        }

        public String authenticationStatus() {
            if (environmentToken != null && !environmentToken.isBlank()) {
                return "Environment token configured";
            }
            if (localToken != null && !localToken.isBlank()) {
                return "Local updater token configured";
            }
            return "No token configured";
        }

        private String nullToEmpty(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
