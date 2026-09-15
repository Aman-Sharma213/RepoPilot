package com.repopilot.git;

import com.repopilot.index.RepoIndex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Materialized immutable Git tree plus the index built from that exact tree. */
public record RevisionSnapshot(Path root, RevisionProvenance provenance, RepoIndex index) implements AutoCloseable {
    public String read(String relativePath) throws IOException {
        if (relativePath == null) return null;
        Path file = com.repopilot.security.SafePath.resolveInside(root, relativePath);
        return Files.isRegularFile(file) ? Files.readString(file) : null;
    }

    @Override public void close() throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
