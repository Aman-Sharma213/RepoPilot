package com.repopilot.graph;

import com.repopilot.resolve.Reference;
import com.repopilot.resolve.Resolution;
import com.repopilot.resolve.ResolutionStatus;
import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;

import java.util.List;

/**
 * Turns resolved references into graph edges.
 *
 * <p>Resolved references become a single edge. Ambiguous references become one edge per candidate,
 * each flagged {@link ResolutionStatus#AMBIGUOUS} with the shared confidence, so downstream impact
 * analysis can classify them as uncertain instead of silently trusting one of them.
 */
public class GraphBuilder {

    public CodeGraph build(SymbolIndex index, List<Reference> references) {
        CodeGraph graph = new CodeGraph();
        for (Symbol symbol : index.all()) {
            graph.addNode(GraphNode.of(symbol));
        }
        for (Reference reference : references) {
            Resolution resolution = reference.resolution();
            switch (resolution.status()) {
                case RESOLVED -> addEdges(graph, reference, resolution, ResolutionStatus.RESOLVED);
                case AMBIGUOUS -> addEdges(graph, reference, resolution, ResolutionStatus.AMBIGUOUS);
                case UNRESOLVED, DYNAMIC -> graph.addUnresolved(new UnresolvedReference(reference.source(),
                        reference.type(), resolution.status(), reference.rawText(), resolution.reason(),
                        reference.location()));
            }
        }
        graph.sortDeterministically();
        return graph;
    }

    private void addEdges(CodeGraph graph, Reference reference, Resolution resolution, ResolutionStatus status) {
        for (SymbolId target : resolution.candidates()) {
            GraphEdge edge = new GraphEdge(reference.source(), target, reference.type(), reference.location(),
                    resolution.confidence(), status, resolution.level(), reference.rawText());
            if (!graph.addEdge(edge)) {
                graph.addUnresolved(new UnresolvedReference(reference.source(), reference.type(),
                        ResolutionStatus.UNRESOLVED, reference.rawText(),
                        "target " + target + " is not an indexed symbol", reference.location()));
            }
        }
    }
}
