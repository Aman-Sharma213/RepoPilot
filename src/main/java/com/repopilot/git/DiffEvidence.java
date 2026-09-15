package com.repopilot.git;

import java.util.List;

/** Direct evidence connecting a changed target to one exact diff hunk. */
public record DiffEvidence(String oldPath, String newPath, DiffChangeType changeType, String hunkHeader,
                           FileDiff.LineRange oldRange, FileDiff.LineRange newRange,
                           List<String> removedLines, List<String> addedLines,
                           String baseContentHash, String headContentHash) {
    public DiffEvidence {
        removedLines = List.copyOf(removedLines);
        addedLines = List.copyOf(addedLines);
    }
}
