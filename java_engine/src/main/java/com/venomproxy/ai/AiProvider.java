package com.venomproxy.ai;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

public enum AiProvider {
    GROQ("groq", "Groq", "GROQ_API_KEY", "groq.token", "groq.model",
            URI.create("https://api.groq.com/openai/v1/models"), "llama-3.3-70b-versatile"),
    OPENROUTER("openrouter", "OpenRouter", "OPENROUTER_API_KEY", "openrouter.token", "openrouter.model",
            URI.create("https://openrouter.ai/api/v1/models"), "openai/gpt-4o-mini"),
    CEREBRAS("cerebras", "Cerebras", "CEREBRAS_API_KEY", "cerebras.token", "cerebras.model",
            URI.create("https://api.cerebras.ai/v1/models"), "llama3.1-8b"),
    MISTRAL("mistral", "Mistral", "MISTRAL_API_KEY", "mistral.token", "mistral.model",
            URI.create("https://api.mistral.ai/v1/models"), "mistral-small-latest");

    private final String id;
    private final String displayName;
    private final String environmentVariable;
    private final String tokenProperty;
    private final String modelProperty;
    private final URI modelsEndpoint;
    private final String defaultModel;

    AiProvider(String id, String displayName, String environmentVariable, String tokenProperty,
               String modelProperty, URI modelsEndpoint, String defaultModel) {
        this.id = id;
        this.displayName = displayName;
        this.environmentVariable = environmentVariable;
        this.tokenProperty = tokenProperty;
        this.modelProperty = modelProperty;
        this.modelsEndpoint = modelsEndpoint;
        this.defaultModel = defaultModel;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String environmentVariable() {
        return environmentVariable;
    }

    public String tokenProperty() {
        return tokenProperty;
    }

    public String modelProperty() {
        return modelProperty;
    }

    public URI modelsEndpoint() {
        return modelsEndpoint;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public static Optional<AiProvider> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(provider -> provider.id.equalsIgnoreCase(id.trim())
                        || provider.displayName.equalsIgnoreCase(id.trim()))
                .findFirst();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
