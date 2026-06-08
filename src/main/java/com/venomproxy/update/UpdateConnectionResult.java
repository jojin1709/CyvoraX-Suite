package com.venomproxy.update;

public record UpdateConnectionResult(boolean success, String message, UpdaterDiagnostics diagnostics) {
}
