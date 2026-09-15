package com.repopilot.benchmark;

import java.nio.file.Path;
import java.util.List;

/**
 * A benchmark case: a small repository plus the ground truth of what a change should affect.
 *
 * @param targets          symbol ids that are "changed"
 * @param expectedAffected symbol ids that must appear in the impact set
 */
public record BenchmarkCase(String name, Path repository, List<String> targets, List<String> expectedAffected,
                            int maxRadius, String description, List<String> expectedTopFiles, int topK,
                            String sourceUrl) {

    public record Definition(String name, List<String> targets, List<String> expectedAffected, Integer maxRadius,
                             String description, List<String> expectedTopFiles, Integer topK, String sourceUrl) {
    }
}
