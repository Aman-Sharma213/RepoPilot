package com.repopilot.git;

import java.util.ArrayList;
import java.util.List;

/** A complete unified-diff hunk, including context and both revision coordinates. */
public record DiffHunk(int oldStart, int oldCount, int newStart, int newCount, String header,
                       List<Line> lines) {

    public enum LineKind { CONTEXT, ADDED, REMOVED, NO_NEWLINE }

    public record Line(LineKind kind, String text) {
    }

    public DiffHunk {
        if (oldStart < 0 || newStart < 0 || oldCount < 0 || newCount < 0) {
            throw new IllegalArgumentException("negative unified-diff coordinate");
        }
        header = header == null ? "" : header;
        lines = List.copyOf(lines);
    }

    public List<FileDiff.LineRange> addedRanges() {
        return ranges(LineKind.ADDED, newStart, true);
    }

    public List<FileDiff.LineRange> removedRanges() {
        return ranges(LineKind.REMOVED, oldStart, false);
    }

    public List<String> addedLines() {
        return lines.stream().filter(l -> l.kind() == LineKind.ADDED).map(Line::text).toList();
    }

    public List<String> removedLines() {
        return lines.stream().filter(l -> l.kind() == LineKind.REMOVED).map(Line::text).toList();
    }

    /** Validates that context/removal/addition lines match the exact old/new snapshots. */
    public boolean matches(String oldSource, String newSource) {
        return matchesSide(oldSource, oldStart, false) && matchesSide(newSource, newStart, true);
    }

    private boolean matchesSide(String source, int start, boolean newSide) {
        if (source == null) {
            return newSide ? newCount == 0 : oldCount == 0;
        }
        List<String> sourceLines = source.lines().toList();
        int cursor = start == 0 ? 0 : start - 1;
        for (Line line : lines) {
            boolean consumes = line.kind() == LineKind.CONTEXT
                    || (newSide && line.kind() == LineKind.ADDED)
                    || (!newSide && line.kind() == LineKind.REMOVED);
            if (!consumes) {
                continue;
            }
            if (cursor < 0 || cursor >= sourceLines.size() || !sourceLines.get(cursor).equals(line.text())) {
                return false;
            }
            cursor++;
        }
        return true;
    }

    private List<FileDiff.LineRange> ranges(LineKind wanted, int start, boolean newSide) {
        List<FileDiff.LineRange> result = new ArrayList<>();
        int lineNumber = start;
        Integer rangeStart = null;
        for (Line line : lines) {
            if (line.kind() == wanted) {
                if (rangeStart == null) {
                    rangeStart = lineNumber;
                }
            } else if (rangeStart != null) {
                result.add(new FileDiff.LineRange(rangeStart, lineNumber - 1));
                rangeStart = null;
            }
            if (line.kind() == LineKind.CONTEXT
                    || (newSide && line.kind() == LineKind.ADDED)
                    || (!newSide && line.kind() == LineKind.REMOVED)) {
                lineNumber++;
            }
        }
        if (rangeStart != null) {
            result.add(new FileDiff.LineRange(rangeStart, lineNumber - 1));
        }
        return List.copyOf(result);
    }
}
