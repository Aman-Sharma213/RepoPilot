package com.repopilot.impact;

import com.repopilot.graph.CodeGraph;
import com.repopilot.graph.EdgeType;
import com.repopilot.graph.GraphEdge;
import com.repopilot.graph.GraphNode;
import com.repopilot.parse.SourceLocation;
import com.repopilot.resolve.ResolutionLevel;
import com.repopilot.resolve.ResolutionStatus;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImpactExplainerTest {
    @Test
    void explanationContainsOrderedEvidenceAndAuthoritativePathConfidence() {
        SymbolId target = new SymbolId("a.Target#changed()");
        SymbolId service = new SymbolId("a.Service#work()");
        SymbolId caller = new SymbolId("a.Caller#go()");
        CodeGraph graph = graph(target, service, caller);
        graph.addEdge(edge(service, target, EdgeType.CALLS, 1, "target call"));
        graph.addEdge(edge(caller, service, EdgeType.CALLS, .9, "service call"));

        AffectedSymbol affected = find(new ImpactEngine(graph).analyse(List.of(target),
                ImpactEngine.Options.defaults()), caller);
        String explanation = new ImpactExplainer().explain(affected);

        assertTrue(explanation.contains("path confidence 90%"), explanation);
        assertTrue(explanation.contains("RESOLVED/L1_SAME_CLASS"), explanation);
        assertTrue(explanation.contains("evidence: service call"), explanation);
        assertTrue(explanation.indexOf("calls Service.work()") < explanation.indexOf("calls Target.changed()"));
        assertTrue(explanation.contains("changed target: Target.changed()"), explanation);
    }

    @Test
    void forwardOverridePathEndsAtTheRealChangedImplementation() {
        SymbolId implementation = new SymbolId("a.Impl#run()");
        SymbolId contract = new SymbolId("a.Api#run()");
        SymbolId caller = new SymbolId("a.Caller#go()");
        CodeGraph graph = graph(implementation, contract, caller);
        graph.addEdge(edge(implementation, contract, EdgeType.OVERRIDES, 1, "implements contract"));
        graph.addEdge(edge(caller, contract, EdgeType.CALLS, .9, "contract call"));

        AffectedSymbol affected = find(new ImpactEngine(graph).analyse(List.of(implementation),
                ImpactEngine.Options.defaults()), caller);
        String explanation = new ImpactExplainer().explain(affected);

        assertEquals(implementation, affected.target());
        assertTrue(explanation.contains("path confidence 72%"), explanation);
        assertTrue(explanation.contains("is overridden by Impl.run()"), explanation);
        assertTrue(explanation.contains("forward-dispatch penalty included in path confidence"), explanation);
        assertTrue(explanation.contains("changed target: Impl.run()"), explanation);
        assertFalse(explanation.contains("changed target: Api.run()"), explanation);
    }

    @Test
    void disconnectedOrWrongLengthEvidenceIsRejected() {
        SymbolId source = new SymbolId("a.Source#go()");
        SymbolId other = new SymbolId("a.Other#go()");
        SymbolId target = new SymbolId("a.Target#go()");
        GraphEdge disconnected = edge(other, target, EdgeType.CALLS, 1, "not connected");
        AffectedSymbol wrongLength = new AffectedSymbol(source, "Source.java", 2, List.of(disconnected), 1,
                ImpactKind.INDIRECT, "bad path");
        assertThrows(IllegalArgumentException.class, () -> new ImpactExplainer().explain(wrongLength));
        AffectedSymbol disconnectedPath = new AffectedSymbol(source, "Source.java", 1, List.of(disconnected), 1,
                ImpactKind.INDIRECT, "bad path");
        assertThrows(IllegalArgumentException.class, () -> new ImpactExplainer().explain(disconnectedPath));
    }

    private AffectedSymbol find(ImpactResult result, SymbolId symbol) {
        return result.affected().stream().filter(item -> item.symbol().equals(symbol)).findFirst().orElseThrow();
    }

    private CodeGraph graph(SymbolId... ids) {
        CodeGraph graph = new CodeGraph();
        for (SymbolId id : ids) {
            graph.addNode(new GraphNode(id, SymbolKind.METHOD, id.ownerFqn() + ".java", 1, 1, false));
        }
        return graph;
    }

    private GraphEdge edge(SymbolId source, SymbolId target, EdgeType type, double confidence, String evidence) {
        return new GraphEdge(source, target, type, SourceLocation.unknown(source.ownerFqn() + ".java"), confidence,
                ResolutionStatus.RESOLVED, ResolutionLevel.L1_SAME_CLASS, evidence);
    }
}
