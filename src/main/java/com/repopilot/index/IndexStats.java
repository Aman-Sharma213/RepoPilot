package com.repopilot.index;

/** Summary of one indexing run. */
public record IndexStats(int files, int parsedFiles, int filesWithSyntaxErrors, int symbols, int nodes, int edges,
                         int unresolvedReferences, long millis, String digest, String mode) {

    public double resolutionRate() {
        int total = edges + unresolvedReferences;
        return total == 0 ? 1.0 : (double) edges / total;
    }
}
