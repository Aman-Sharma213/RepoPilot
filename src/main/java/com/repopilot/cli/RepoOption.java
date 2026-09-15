package com.repopilot.cli;

import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Shared {@code --repo} option: the repository root, defaulting to the working directory. */
public class RepoOption {

    @Option(names = {"-r", "--repo-path"}, paramLabel = "<dir>",
            description = "Repository root to analyse (default: current working directory).")
    Path path;

    public Path resolveRoot() {
        Path base = path != null ? path : Paths.get("").toAbsolutePath();
        Path abs = base.toAbsolutePath().normalize();
        if (!Files.isDirectory(abs)) {
            throw new IllegalArgumentException("Not a directory: " + abs);
        }
        return abs;
    }
}
