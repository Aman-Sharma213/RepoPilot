package com.repopilot.git;

import com.repopilot.index.RepoIndex;

import java.util.List;

public record PrAnalysisResult(List<ChangedTarget> targets, RepoIndex headIndex, List<String> diagnostics) {
    public PrAnalysisResult {
        targets = List.copyOf(targets);
        diagnostics = List.copyOf(diagnostics);
    }
}
