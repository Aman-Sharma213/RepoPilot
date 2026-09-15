package com.repopilot.benchmark;

import java.util.List;

/** Precision, recall and F1 for one case, with the concrete mistakes listed. */
public record BenchmarkMetrics(String name, int truePositives, int falsePositives, int falseNegatives,
                               List<String> falsePositiveSymbols, List<String> falseNegativeSymbols,
                               long indexMillis, long queryMillis, long memoryBytes, int symbols, int edges,
                               IssueRankingMetrics issueRanking) {

    public double precision() {
        int predicted = truePositives + falsePositives;
        return predicted == 0 ? 1.0 : (double) truePositives / predicted;
    }

    public double recall() {
        int actual = truePositives + falseNegatives;
        return actual == 0 ? 1.0 : (double) truePositives / actual;
    }

    public double f1() {
        double p = precision();
        double r = recall();
        return p + r == 0 ? 0 : 2 * p * r / (p + r);
    }
}
