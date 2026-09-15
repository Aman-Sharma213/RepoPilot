package com.repopilot.incremental;

import com.repopilot.scan.ScannedFile;
import com.repopilot.util.Hashing;

import java.io.IOException;

/**
 * Fingerprint of an indexed file. Content hashing (not just mtime) makes change detection reliable
 * across checkouts, and makes rename detection possible.
 */
public record FileRecord(String path, long size, long lastModified, String sha256, int symbolCount) {

    public static FileRecord of(ScannedFile file, int symbolCount) throws IOException {
        return new FileRecord(file.relativePath(), file.sizeBytes(), file.lastModified(),
                Hashing.sha256(file.absolutePath()), symbolCount);
    }

    public boolean sameContentAs(FileRecord other) {
        return other != null && sha256.equals(other.sha256);
    }

    /** Cheap check first: identical size and timestamp means the content hash can be trusted. */
    public boolean unchangedMetadata(ScannedFile file) {
        return file.sizeBytes() == size && file.lastModified() == lastModified;
    }
}
