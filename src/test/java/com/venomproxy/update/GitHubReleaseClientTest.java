package com.venomproxy.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitHubReleaseClientTest {
    @Test
    void parsesLatestReleasePayload() {
        String json = """
                {
                  "tag_name": "v1.2.0",
                  "name": "CyvoraX Suite v1.2.0",
                  "body": "Foundation release",
                  "html_url": "https://github.com/jojin1709/CyvoraX-Suite/releases/tag/v1.2.0",
                  "assets": [
                    {
                      "name": "CyvoraX-Setup-1.2.0.exe",
                      "browser_download_url": "https://github.com/download/setup.exe"
                    }
                  ]
                }
                """;

        GitHubReleaseClient.ReleaseData release = new GitHubReleaseClient("owner", "repo", "").parseRelease(json);

        assertEquals("v1.2.0", release.tagName());
        assertEquals("Foundation release", release.body());
        assertEquals("CyvoraX-Setup-1.2.0.exe", release.assets().get(0).name());
        assertEquals("https://github.com/download/setup.exe", release.assets().get(0).browserDownloadUrl());
    }
}
