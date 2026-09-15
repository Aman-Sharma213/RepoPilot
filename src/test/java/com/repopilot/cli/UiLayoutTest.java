package com.repopilot.cli;

import com.repopilot.graph.EdgeType;
import com.repopilot.graph.GraphEdge;
import com.repopilot.impact.AffectedSymbol;
import com.repopilot.impact.ImpactKind;
import com.repopilot.impact.ImpactResult;
import com.repopilot.impact.UnresolvedImpact;
import com.repopilot.parse.SourceLocation;
import com.repopilot.resolve.ResolutionStatus;
import com.repopilot.resolve.ResolutionLevel;
import com.repopilot.symbol.SymbolId;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiLayoutTest {
    @Test
    void changedSectionUsesIndexedPathAndWhyHeadingsKeepClassification() {
        SymbolId target = new SymbolId("a.Target#changed()");
        SymbolId indirect = new SymbolId("a.Service#work()");
        AffectedSymbol direct = new AffectedSymbol(target, "src/main/java/a/Target.java", 0, List.of(), 1,
                ImpactKind.DIRECT, "changed symbol");
        GraphEdge edge = new GraphEdge(indirect, target, EdgeType.CALLS,
                SourceLocation.unknown("src/main/java/a/Service.java"), .9, ResolutionStatus.RESOLVED,
                ResolutionLevel.L1_SAME_CLASS, "service call");
        AffectedSymbol dependent = new AffectedSymbol(indirect, "src/main/java/a/Service.java", 1, List.of(edge), .9,
                ImpactKind.INDIRECT, "calls target");
        ImpactResult result = new ImpactResult(List.of(target), List.of(direct, dependent), 1, List.of(), List.of());

        StringWriter buffer = new StringWriter();
        new Ui(new PrintWriter(buffer)).impact(result, true);
        String output = buffer.toString();

        assertTrue(output.contains("Changed:" + System.lineSeparator()
                + "  src/main/java/a/Target.java"), output);
        assertTrue(output.contains("INDIRECTLY AFFECTED - Radius 1"), output);
        assertFalse(output.contains("\nAFFECTED - Radius"), output);
    }

    @Test
    void unresolvedEvidenceUsesAClosedDeterministicTree() {
        SymbolId target = new SymbolId("a.Target#changed()");
        AffectedSymbol direct = new AffectedSymbol(target, "Target.java", 0, List.of(), 1,
                ImpactKind.DIRECT, "changed symbol");
        UnresolvedImpact first = new UnresolvedImpact(target, EdgeType.CALLS, ResolutionStatus.DYNAMIC,
                "dynamic()", "runtime dispatch", SourceLocation.unknown("Target.java"));
        UnresolvedImpact last = new UnresolvedImpact(target, EdgeType.RETURN_TYPE, ResolutionStatus.UNRESOLVED,
                "Missing", "outside index", SourceLocation.unknown("Target.java"));
        ImpactResult result = new ImpactResult(List.of(target), List.of(direct), 0, List.of(), List.of(first, last));

        StringWriter buffer = new StringWriter();
        new Ui(new PrintWriter(buffer)).impact(result, new Ui.ImpactDisplayOptions(false, true, 20));
        String output = buffer.toString();

        assertTrue(output.contains("├── DYNAMIC"), output);
        assertTrue(output.contains("└── UNRESOLVED"), output);
        assertTrue(output.contains("    evidence: Missing"), output);
        assertFalse(output.contains("WHY"), output);
    }

    @Test
    void unresolvedDetailsAreHiddenByDefaultButCoverageRemainsVisible() {
        SymbolId target = new SymbolId("a.Target#changed()");
        AffectedSymbol direct = new AffectedSymbol(target, "Target.java", 0, List.of(), 1,
                ImpactKind.DIRECT, "changed symbol");
        UnresolvedImpact unresolved = new UnresolvedImpact(target, EdgeType.CALLS, ResolutionStatus.UNRESOLVED,
                "service.call()", "receiver is neither a visible variable nor indexed type",
                SourceLocation.unknown("Target.java"));
        ImpactResult result = new ImpactResult(List.of(target), List.of(direct), 0, List.of(), List.of(unresolved));

        StringWriter buffer = new StringWriter();
        new Ui(new PrintWriter(buffer)).impact(result, false);
        String output = buffer.toString();
        assertTrue(output.contains("ANALYSIS COVERAGE"), output);
        assertTrue(output.contains("additional affected files may exist"), output);
        assertTrue(output.contains("Details hidden"), output);
        assertFalse(output.contains("evidence: service.call()"), output);
    }
}
