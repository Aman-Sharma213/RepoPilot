package com.repopilot.git;

import java.util.ArrayList;
import java.util.List;

/** Parses unified diff text without discarding hunk context or old/new coordinates. */
public class UnifiedDiffParser {

    public List<FileDiff> parse(String diff) {
        if (diff == null || diff.isBlank()) {
            return List.of();
        }
        List<FileDiff> files = new ArrayList<>();
        Builder file = null;
        HunkBuilder hunk = null;
        for (String line : diff.split("\\R", -1)) {
            if (line.startsWith("diff --git ")) {
                if (file != null) {
                    file.finishHunk(hunk);
                    files.add(file.build());
                }
                file = new Builder();
                hunk = null;
                List<String> paths = parseHeaderPaths(line.substring(11).trim());
                if (paths.size() == 2) {
                    file.oldPath = stripPrefix(paths.get(0));
                    file.newPath = stripPrefix(paths.get(1));
                }
                continue;
            }
            if (file == null) {
                continue;
            }
            if (line.startsWith("@@")) {
                file.finishHunk(hunk);
                hunk = HunkBuilder.fromHeader(line);
            } else if (hunk != null && line.startsWith("\\ No newline at end of file")) {
                hunk.lines.add(new DiffHunk.Line(DiffHunk.LineKind.NO_NEWLINE, line));
            } else if (hunk != null && !line.isEmpty()) {
                char prefix = line.charAt(0);
                DiffHunk.LineKind kind = switch (prefix) {
                    case '+' -> DiffHunk.LineKind.ADDED;
                    case '-' -> DiffHunk.LineKind.REMOVED;
                    case ' ' -> DiffHunk.LineKind.CONTEXT;
                    default -> null;
                };
                if (kind != null) {
                    hunk.lines.add(new DiffHunk.Line(kind, line.substring(1)));
                }
            } else if (hunk != null && line.isEmpty()) {
                // An empty context line is encoded by a single leading space. A physically empty
                // line here is normally the terminal split artifact and is deliberately ignored.
            } else if (line.startsWith("--- ")) {
                String p = line.substring(4).trim();
                file.oldPath = p.equals("/dev/null") ? null : stripPrefix(p);
            } else if (line.startsWith("+++ ")) {
                String p = line.substring(4).trim();
                file.newPath = p.equals("/dev/null") ? null : stripPrefix(p);
            } else if (line.startsWith("deleted file mode")) {
                file.deleted = true;
            } else if (line.startsWith("new file mode")) {
                file.added = true;
            } else if (line.startsWith("Binary files") || line.startsWith("GIT binary patch")) {
                file.binary = true;
            }
        }
        if (file != null) {
            file.finishHunk(hunk);
            files.add(file.build());
        }
        return List.copyOf(files);
    }

    private static String stripPrefix(String path) {
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        path = path.replace("\\\"", "\"").replace("\\\\", "\\");
        return path.startsWith("a/") || path.startsWith("b/") ? path.substring(2) : path;
    }

    /** Splits the two git header paths without treating spaces inside quoted paths as separators. */
    private static List<String> parseHeaderPaths(String text) {
        List<String> paths = new ArrayList<>(2);
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                current.append('\\').append(c);
                escaped = false;
            } else if (c == '\\' && quoted) {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
                current.append(c);
            } else if (Character.isWhitespace(c) && !quoted) {
                if (!current.isEmpty()) {
                    paths.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (escaped) current.append('\\');
        if (quoted) throw new IllegalArgumentException("Unterminated quoted path in diff header: " + text);
        if (!current.isEmpty()) paths.add(current.toString());
        if (paths.size() != 2) throw new IllegalArgumentException("Malformed git diff header: " + text);
        return List.copyOf(paths);
    }

    private static final class HunkBuilder {
        final int oldStart;
        final int oldCount;
        final int newStart;
        final int newCount;
        final String header;
        final List<DiffHunk.Line> lines = new ArrayList<>();

        private HunkBuilder(int oldStart, int oldCount, int newStart, int newCount, String header) {
            this.oldStart = oldStart;
            this.oldCount = oldCount;
            this.newStart = newStart;
            this.newCount = newCount;
            this.header = header;
        }

        static HunkBuilder fromHeader(String header) {
            int close = header.indexOf("@@", 2);
            if (close < 0) {
                throw new IllegalArgumentException("Malformed unified-diff hunk header: " + header);
            }
            String[] coordinates = header.substring(2, close).trim().split("\\s+");
            if (coordinates.length < 2) {
                throw new IllegalArgumentException("Malformed unified-diff hunk header: " + header);
            }
            int[] oldSide = coordinate(coordinates[0], '-');
            int[] newSide = coordinate(coordinates[1], '+');
            return new HunkBuilder(oldSide[0], oldSide[1], newSide[0], newSide[1], header);
        }

        private static int[] coordinate(String value, char prefix) {
            if (value.isEmpty() || value.charAt(0) != prefix) {
                throw new IllegalArgumentException("Malformed unified-diff coordinate: " + value);
            }
            String[] parts = value.substring(1).split(",", 2);
            try {
                return new int[]{Integer.parseInt(parts[0]), parts.length == 1 ? 1 : Integer.parseInt(parts[1])};
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Malformed unified-diff coordinate: " + value, e);
            }
        }

        DiffHunk build() {
            return new DiffHunk(oldStart, oldCount, newStart, newCount, header, lines);
        }
    }

    private static final class Builder {
        String oldPath;
        String newPath;
        boolean binary;
        boolean deleted;
        boolean added;
        final List<DiffHunk> hunks = new ArrayList<>();

        void finishHunk(HunkBuilder hunk) {
            if (hunk != null) hunks.add(hunk.build());
        }

        FileDiff build() {
            DiffChangeType type = binary ? DiffChangeType.BINARY
                    : deleted || newPath == null ? DiffChangeType.DELETED
                    : added || oldPath == null ? DiffChangeType.ADDED
                    : !oldPath.equals(newPath) ? DiffChangeType.RENAMED : DiffChangeType.MODIFIED;
            List<FileDiff.LineRange> changed = hunks.stream().flatMap(h -> h.addedRanges().stream()).toList();
            List<FileDiff.LineRange> removed = hunks.stream().flatMap(h -> h.removedRanges().stream()).toList();
            return new FileDiff(newPath, oldPath, type, changed, removed, binary, hunks);
        }
    }
}
