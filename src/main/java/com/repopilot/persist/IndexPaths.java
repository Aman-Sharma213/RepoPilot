package com.repopilot.persist;

import java.nio.file.Files;
import java.nio.file.Path;

/** Layout of the on-disk index directory: {@code <repo>/.repo-pilot/}. */
public record IndexPaths(Path repositoryRoot) {

    public static final String DIRECTORY = ".repo-pilot";
    /** Bumped whenever the persisted format changes in an incompatible way. */
    public static final int FORMAT_VERSION = 1;

    public Path directory() {
        return repositoryRoot.resolve(DIRECTORY);
    }

    public Path versionFile() {
        return directory().resolve("version");
    }

    public Path metadataFile() {
        return directory().resolve("metadata.json");
    }

    public Path symbolsFile() {
        return directory().resolve("symbols.json");
    }

    public Path edgesFile() {
        return directory().resolve("edges.json");
    }

    public Path filesFile() {
        return directory().resolve("files.json");
    }

    public boolean exists() {
        return Files.isDirectory(directory()) && Files.isRegularFile(versionFile());
    }

    public boolean hasIndex() {
        return exists() && Files.isRegularFile(symbolsFile()) && Files.isRegularFile(edgesFile());
    }
}
