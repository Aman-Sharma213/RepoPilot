package com.repopilot.analysis;

import com.repopilot.graph.CodeGraph;
import com.repopilot.graph.EdgeType;
import com.repopilot.graph.UnresolvedReference;
import com.repopilot.parse.SourceLocation;
import com.repopilot.resolve.ResolutionStatus;
import com.repopilot.symbol.SymbolId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoverageReportTest {

    @Test
    void separatesExternalNoiseFromProjectRiskGaps() {
        CodeGraph graph = new CodeGraph();
        SymbolId source = new SymbolId("a.A#run()");
        graph.addUnresolved(new UnresolvedReference(source, EdgeType.CALLS, ResolutionStatus.UNRESOLVED,
                "list.size()", "list has type java.util.List which is outside the indexed sources",
                SourceLocation.unknown("A.java")));
        graph.addUnresolved(new UnresolvedReference(source, EdgeType.CALLS, ResolutionStatus.UNRESOLVED,
                "service.work()", "receiver service is neither a visible variable nor indexed type",
                SourceLocation.unknown("A.java")));
        CoverageReport report = CoverageReport.from(graph);
        assertEquals(1L, report.count(UnresolvedCategory.JDK_EXTERNAL));
        assertEquals(1L, report.count(UnresolvedCategory.PROJECT_LOCAL_UNRESOLVED));
        assertTrue(report.mayMissProjectFiles());
        assertEquals(CoverageReport.Completeness.LOW_COVERAGE, report.completeness());
    }

    @Test
    void externalOnlyGapsDoNotClaimThatProjectFilesAreMissing() {
        CodeGraph graph = new CodeGraph();
        graph.addUnresolved(new UnresolvedReference(new SymbolId("a.A#run()"), EdgeType.CALLS,
                ResolutionStatus.UNRESOLVED, "log.error()", "log has type Logger outside indexed sources",
                SourceLocation.unknown("A.java")));
        CoverageReport report = CoverageReport.from(graph);
        assertEquals(1L, report.count(UnresolvedCategory.DEPENDENCY_EXTERNAL));
        assertFalse(report.mayMissProjectFiles());
        assertEquals(CoverageReport.Completeness.COMPLETE_ENOUGH, report.completeness());
    }
}
