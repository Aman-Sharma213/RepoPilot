package com.repopilot.impact;

import com.repopilot.graph.CodeGraph;
import com.repopilot.graph.EdgeType;
import com.repopilot.graph.GraphEdge;
import com.repopilot.graph.GraphNode;
import com.repopilot.index.TestRepo;
import com.repopilot.parse.SourceLocation;
import com.repopilot.resolve.Resolution;
import com.repopilot.resolve.ResolutionLevel;
import com.repopilot.resolve.ResolutionStatus;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Phase13DefectRegressionTest {
    @Test
    void explanationUsesTheComputedConfidenceIncludingOverridePenalty() {
        SymbolId impl = new SymbolId("a.Impl#run()");
        SymbolId api = new SymbolId("a.Api#run()");
        SymbolId caller = new SymbolId("a.Caller#go()");
        CodeGraph graph = graph(impl, api, caller);
        graph.addEdge(edge(impl, api, EdgeType.OVERRIDES, 1, ResolutionStatus.RESOLVED, "override"));
        graph.addEdge(edge(caller, api, EdgeType.CALLS, .9, ResolutionStatus.RESOLVED, "call"));

        AffectedSymbol affected = new ImpactEngine(graph).analyse(List.of(impl), ImpactEngine.Options.defaults())
                .affected().stream().filter(item -> item.symbol().equals(caller)).findFirst().orElseThrow();
        assertEquals(.72, affected.confidence());
        assertEquals(ImpactKind.UNCERTAIN, affected.kind());
        assertTrue(affected.reason().contains("path confidence 72%"), affected.reason());
        assertFalse(affected.reason().contains("path confidence 90%"), affected.reason());
    }

    @Test
    void actualIndexingExposesDynamicAndUnresolvedEvidenceWithoutTargets() throws Exception {
        var index = TestRepo.of().file("src/a/M.java", """
                package a;
                import java.util.List;
                import java.util.function.Supplier;
                class M {
                  void dynamicCall() { ((Supplier<String>) () -> "x").get(); }
                  void unresolvedCall(List<String> list) { list.size(); }
                }
                """).index();
        SymbolId dynamic = new SymbolId("a.M#dynamicCall()");
        SymbolId unresolved = new SymbolId("a.M#unresolvedCall(List)");
        ImpactResult result = new ImpactEngine(index.graph()).analyse(List.of(dynamic, unresolved),
                ImpactEngine.Options.defaults());

        assertTrue(result.unresolvedImpacts().stream().anyMatch(f -> f.source().equals(dynamic)
                && f.status() == ResolutionStatus.DYNAMIC));
        assertTrue(result.unresolvedImpacts().stream().anyMatch(f -> f.source().equals(unresolved)
                && f.status() == ResolutionStatus.UNRESOLVED));
        assertTrue(result.unresolvedImpacts().stream().allMatch(f -> f.status() == ResolutionStatus.DYNAMIC
                || f.status() == ResolutionStatus.UNRESOLVED));
        assertTrue(index.graph().edges().stream().noneMatch(e -> e.status() == ResolutionStatus.DYNAMIC
                || e.status() == ResolutionStatus.UNRESOLVED));
    }

    @Test
    void nonFiniteConfidenceIsRejectedBeforeImpactArithmetic() {
        for (double invalid : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.1, 1.1)) {
            assertThrows(IllegalArgumentException.class,
                    () -> Resolution.resolved(new SymbolId("a.T"), invalid, ResolutionLevel.NONE, "bad"));
            assertThrows(IllegalArgumentException.class,
                    () -> edge(new SymbolId("a.S"), new SymbolId("a.T"), EdgeType.CALLS, invalid,
                            ResolutionStatus.RESOLVED, "bad"));
            assertThrows(IllegalArgumentException.class,
                    () -> new AffectedSymbol(new SymbolId("a.S"), "S.java", 1, List.of(), invalid,
                            ImpactKind.UNCERTAIN, "bad"));
        }
    }

    private CodeGraph graph(SymbolId... ids) {
        CodeGraph graph = new CodeGraph();
        for (SymbolId id : ids) graph.addNode(new GraphNode(id, SymbolKind.METHOD, id.ownerFqn()+".java", 1, 1, false));
        return graph;
    }

    private GraphEdge edge(SymbolId source, SymbolId target, EdgeType type, double confidence,
                           ResolutionStatus status, String evidence) {
        return new GraphEdge(source, target, type, SourceLocation.unknown("X.java"), confidence, status,
                ResolutionLevel.L1_SAME_CLASS, evidence);
    }
}
