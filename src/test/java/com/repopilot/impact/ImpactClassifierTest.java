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

class ImpactClassifierTest {
    private final SymbolId target = new SymbolId("a.Target#changed()");
    private final SymbolId production = new SymbolId("a.Service#work()");
    private final SymbolId test = new SymbolId("a.ServiceTest#works()");
    private final CodeGraph graph = graph();
    private final ImpactClassifier classifier = new ImpactClassifier(graph);

    @Test
    void classifiesDirectIndirectAndTestImpact() {
        assertEquals(ImpactKind.DIRECT, classifier.classify(target, 0, List.of(), 1, true));
        assertEquals(ImpactKind.INDIRECT,
                classifier.classify(production, 1, List.of(edge(production, ResolutionStatus.RESOLVED,
                        EdgeType.CALLS, .9, "resolved")), .9, true));
        assertEquals(ImpactKind.TEST_IMPACT,
                classifier.classify(test, 1, List.of(edge(test, ResolutionStatus.RESOLVED,
                        EdgeType.CALLS, .9, "resolved test")), .9, true));
        assertEquals(ImpactKind.INDIRECT,
                classifier.classify(test, 1, List.of(edge(test, ResolutionStatus.RESOLVED,
                        EdgeType.CALLS, .9, "resolved test")), .9, false));
    }

    @Test
    void uncertaintyTakesPrecedenceOverTestGrouping() {
        GraphEdge ambiguous = edge(test, ResolutionStatus.AMBIGUOUS, EdgeType.CALLS, .9, "two candidates");
        assertEquals(ImpactKind.UNCERTAIN, classifier.classify(test, 1, List.of(ambiguous), .9, true));
    }

    @Test
    void dynamicAndLowConfidencePathsAreUncertain() {
        assertEquals(ImpactKind.UNCERTAIN, classifier.classify(production, 1,
                List.of(edge(production, ResolutionStatus.DYNAMIC, EdgeType.DYNAMIC, .9, "reflection")), .9, true));
        assertEquals(ImpactKind.UNCERTAIN, classifier.classify(production, 1,
                List.of(edge(production, ResolutionStatus.RESOLVED, EdgeType.CALLS, .74, "weak")), .74, true));
        assertEquals(ImpactKind.INDIRECT, classifier.classify(production, 1,
                List.of(edge(production, ResolutionStatus.RESOLVED, EdgeType.CALLS, .75, "threshold")), .75, true));
    }

    @Test
    void reasonNamesTheActualUncertainEdge() {
        GraphEdge nearest = edge(production, ResolutionStatus.RESOLVED, EdgeType.CALLS, 1, "nearest exact");
        GraphEdge deeper = new GraphEdge(target, target, EdgeType.DYNAMIC, SourceLocation.unknown("Target.java"),
                .6, ResolutionStatus.DYNAMIC, ResolutionLevel.NONE, "reflective lookup");
        String reason = classifier.reason(List.of(nearest, deeper), ImpactKind.UNCERTAIN, .6);
        assertTrue(reason.contains("dynamic relationship"));
        assertTrue(reason.contains("reflective lookup"));
        assertTrue(reason.contains("path confidence 60%"));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(NullPointerException.class, () -> new ImpactClassifier(null));
        assertThrows(IllegalArgumentException.class,
                () -> classifier.classify(production, -1, List.of(), 1, true));
        assertThrows(IllegalArgumentException.class,
                () -> classifier.classify(production, 1, List.of(), Double.NaN, true));
    }

    private CodeGraph graph() {
        CodeGraph result = new CodeGraph();
        result.addNode(new GraphNode(target, SymbolKind.METHOD, "Target.java", 1, 1, false));
        result.addNode(new GraphNode(production, SymbolKind.METHOD, "Service.java", 1, 1, false));
        result.addNode(new GraphNode(test, SymbolKind.METHOD, "src/test/ServiceTest.java", 1, 1, true));
        return result;
    }

    private GraphEdge edge(SymbolId source, ResolutionStatus status, EdgeType type,
                           double confidence, String evidence) {
        return new GraphEdge(source, target, type, SourceLocation.unknown("Source.java"), confidence, status,
                ResolutionLevel.L1_SAME_CLASS, evidence);
    }
}
