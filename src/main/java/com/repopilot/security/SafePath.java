package com.repopilot.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Repository content is untrusted. Every path that comes from a scan, a diff, an index file or an
 * AI answer is validated here before it is opened.
 */
public final class SafePath {

    private SafePath() {
    }

    /** True when {@code candidate} is inside {@code root} after normalisation (no {@code ..} escape). */
    public static boolean isInside(Path root, Path candidate) {
        Path r = root.toAbsolutePath().normalize();
        Path c = candidate.toAbsolutePath().normalize();
        return c.startsWith(r);
    }

    /**
     * Resolves a repository-relative path, rejecting absolute paths, {@code ..} traversal and
     * anything that leaves the repository root.
     */
    public static Path resolveInside(Path root, String relative) {
        if (relative == null || relative.isBlank()) {
            throw new SecurityException("Empty path");
        }
        if (relative.indexOf('\u0000') >= 0) {
            throw new SecurityException("Illegal NUL byte in path: " + relative);
        }
        Path rel = Path.of(relative);
        if (rel.isAbsolute()) {
            throw new SecurityException("Absolute paths are not accepted: " + relative);
        }
        Path resolved = root.resolve(rel).normalize();
        if (!isInside(root, resolved)) {
            throw new SecurityException("Path escapes repository root: " + relative);
        }
        return resolved;
    }

    /** True when the real path stays inside the root, i.e. the file is not a symlink escape. */
    public static boolean isRealPathInside(Path root, Path candidate) {
        try {
            Path real = candidate.toRealPath();
            return isInside(root.toRealPath(), real);
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isSymbolicLink(Path p) {
        return Files.isSymbolicLink(p) || !Files.exists(p, LinkOption.NOFOLLOW_LINKS) && Files.exists(p);
    }
}
