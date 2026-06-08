package com.venomproxy.ai;

import java.util.Map;

public record AiProviderSettings(AiProvider activeProvider, Map<AiProvider, ProviderSettings> providers) {
    public ProviderSettings active() {
        return providers.getOrDefault(activeProvider, ProviderSettings.empty(activeProvider));
    }

    public record ProviderSettings(AiProvider provider, String model, String environmentToken,
                                   String localToken, String lastChecked, String lastStatus,
                                   int lastModelCount) {
        public static ProviderSettings empty(AiProvider provider) {
            return new ProviderSettings(provider, provider.defaultModel(), "", "", "Never", "Not checked", 0);
        }

        public String effectiveToken() {
            return environmentToken == null || environmentToken.isBlank() ? blank(localToken) : environmentToken.trim();
        }

        public boolean hasToken() {
            return !effectiveToken().isBlank();
        }

        public String authenticationStatus() {
            if (environmentToken != null && !environmentToken.isBlank()) {
                return "Environment key configured";
            }
            if (localToken != null && !localToken.isBlank()) {
                return "Local encrypted key configured";
            }
            return "No key configured";
        }

        private String blank(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
