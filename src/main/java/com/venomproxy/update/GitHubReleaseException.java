package com.venomproxy.update;

import java.io.IOException;

public class GitHubReleaseException extends IOException {
    private final int statusCode;

    public GitHubReleaseException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
