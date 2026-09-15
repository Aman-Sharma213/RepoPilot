package com.repopilot.git;

import java.util.List;

/**
 * One file in a diff.
 *
 * @param newPath      path after the change (null for deletions)
 * @param oldPath      path before the change (null for additions)
 * @param changedLines line ranges in the new file that were added or modified
 * @param removedLines line ranges in the old file that were removed or modified
 */
public record FileDiff(String newPath, String oldPath, DiffChangeType changeType, List<LineRange> changedLines,
                       List<LineRange> removedLines, boolean binary, List<DiffHunk> hunks) {

    public FileDiff {
        changedLines = List.copyOf(changedLines);
        removedLines = List.copyOf(removedLines);
        hunks = List.copyOf(hunks);
    }

    /** Backwards-compatible constructor for callers that do not yet retain complete hunks. */
    public FileDiff(String newPath, String oldPath, DiffChangeType changeType, List<LineRange> changedLines,
                    List<LineRange> removedLines, boolean binary) {
        this(newPath, oldPath, changeType, changedLines, removedLines, binary, List.of());
    }

    public record LineRange(int start, int end) {
        public boolean intersects(int from, int to) {
            return from <= end && to >= start;
        }
    }

    public String path() {
        return newPath != null ? newPath : oldPath;
    }

    public boolean isJava() {
        String path = path();
        return path != null && path.endsWith(".java");
    }
}
