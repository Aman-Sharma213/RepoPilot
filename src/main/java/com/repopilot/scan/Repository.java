package com.repopilot.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** The result of scanning a repository: accepted Java files plus an explainable skip list. */
public record Repository(Path root, List<ScannedFile> javaFiles, List<IgnoredFile> ignored,
                         RepositoryMetadata metadata) {

    public Repository {
        javaFiles = List.copyOf(javaFiles);
        ignored = List.copyOf(ignored);
    }

    public boolean isEmpty() {
        return javaFiles.isEmpty();
    }

    public Optional<ScannedFile> file(String relativePath) {
        return javaFiles.stream().filter(f -> f.relativePath().equals(relativePath)).findFirst();
    }
}
