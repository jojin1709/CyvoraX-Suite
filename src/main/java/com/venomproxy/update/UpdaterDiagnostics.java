package com.venomproxy.update;

public record UpdaterDiagnostics(String currentVersion, String latestVersion, String lastUpdateCheck,
                                 String authenticationStatus, String repositoryStatus, String apiUrl,
                                 String assetUrl, int httpStatus, int assetCount) {
    public UpdaterDiagnostics(String currentVersion, String latestVersion, String lastUpdateCheck,
                              String authenticationStatus, String repositoryStatus) {
        this(currentVersion, latestVersion, lastUpdateCheck, authenticationStatus, repositoryStatus, "", "", 0, 0);
    }

    public String toDisplayString() {
        return "Updater diagnostics\n"
                + "Current version: " + blank(currentVersion) + "\n"
                + "Latest version: " + blank(latestVersion) + "\n"
                + "Last update check: " + blank(lastUpdateCheck) + "\n"
                + "Authentication status: " + blank(authenticationStatus) + "\n"
                + "Repository status: " + blank(repositoryStatus) + "\n"
                + "API URL used: " + blank(apiUrl) + "\n"
                + "Asset URL used: " + blank(assetUrl) + "\n"
                + "HTTP status: " + (httpStatus <= 0 ? "Unknown" : httpStatus) + "\n"
                + "Asset count detected: " + assetCount + "\n";
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }
}
