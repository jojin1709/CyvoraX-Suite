package com.venomproxy.diagnostics;

import java.nio.file.Path;
import java.time.Instant;

public record CrashReport(Path path, Instant timestamp, String summary, String content) {
}
