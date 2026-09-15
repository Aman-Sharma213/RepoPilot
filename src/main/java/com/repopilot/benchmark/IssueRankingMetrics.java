package com.repopilot.benchmark;

import java.util.List;

/** Top-k issue-to-file retrieval quality for cases derived from accepted contributions. */
public record IssueRankingMetrics(boolean enabled, int topK, int hits, int expected,
                                  List<String> predictedFiles, List<String> missedFiles) {
    public static IssueRankingMetrics disabled() {
        return new IssueRankingMetrics(false, 0, 0, 0, List.of(), List.of());
    }

    public boolean passed() {
        return !enabled || missedFiles.isEmpty();
    }

    public double recallAtK() {
        return !enabled || expected == 0 ? 1.0 : (double) hits / expected;
    }
}
