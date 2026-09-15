package com.repopilot.scan;

import com.repopilot.scan.IgnoredFile.IgnoreReason;
import com.repopilot.security.ResourceLimits;
import com.repopilot.security.SafePath;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Walks a repository and returns a deterministic, filtered file set.
 *
 * <p>The walk never follows symbolic links and never leaves the repository root, so a repository
 * containing a link to {@code /etc} cannot pull foreign files into the index.
 */
public class RepositoryScanner {

    private final FileFilter filter;

    public RepositoryScanner() {
        this(new FileFilter());
    }

    public RepositoryScanner(FileFilter filter) {
        this.filter = filter;
    }

    public Repository scan(Path rootIn) throws IOException {
        long started = System.nanoTime();
        Path root = rootIn.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("Repository root is not a directory: " + root);
        }
        List<ScannedFile> accepted = new ArrayList<>();
        List<IgnoredFile> ignored = new ArrayList<>();
        Counters counters = new Counters();
        ResourceLimits limits = filter.limits();

        Files.walkFileTree(root, java.util.Set.of(), limits.maxDirectoryDepth(), new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = dir.getFileName().toString();
                String rel = relative(root, dir);
                if (Files.isSymbolicLink(dir)) {
                    ignored.add(new IgnoredFile(rel, IgnoreReason.SYMLINK, "symlinked directory"));
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (filter.isIgnoredDirectory(name)) {
                    ignored.add(new IgnoredFile(rel, IgnoreReason.IGNORED_DIRECTORY, name));
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!SafePath.isInside(root, dir)) {
                    ignored.add(new IgnoredFile(rel, IgnoreReason.SYMLINK, "path escapes repository root"));
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String rel = relative(root, file);
                counters.seen++;
                if (counters.seen > limits.maxFiles() || counters.bytes > limits.maxTotalBytes()) {
                    counters.truncated = true;
                    ignored.add(new IgnoredFile(rel, IgnoreReason.LIMIT_EXCEEDED, "scan limit reached"));
                    return FileVisitResult.TERMINATE;
                }
                if (Files.isSymbolicLink(file)) {
                    ignored.add(new IgnoredFile(rel, IgnoreReason.SYMLINK, "symlink not followed"));
                    return FileVisitResult.CONTINUE;
                }
                if (!attrs.isRegularFile()) {
                    ignored.add(new IgnoredFile(rel, IgnoreReason.IGNORED_NAME, "not a regular file"));
                    return FileVisitResult.CONTINUE;
                }
                var rejection = filter.reject(root, file, rel);
                if (rejection.isPresent()) {
                    ignored.add(rejection.get());
                    return FileVisitResult.CONTINUE;
                }
                long size = attrs.size();
                if (size > limits.maxTotalBytes() - counters.bytes) {
                    counters.truncated = true;
                    ignored.add(new IgnoredFile(rel, IgnoreReason.LIMIT_EXCEEDED,
                            "total Java source byte limit reached"));
                    return FileVisitResult.TERMINATE;
                }
                counters.bytes += size;
                accepted.add(new ScannedFile(rel, file, size, attrs.lastModifiedTime().toMillis(), true));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                ignored.add(new IgnoredFile(relative(root, file), IgnoreReason.UNREADABLE,
                        exc.getClass().getSimpleName()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        accepted.sort(Comparator.comparing(ScannedFile::relativePath));
        ignored.sort(Comparator.comparing(IgnoredFile::relativePath));
        long millis = (System.nanoTime() - started) / 1_000_000;
        RepositoryMetadata metadata = new RepositoryMetadata(counters.seen, accepted.size(), ignored.size(),
                counters.bytes, millis, counters.truncated);
        return new Repository(root, accepted, ignored, metadata);
    }

    /** True when the directory looks like a Git repository (used by status output). */
    public static boolean isGitRepository(Path root) {
        return Files.exists(root.resolve(".git"), LinkOption.NOFOLLOW_LINKS);
    }

    private static String relative(Path root, Path p) {
        return root.relativize(p).toString().replace('\\', '/');
    }

    private static final class Counters {
        int seen;
        long bytes;
        boolean truncated;
    }
}
