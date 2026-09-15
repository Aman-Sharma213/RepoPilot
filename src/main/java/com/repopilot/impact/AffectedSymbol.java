package com.repopilot.impact;

import com.repopilot.graph.GraphEdge;
import com.repopilot.symbol.SymbolId;

import java.util.List;

/**
 * One impacted symbol with the complete evidence trail.
 *
 * @param path edges from the impacted symbol back to the changed target, nearest first
 */
public record AffectedSymbol(SymbolId symbol, String file, int radius, List<GraphEdge> path, double confidence,
                             ImpactKind kind, String reason) {

    public AffectedSymbol {
        path = List.copyOf(path);
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("impact confidence must be a finite number between 0 and 1");
        }
    }

    public SymbolId target() {
        SymbolId cursor = symbol;
        for (GraphEdge edge : path) {
            if (edge.source().equals(cursor)) {
                cursor = edge.target();
            } else if (edge.type() == com.repopilot.graph.EdgeType.OVERRIDES && edge.target().equals(cursor)) {
                cursor = edge.source();
            } else {
                throw new IllegalStateException("disconnected evidence path at " + cursor.value());
            }
        }
        return cursor;
    }
}
