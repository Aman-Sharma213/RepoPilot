package com.repopilot.git;

import com.repopilot.symbol.SymbolId;

import java.util.List;

/** One revision-aware direct change. Null base/head symbols mean no such declaration exists. */
public record ChangedTarget(ChangedTargetKind kind, TargetMappingStatus status, String oldPath, String newPath,
                            SymbolId baseSymbol, SymbolId headSymbol, String language,
                            RevisionProvenance baseRevision, RevisionProvenance headRevision,
                            DiffEvidence evidence, List<SymbolId> candidates,
                            List<CrossLanguageObservation> observations, String reason) {
    public ChangedTarget {
        candidates = List.copyOf(candidates);
        observations = List.copyOf(observations);
    }
}
