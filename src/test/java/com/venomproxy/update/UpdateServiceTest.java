package com.venomproxy.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateServiceTest {
    private static final String LOCAL_TOKEN = token("serviceToken");
    private static final String ENV_TOKEN = token("envServiceToken");

    @TempDir
    Path tempDir;

    @Test
    void usesEnvironmentTokenForAuthenticatedReleaseChecks() throws Exception {
        UpdaterConfig config = new UpdaterConfig(tempDir.resolve("updater.properties"),
                Map.of(UpdaterConfig.ENV_TOKEN, ENV_TOKEN));
        AtomicReference<String> usedToken = new AtomicReference<>();
        UpdateService service = new UpdateService("1.0.0", config, tempDir.resolve("updates"), settings -> {
            usedToken.set(settings.effectiveToken());
            return new SuccessClient();
        });

        UpdateInfo info = service.checkForUpdates();

        assertEquals(ENV_TOKEN, usedToken.get());
        assertTrue(info.updateAvailable());
        assertEquals("v1.2.0", info.latestVersion());
        assertEquals("Environment token configured", service.diagnostics().authenticationStatus());
    }

    @Test
    void missingTokenOnPrivateRepositoryGetsClearMessage() {
        UpdaterConfig config = new UpdaterConfig(tempDir.resolve("updater.properties"), Map.of());
        UpdateService service = new UpdateService("1.0.0", config, tempDir.resolve("updates"),
                settings -> new FailingClient(404));

        IOException ex = assertThrows(IOException.class, service::checkForUpdates);

        assertEquals("This repository is private. Configure a GitHub token in Updater Settings.", ex.getMessage());
        assertEquals("No token configured", service.diagnostics().authenticationStatus());
    }

    @Test
    void invalidTokenConnectionTestReturnsAuthenticationFailed() {
        UpdaterConfig config = new UpdaterConfig(tempDir.resolve("updater.properties"), Map.of());
        config.save("owner", "repo", LOCAL_TOKEN);
        UpdateService service = new UpdateService("1.0.0", config, tempDir.resolve("updates"),
                settings -> new FailingClient(401));

        UpdateConnectionResult result = service.testConnection();

        assertFalse(result.success());
        assertEquals("Authentication failed", result.message());
        assertEquals("Local updater token configured", result.diagnostics().authenticationStatus());
    }

    private static class SuccessClient extends GitHubReleaseClient {
        SuccessClient() {
            super("owner", "repo", "");
        }

        @Override
        public ReleaseData fetchLatest() {
            return new ReleaseData("v1.2.0", "CyvoraX Suite v1.2.0", "Release notes",
                    "https://github.com/jojin1709/CyvoraX-Suite/releases/tag/v1.2.0",
                    List.of(new AssetData("CyvoraX-Setup-1.2.0.exe", "https://example.test/setup.exe")));
        }
    }

    private static class FailingClient extends GitHubReleaseClient {
        private final int statusCode;

        FailingClient(int statusCode) {
            super("owner", "repo", "");
            this.statusCode = statusCode;
        }

        @Override
        public ReleaseData fetchLatest() throws IOException {
            throw new GitHubReleaseException("GitHub release check failed with HTTP " + statusCode, statusCode);
        }
    }

    private static String token(String label) {
        return "gh" + "p_" + label + "1234567890";
    }
}
