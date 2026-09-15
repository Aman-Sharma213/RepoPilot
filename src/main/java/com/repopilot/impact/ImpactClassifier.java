package com.repopilot.impact;

import com.repopilot.graph.CodeGraph;
import com.repopilot.graph.GraphEdge;
import com.repopilot.resolve.ResolutionStatus;
import com.repopilot.symbol.SymbolId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Decides how trustworthy an impact path is. Ambiguous edges, dynamic dispatch and low confidence
 * produce {@link ImpactKind#UNCERTAIN} rather than being reported as facts.
 */
public class ImpactClassifier {

    public static final double CERTAINTY_THRESHOLD = 0.75;

    private final CodeGraph graph;

    public ImpactClassifier(CodeGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    public ImpactKind classify(SymbolId symbol, int radius, List<GraphEdge> path, double confidence,
                               boolean separateTests) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(path, "path");
        if (radius < 0) throw new IllegalArgumentException("impact radius must be zero or greater");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("impact confidence must be a finite number between 0 and 1");
        }
        if (radius == 0) {
            return ImpactKind.DIRECT;
        }
        boolean uncertainEdge = path.stream().anyMatch(this::isUncertain);
        if (uncertainEdge || confidence < CERTAINTY_THRESHOLD) {
            return ImpactKind.UNCERTAIN;
        }
        if (separateTests && graph.node(symbol).map(n -> n.testCode()).orElse(false)) {
            return ImpactKind.TEST_IMPACT;
        }
        return ImpactKind.INDIRECT;
    }

    public String reason(List<GraphEdge> path, ImpactKind kind, double pathConfidence) {
        if (!Double.isFinite(pathConfidence) || pathConfidence < 0 || pathConfidence > 1) {
            throw new IllegalArgumentException("impact confidence must be a finite number between 0 and 1");
        }
        if (path.isEmpty()) {
            return "changed symbol";
        }
        GraphEdge nearest = path.get(0);
        String base = nearest.source().display() + " " + verb(nearest) + " " + nearest.target().display()
                + " at " + nearest.location();
        return switch (kind) {
            case UNCERTAIN -> base + uncertaintyReason(path, pathConfidence);
            case TEST_IMPACT -> base + " (test code)";
            default -> base;
        };
    }

    private boolean isUncertain(GraphEdge edge) {
        return edge.status() != ResolutionStatus.RESOLVED
                || edge.type() == com.repopilot.graph.EdgeType.DYNAMIC;
    }

    private String uncertaintyReason(List<GraphEdge> path, double pathConfidence) {
        GraphEdge cause = path.stream().filter(this::isUncertain).findFirst()
                .orElseGet(() -> path.stream().min(Comparator.comparingDouble(GraphEdge::confidence))
                        .orElse(path.get(0)));
        String causeKind = cause.type() == com.repopilot.graph.EdgeType.DYNAMIC
                || cause.status() == ResolutionStatus.DYNAMIC ? "dynamic relationship"
                : cause.status() == ResolutionStatus.AMBIGUOUS ? "ambiguous relationship"
                : "low-confidence relationship";
        return " (uncertain: " + causeKind + " " + cause.source().display() + " -> "
                + cause.target().display() + "; " + cause.evidence() + "; path confidence "
                + Math.round(pathConfidence * 100) + "%)";
    }

    public static String verb(GraphEdge edge) {
        return switch (edge.type()) {
            case CALLS -> "calls";
            case EXTENDS -> "extends";
            case IMPLEMENTS -> "implements";
            case OVERRIDES -> "overrides";
            case FIELD_TYPE -> "has field of type";
            case PARAMETER_TYPE -> "takes parameter of type";
            case RETURN_TYPE -> "returns";
            case INSTANTIATES -> "instantiates";
            case IMPORTS -> "imports";
            case ANNOTATED_WITH -> "is annotated with";
            case SPRING_DI -> "is injected with";
            case EVENT_LISTENER -> "listens to";
            case BEAN_CREATION -> "creates bean";
            case DYNAMIC -> "dynamically references";
        };
    }
}
