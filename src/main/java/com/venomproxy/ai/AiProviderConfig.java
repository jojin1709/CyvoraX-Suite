package com.venomproxy.ai;

import com.venomproxy.util.LocalSecretProtector;
import com.venomproxy.util.SecretMasker;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

public class AiProviderConfig {
    private static final String ACTIVE_PROVIDER = "active.provider";
    private static final String EXCLUDE_BACKUPS = "backup.exclude";
    private static final String LAST_CHECKED_SUFFIX = ".lastChecked";
    private static final String LAST_STATUS_SUFFIX = ".lastStatus";
    private static final String LAST_MODEL_COUNT_SUFFIX = ".lastModelCount";
    private static final String PURPOSE = "CyvoraX AI provider keys";

    private final Path configPath;
    private final Supplier<Map<String, String>> environmentSupplier;

    public AiProviderConfig(Path appDirectory) {
        this(appDirectory.resolve("config").resolve("ai-providers.properties"), System::getenv);
    }

    public AiProviderConfig(Path configPath, Map<String, String> environment) {
        this(configPath, () -> environment == null ? Map.of() : environment);
    }

    public AiProviderConfig(Path configPath, Supplier<Map<String, String>> environmentSupplier) {
        this.configPath = configPath;
        this.environmentSupplier = environmentSupplier == null ? Map::of : environmentSupplier;
    }

    public synchronized AiProviderSettings load() {
        Properties properties = loadProperties();
        Map<String, String> environment = environmentSupplier.get();
        if (environment == null) {
            environment = Map.of();
        }
        AiProvider active = AiProvider.fromId(properties.getProperty(ACTIVE_PROVIDER, AiProvider.GROQ.id()))
                .orElse(AiProvider.GROQ);
        EnumMap<AiProvider, AiProviderSettings.ProviderSettings> providers = new EnumMap<>(AiProvider.class);
        for (AiProvider provider : AiProvider.values()) {
            providers.put(provider, loadProvider(properties, environment, provider));
        }
        return new AiProviderSettings(active, Map.copyOf(providers));
    }

    public synchronized void saveActiveProvider(AiProvider provider) {
        Properties properties = loadProperties();
        properties.setProperty(ACTIVE_PROVIDER, provider == null ? AiProvider.GROQ.id() : provider.id());
        properties.setProperty(EXCLUDE_BACKUPS, "true");
        store(properties);
    }

    public synchronized void saveProvider(AiProvider provider, String model, String tokenInput) {
        if (provider == null) {
            return;
        }
        Properties properties = loadProperties();
        properties.setProperty(ACTIVE_PROVIDER, provider.id());
        properties.setProperty(EXCLUDE_BACKUPS, "true");
        properties.setProperty(provider.modelProperty(), clean(model, provider.defaultModel()));
        String token = tokenInput == null ? "" : tokenInput.trim();
        if (!isMaskedToken(token)) {
            if (token.isBlank()) {
                properties.remove(provider.tokenProperty());
            } else {
                properties.setProperty(provider.tokenProperty(), LocalSecretProtector.encrypt(token, PURPOSE + ":" + provider.id()));
            }
        }
        store(properties);
    }

    public synchronized void recordConnection(AiConnectionResult result) {
        if (result == null || result.provider() == null) {
            return;
        }
        Properties properties = loadProperties();
        String prefix = result.provider().id();
        properties.setProperty(prefix + LAST_CHECKED_SUFFIX, Instant.now().toString());
        properties.setProperty(prefix + LAST_STATUS_SUFFIX, SecretMasker.maskSecrets(result.message()));
        properties.setProperty(prefix + LAST_MODEL_COUNT_SUFFIX, String.valueOf(Math.max(0, result.modelCount())));
        store(properties);
    }

    public synchronized String maskedToken(AiProvider provider) {
        if (provider == null) {
            return "";
        }
        return SecretMasker.maskToken(load().providers().getOrDefault(provider,
                AiProviderSettings.ProviderSettings.empty(provider)).effectiveToken());
    }

    public synchronized String diagnostics() {
        AiProviderSettings settings = load();
        StringBuilder builder = new StringBuilder("AI provider diagnostics\n");
        builder.append("Active provider: ").append(settings.activeProvider().displayName()).append('\n');
        for (AiProvider provider : AiProvider.values()) {
            AiProviderSettings.ProviderSettings providerSettings = settings.providers().get(provider);
            builder.append(provider.displayName()).append(": ")
                    .append(providerSettings.authenticationStatus())
                    .append(", model=").append(providerSettings.model())
                    .append(", lastChecked=").append(providerSettings.lastChecked())
                    .append(", status=").append(providerSettings.lastStatus())
                    .append('\n');
        }
        return SecretMasker.maskSecrets(builder.toString());
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

    private AiProviderSettings.ProviderSettings loadProvider(Properties properties, Map<String, String> environment,
                                                            AiProvider provider) {
        String environmentToken = environment.getOrDefault(provider.environmentVariable(), "").trim();
        String localToken = decryptLocalToken(provider, properties.getProperty(provider.tokenProperty(), ""));
        String model = clean(properties.getProperty(provider.modelProperty(), provider.defaultModel()), provider.defaultModel());
        String prefix = provider.id();
        return new AiProviderSettings.ProviderSettings(provider, model, environmentToken, localToken,
                properties.getProperty(prefix + LAST_CHECKED_SUFFIX, "Never"),
                properties.getProperty(prefix + LAST_STATUS_SUFFIX, "Not checked"),
                parseInt(properties.getProperty(prefix + LAST_MODEL_COUNT_SUFFIX, "0")));
    }

    private String decryptLocalToken(AiProvider provider, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return LocalSecretProtector.decrypt(value.trim(), PURPOSE + ":" + provider.id());
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private Properties loadProperties() {
        ensureConfigFile();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read AI provider configuration", ex);
        }
    }

    private void ensureConfigFile() {
        try {
            Files.createDirectories(configPath.getParent());
            if (!Files.exists(configPath)) {
                Properties defaults = new Properties();
                defaults.setProperty(ACTIVE_PROVIDER, AiProvider.GROQ.id());
                defaults.setProperty(EXCLUDE_BACKUPS, "true");
                for (AiProvider provider : AiProvider.values()) {
                    defaults.setProperty(provider.modelProperty(), provider.defaultModel());
                }
                try (OutputStream output = Files.newOutputStream(configPath)) {
                    defaults.store(output, "CyvoraX AI provider configuration - local only, encrypted keys");
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not initialize AI provider configuration", ex);
        }
    }

    private void store(Properties properties) {
        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "CyvoraX AI provider configuration - local only, encrypted keys");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write AI provider configuration", ex);
        }
    }

    private String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (RuntimeException ex) {
            return 0;
        }
    }
}
