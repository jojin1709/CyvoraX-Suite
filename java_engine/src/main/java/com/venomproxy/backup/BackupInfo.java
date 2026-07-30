package com.venomproxy.backup;

import java.nio.file.Path;
import java.time.Instant;

public record BackupInfo(String id, String workspaceId, String workspaceName, Instant createdAt,
                         Path backupPath, long sizeBytes) {
}
