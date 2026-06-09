package com.venomproxy.update;

public record UpdateInfo(String currentVersion, String latestVersion, boolean updateAvailable,
                         String releaseNotes, String releaseUrl, String releaseDate, String assetName,
                         long assetSizeBytes, String sha256, String downloadUrl, String assetApiUrl,
                         String releaseApiUrl, int assetCount) {
    public UpdateInfo(String currentVersion, String latestVersion, boolean updateAvailable,
                      String releaseNotes, String releaseUrl, String assetName, String downloadUrl) {
        this(currentVersion, latestVersion, updateAvailable, releaseNotes, releaseUrl, "", assetName,
                0L, "", downloadUrl, downloadUrl, "", 0);
    }

    public static UpdateInfo unavailable(String currentVersion, String message) {
        return new UpdateInfo(currentVersion, currentVersion, false, message, "", "", "",
                0L, "", "", "", "", 0);
    }
}
