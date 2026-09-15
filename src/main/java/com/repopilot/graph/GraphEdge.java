package com.repopilot.graph;

import com.repopilot.parse.SourceLocation;
import com.repopilot.resolve.ResolutionLevel;
import com.repopilot.resolve.ResolutionStatus;
import com.repopilot.symbol.SymbolId;

/**
 * A relationship between two symbols. Every edge keeps its evidence: where it was written, how it
 * was resolved and how confident the resolver is. Nothing in the graph is unexplainable.
 */
public record GraphEdge(SymbolId source, SymbolId target, EdgeType type, SourceLocation location,
                        double confidence, ResolutionStatus status, ResolutionLevel level, String evidence) {

    public GraphEdge {
        java.util.Objects.requireNonNull(source, "source");
        java.util.Objects.requireNonNull(target, "target");
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(location, "location");
        java.util.Objects.requireNonNull(status, "status");
        java.util.Objects.requireNonNull(level, "level");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("edge confidence must be a finite number between 0 and 1");
        }
        evidence = evidence == null ? "" : evidence;
    }

    public String describe() {
        return source.display() + " --" + type + "--> " + target.display()
                + " (" + location + ", " + Math.round(confidence * 100) + "%, " + status + ")";
    }
}
