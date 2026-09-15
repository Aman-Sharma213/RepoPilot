package com.repopilot.scan;

import com.repopilot.scan.IgnoredFile.IgnoreReason;
import com.repopilot.security.ResourceLimits;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decides which files enter the index. The filter is deliberately conservative: build output,
 * VCS metadata, dependency caches, generated sources and binary blobs never reach the parser.
 */
public class FileFilter {

    /** Directory names that are never traversed. */
    public static final Set<String> DEFAULT_IGNORED_DIRECTORIES = new LinkedHashSet<>(List.of(
            ".git", ".hg", ".svn", ".repo-pilot",
            "target", "build", "out", "bin", "dist",
            "node_modules", ".gradle", ".mvn", ".idea", ".vscode", ".settings",
            ".venv", "venv", "__pycache__", ".pytest_cache"));

    /** Path fragments that indicate machine-generated sources. */
    public static final Set<String> GENERATED_PATH_FRAGMENTS = new LinkedHashSet<>(List.of(
            "generated-sources", "generated-test-sources", "src/gen", "/generated/"));

    private final Set<String> ignoredDirectories;
    private final ResourceLimits limits;

    public FileFilter() {
        this(DEFAULT_IGNORED_DIRECTORIES, ResourceLimits.defaults());
    }

    public FileFilter(Set<String> ignoredDirectories, ResourceLimits limits) {
        this.ignoredDirectories = Set.copyOf(ignoredDirectories);
        this.limits = limits;
    }

    public ResourceLimits limits() {
        return limits;
    }

    public boolean isIgnoredDirectory(String directoryName) {
        return ignoredDirectories.contains(directoryName);
    }

    public boolean isJavaSource(String relativePath) {
        return relativePath.endsWith(".java") && !relativePath.endsWith("module-info.java")
                && !relativePath.endsWith("package-info.java");
    }

    public boolean looksGenerated(String relativePath) {
        String normalised = "/" + relativePath;
        return GENERATED_PATH_FRAGMENTS.stream().anyMatch(normalised::contains);
    }

    /**
     * @return the reason to skip the file, or empty when the file should be indexed.
     */
    public Optional<IgnoredFile> reject(Path root, Path file, String relativePath) {
        if (Files.isSymbolicLink(file)) {
            return Optional.of(new IgnoredFile(relativePath, IgnoreReason.SYMLINK, "symlink not followed"));
        }
        if (!isJavaSource(relativePath)) {
            return Optional.of(new IgnoredFile(relativePath, IgnoreReason.NOT_JAVA, "not a Java source file"));
        }
        if (looksGenerated(relativePath)) {
            return Optional.of(new IgnoredFile(relativePath, IgnoreReason.GENERATED, "generated source path"));
        }
        try {
            long size = Files.size(file);
            if (size > limits.maxFileBytes()) {
                return Optional.of(new IgnoredFile(relativePath, IgnoreReason.TOO_LARGE, size + " bytes"));
            }
            if (isBinary(file)) {
                return Optional.of(new IgnoredFile(relativePath, IgnoreReason.BINARY, "binary content"));
            }
        } catch (IOException e) {
            return Optional.of(new IgnoredFile(relativePath, IgnoreReason.UNREADABLE, e.getClass().getSimpleName()));
        }
        return Optional.empty();
    }

    /** A file is treated as binary when its first bytes contain NUL or are not decodable as UTF-8. */
    public boolean isBinary(Path file) throws IOException {
        byte[] head = new byte[4096];
        int read;
        try (InputStream in = Files.newInputStream(file)) {
            read = in.readNBytes(head, 0, head.length);
        }
        for (int i = 0; i < read; i++) {
            if (head[i] == 0) {
                return true;
            }
        }
        String text = new String(head, 0, read, StandardCharsets.UTF_8);
        return text.indexOf('\uFFFD') >= 0;
    }
}
