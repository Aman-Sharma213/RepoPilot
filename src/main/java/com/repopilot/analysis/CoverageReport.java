package com.repopilot.analysis;

import com.repopilot.graph.CodeGraph;

import java.util.EnumMap;
import java.util.Map;

/** Honest coverage summary. Path strength and graph completeness are intentionally separate. */
public record CoverageReport(long resolved, Map<UnresolvedCategory, Long> unresolved,
                             double projectResolutionRate, Completeness completeness) {

    public enum Completeness { COMPLETE_ENOUGH, PARTIAL, LOW_COVERAGE }

    public CoverageReport {
        unresolved = Map.copyOf(unresolved);
    }

    public static CoverageReport from(CodeGraph graph) {
        EnumMap<UnresolvedCategory, Long> counts = new EnumMap<>(UnresolvedCategory.class);
        for (UnresolvedCategory category : UnresolvedCategory.values()) {
            counts.put(category, 0L);
        }
        graph.unresolvedReferences().forEach(reference -> counts.merge(
                UnresolvedClassifier.classify(reference), 1L, Long::sum));
        long localGaps = counts.get(UnresolvedCategory.PROJECT_LOCAL_UNRESOLVED);
        long dynamic = counts.get(UnresolvedCategory.DYNAMIC);
        long relevantTotal = graph.edgeCount() + localGaps + dynamic;
        double rate = relevantTotal == 0 ? 1.0 : (double) graph.edgeCount() / relevantTotal;
        Completeness status = localGaps == 0 && dynamic == 0
                ? Completeness.COMPLETE_ENOUGH
                : rate < 0.60 ? Completeness.LOW_COVERAGE : Completeness.PARTIAL;
        return new CoverageReport(graph.edgeCount(), counts, rate, status);
    }

    public long count(UnresolvedCategory category) {
        return unresolved.getOrDefault(category, 0L);
    }

    public long totalUnresolved() {
        return unresolved.values().stream().mapToLong(Long::longValue).sum();
    }

    public boolean mayMissProjectFiles() {
        return count(UnresolvedCategory.PROJECT_LOCAL_UNRESOLVED) > 0
                || count(UnresolvedCategory.DYNAMIC) > 0;
    }
}
