package com.repopilot.impact;

import com.repopilot.graph.EdgeType;
import com.repopilot.graph.UnresolvedReference;
import com.repopilot.parse.SourceLocation;
import com.repopilot.resolve.ResolutionStatus;
import com.repopilot.symbol.SymbolId;

import java.util.Objects;

/** Evidence for a relationship whose target cannot be identified; this is not a graph edge. */
public record UnresolvedImpact(SymbolId source, EdgeType relationshipType, ResolutionStatus status,
                               String rawText, String reason, SourceLocation location) {
    public UnresolvedImpact {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(relationshipType, "relationshipType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(location, "location");
        if (status != ResolutionStatus.UNRESOLVED && status != ResolutionStatus.DYNAMIC) {
            throw new IllegalArgumentException("unresolved impact requires UNRESOLVED or DYNAMIC status");
        }
        rawText = rawText == null ? "" : rawText;
        reason = reason == null ? "" : reason;
    }

    public static UnresolvedImpact from(UnresolvedReference reference) {
        return new UnresolvedImpact(reference.source(), reference.type(), reference.status(), reference.rawText(),
                reference.reason(), reference.location());
    }
}
