package com.venomproxy.update;

public record UpdateInfo(String currentVersion, String latestVersion, boolean updateAvailable,
                         String releaseNotes, String releaseUrl, String assetName, String downloadUrl) {
    public static UpdateInfo unavailable(String currentVersion, String message) {
        return new UpdateInfo(currentVersion, currentVersion, false, message, "", "", "");
    }
}
