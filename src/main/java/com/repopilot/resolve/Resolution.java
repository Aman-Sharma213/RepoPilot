package com.repopilot.resolve;

import com.repopilot.symbol.SymbolId;

import java.util.List;
import java.util.Optional;

/**
 * The result of resolving one reference.
 *
 * @param level      resolution level that produced the answer (see {@link ResolutionLevel})
 * @param confidence 0..1 - how certain the deterministic analysis is
 * @param reason     human readable evidence, shown in explainable output
 */
public record Resolution(ResolutionStatus status, List<SymbolId> candidates, double confidence,
                         ResolutionLevel level, String reason) {

    public Resolution {
        candidates = List.copyOf(candidates);
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence out of range: " + confidence);
        }
    }

    public static Resolution resolved(SymbolId target, double confidence, ResolutionLevel level, String reason) {
        return new Resolution(ResolutionStatus.RESOLVED, List.of(target), confidence, level, reason);
    }

    public static Resolution ambiguous(List<SymbolId> candidates, ResolutionLevel level, String reason) {
        double confidence = candidates.isEmpty() ? 0 : 1.0 / candidates.size();
        return new Resolution(ResolutionStatus.AMBIGUOUS, candidates, Math.min(confidence, 0.5), level, reason);
    }

    public static Resolution unresolved(String reason) {
        return new Resolution(ResolutionStatus.UNRESOLVED, List.of(), 0, ResolutionLevel.NONE, reason);
    }

    public static Resolution dynamic(String reason) {
        return new Resolution(ResolutionStatus.DYNAMIC, List.of(), 0, ResolutionLevel.NONE, reason);
    }

    public boolean isResolved() {
        return status == ResolutionStatus.RESOLVED;
    }

    public Optional<SymbolId> single() {
        return status == ResolutionStatus.RESOLVED && candidates.size() == 1
                ? Optional.of(candidates.get(0)) : Optional.empty();
    }
}
