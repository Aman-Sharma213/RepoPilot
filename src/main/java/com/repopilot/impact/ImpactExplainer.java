package com.repopilot.impact;

import com.repopilot.graph.GraphEdge;
import com.repopilot.graph.EdgeType;
import com.repopilot.symbol.SymbolId;

import java.util.List;
import java.util.Objects;

/**
 * Renders the evidence trail of an impacted symbol.
 *
 * <pre>
 * INDIRECTLY AFFECTED - Radius 2
 *
 * AuthController.java
 *   AuthController.login()
 *     calls LoginService.login()   [AuthController.java:24]
 *       calls AuthService.validate()   [LoginService.java:31]
 *         changed target: AuthTokenValidator.validate(Token)
 * </pre>
 */
public class ImpactExplainer {

    public String explain(AffectedSymbol affected) {
        Objects.requireNonNull(affected, "affected");
        StringBuilder sb = new StringBuilder();
        sb.append(affected.file()).append('\n');
        sb.append("  ").append(affected.symbol().display())
                .append("   [").append(affected.kind()).append(", radius ").append(affected.radius())
                .append(", path confidence ").append(Math.round(affected.confidence() * 100)).append("%]")
                .append('\n');
        List<GraphEdge> path = affected.path();
        if (affected.radius() != path.size()) {
            throw new IllegalArgumentException("evidence path length " + path.size()
                    + " does not match radius " + affected.radius());
        }
        String indent = "    ";
        SymbolId cursor = affected.symbol();
        for (GraphEdge edge : path) {
            boolean forwardOverride = edge.type() == EdgeType.OVERRIDES && edge.target().equals(cursor);
            SymbolId next;
            String relationship;
            if (edge.source().equals(cursor)) {
                next = edge.target();
                relationship = ImpactClassifier.verb(edge);
            } else if (forwardOverride) {
                next = edge.source();
                relationship = "is overridden by";
            } else {
                throw new IllegalArgumentException("disconnected evidence path at " + cursor.value()
                        + ": " + edge.describe());
            }
            sb.append(indent).append("└── ").append(relationship).append(' ')
                    .append(next.display())
                    .append("   [").append(edge.location()).append(", edge confidence ")
                    .append(Math.round(edge.confidence() * 100)).append("%, ")
                    .append(edge.status()).append('/').append(edge.level())
                    .append(forwardOverride ? ", forward-dispatch penalty included in path confidence" : "")
                    .append(", evidence: ").append(singleLine(edge.evidence()))
                    .append(']').append('\n');
            cursor = next;
            indent += "     ";
        }
        sb.append(indent).append("└── changed target: ").append(cursor.display()).append('\n');
        return sb.toString();
    }

    /** One-line form used in compact listings. */
    public String summarise(AffectedSymbol affected) {
        return affected.symbol().display() + "  (" + affected.file() + ", radius " + affected.radius()
                + ", " + Math.round(affected.confidence() * 100) + "%)";
    }

    private String singleLine(String value) {
        return value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }
}
