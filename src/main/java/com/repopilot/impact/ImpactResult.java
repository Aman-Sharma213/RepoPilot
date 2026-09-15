package com.repopilot.impact;

import com.repopilot.symbol.SymbolId;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public record ImpactResult(List<SymbolId> targets, List<AffectedSymbol> affected, int maxRadius,
                           List<String> notes, List<UnresolvedImpact> unresolvedImpacts) {

    public ImpactResult {
        targets = List.copyOf(targets);
        affected = List.copyOf(affected);
        notes = List.copyOf(notes);
        unresolvedImpacts = List.copyOf(unresolvedImpacts);
    }

    public List<AffectedSymbol> ofKind(ImpactKind kind) {
        return affected.stream().filter(a -> a.kind() == kind).toList();
    }

    public Map<Integer, List<AffectedSymbol>> byRadius() {
        return affected.stream().filter(a -> a.radius() > 0)
                .collect(Collectors.groupingBy(AffectedSymbol::radius, TreeMap::new, Collectors.toList()));
    }

    public List<String> files() {
        return affected.stream().map(AffectedSymbol::file).distinct().sorted().toList();
    }

    /** Mean confidence over all impacted symbols; 1.0 when only the direct targets are reported. */
    public double overallConfidence() {
        return affected.isEmpty() ? 1.0
                : affected.stream().mapToDouble(AffectedSymbol::confidence).average().orElse(1.0);
    }

    public int symbolCount() {
        return affected.size();
    }
}
