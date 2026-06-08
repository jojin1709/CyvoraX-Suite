package com.venomproxy.update;

public record UpdaterDiagnostics(String currentVersion, String latestVersion, String lastUpdateCheck,
                                 String authenticationStatus, String repositoryStatus) {
    public String toDisplayString() {
        return "Updater diagnostics\n"
                + "Current version: " + blank(currentVersion) + "\n"
                + "Latest version: " + blank(latestVersion) + "\n"
                + "Last update check: " + blank(lastUpdateCheck) + "\n"
                + "Authentication status: " + blank(authenticationStatus) + "\n"
                + "Repository status: " + blank(repositoryStatus) + "\n";
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }
}
