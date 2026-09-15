package com.repopilot.cli;

import com.repopilot.analysis.CoverageReport;
import com.repopilot.analysis.UnresolvedCategory;
import com.repopilot.analysis.UnresolvedClassifier;
import com.repopilot.impact.AffectedSymbol;
import com.repopilot.impact.ImpactExplainer;
import com.repopilot.impact.ImpactKind;
import com.repopilot.impact.ImpactResult;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/** Console rendering. Keeps every command's output consistent and script friendly. */
public class Ui {

    public record ImpactDisplayOptions(boolean explain, boolean showUnresolved, int unresolvedLimit) {
        public ImpactDisplayOptions {
            if (unresolvedLimit < 0) throw new IllegalArgumentException("unresolved limit must be >= 0");
        }
    }

    public static final String RULE = "────────────────────────────────────";

    private final PrintWriter out;

    public Ui(PrintWriter out) {
        this.out = out;
    }

    public void header() {
        out.println("Repo Pilot");
        out.println(RULE);
        out.println();
    }

    public void section(String title) {
        out.println(title);
    }

    public void tree(List<String> items) {
        for (int i = 0; i < items.size(); i++) {
            out.println((i == items.size() - 1 ? "└── " : "├── ") + items.get(i));
        }
    }

    public void blank() {
        out.println();
    }

    public void line(String text) {
        out.println(text);
    }

    /** Renders an impact result in the documented layout. */
    public void impact(ImpactResult result, boolean explain) {
        impact(result, new ImpactDisplayOptions(explain, false, 20));
    }

    /** Renders an impact result with detailed diagnostics available only on explicit request. */
    public void impact(ImpactResult result, ImpactDisplayOptions options) {
        header();
        List<AffectedSymbol> direct = result.ofKind(ImpactKind.DIRECT);
        out.println("Changed:");
        for (var target : result.targets()) {
            String file = direct.stream().filter(item -> item.symbol().equals(target))
                    .map(AffectedSymbol::file).findFirst()
                    .orElse(target.ownerFqn().substring(target.ownerFqn().lastIndexOf('.') + 1) + ".java");
            out.println("  " + file);
            out.println("    └── " + target.display());
        }
        out.println();

        if (!direct.isEmpty()) {
            out.println("DIRECTLY MODIFIED");
            tree(direct.stream().map(a -> a.symbol().display() + "   [" + a.file() + "]").toList());
            out.println();
        }

        Map<Integer, List<AffectedSymbol>> byRadius = result.byRadius();
        List<AffectedSymbol> indirect = result.ofKind(ImpactKind.INDIRECT);
        if (!indirect.isEmpty()) {
            out.println("INDIRECTLY AFFECTED");
            out.println();
            byRadius.forEach((radius, symbols) -> {
                List<AffectedSymbol> filtered = symbols.stream()
                        .filter(s -> s.kind() == ImpactKind.INDIRECT).toList();
                if (filtered.isEmpty()) {
                    return;
                }
                out.println("Radius " + radius);
                tree(filtered.stream().map(a -> a.symbol().display() + "   [" + a.file() + ", "
                        + Math.round(a.confidence() * 100) + "%]").toList());
                out.println();
            });
        }

        List<AffectedSymbol> tests = result.ofKind(ImpactKind.TEST_IMPACT);
        if (!tests.isEmpty()) {
            out.println("TEST IMPACT");
            tree(tests.stream().map(a -> a.symbol().display() + "   [" + a.file() + "]").toList());
            out.println();
        }

        List<AffectedSymbol> uncertain = result.ofKind(ImpactKind.UNCERTAIN);
        if (!uncertain.isEmpty()) {
            out.println("UNCERTAIN");
            tree(uncertain.stream().map(a -> a.symbol().display() + "   [" + a.file() + ", "
                    + Math.round(a.confidence() * 100) + "%] " + a.reason()).toList());
            out.println();
        }

        var classified = result.unresolvedImpacts().stream()
                .collect(java.util.stream.Collectors.groupingBy(UnresolvedClassifier::classify,
                        () -> new java.util.EnumMap<>(UnresolvedCategory.class),
                        java.util.stream.Collectors.counting()));
        if (!classified.isEmpty()) {
            out.println("ANALYSIS COVERAGE");
            for (UnresolvedCategory category : UnresolvedCategory.values()) {
                long count = classified.getOrDefault(category, 0L);
                if (count > 0) out.println("  " + category + ": " + count);
            }
            long risky = classified.getOrDefault(UnresolvedCategory.PROJECT_LOCAL_UNRESOLVED, 0L)
                    + classified.getOrDefault(UnresolvedCategory.DYNAMIC, 0L);
            if (risky > 0) {
                out.println("  Warning: additional affected files may exist beyond " + risky
                        + " project-local/dynamic relationship gap(s).");
            } else {
                out.println("  External gaps do not normally point to files that contributors should modify.");
            }
            if (!options.showUnresolved()) {
                out.println("  Details hidden; use --show-unresolved to inspect them.");
            }
            out.println();
        }

        if (options.showUnresolved() && !result.unresolvedImpacts().isEmpty()) {
            out.println("UNRESOLVED IMPACT EVIDENCE — NO TARGET INFERRED");
            List<com.repopilot.impact.UnresolvedImpact> displayed = result.unresolvedImpacts().stream()
                    .limit(options.unresolvedLimit()).toList();
            for (int i = 0; i < displayed.size(); i++) {
                var finding = displayed.get(i);
                boolean last = i == displayed.size() - 1;
                String branch = last ? "└── " : "├── ";
                String continuation = last ? "    " : "│   ";
                out.println(branch + finding.status() + " " + finding.source().display() + " "
                        + finding.relationshipType() + " <unknown>   [" + finding.location() + "]");
                out.println(continuation + "evidence: " + finding.rawText());
                out.println(continuation + "reason: " + finding.reason());
            }
            int omitted = result.unresolvedImpacts().size() - displayed.size();
            if (omitted > 0) out.println("... " + omitted + " additional unresolved relationship(s) omitted");
            out.println();
        }

        if (options.explain()) {
            ImpactExplainer explainer = new ImpactExplainer();
            out.println("WHY");
            out.println();
            for (AffectedSymbol affected : result.affected()) {
                if (affected.radius() == 0) {
                    continue;
                }
                out.println(whyTitle(affected));
                out.println();
                out.print(explainer.explain(affected));
                out.println();
            }
        }

        out.println(RULE);
        out.printf("Impact: %d symbols / %d files%n", result.symbolCount(), result.files().size());
        out.printf("Resolved-path strength: %d%%%n", Math.round(result.overallConfidence() * 100));
        for (String note : result.notes()) {
            out.println("note: " + note);
        }
        out.flush();
    }

    private String whyTitle(AffectedSymbol affected) {
        String section = switch (affected.kind()) {
            case DIRECT -> "DIRECTLY MODIFIED";
            case INDIRECT -> "INDIRECTLY AFFECTED";
            case TEST_IMPACT -> "TEST IMPACT";
            case UNCERTAIN -> "UNCERTAIN";
        };
        return section + " - Radius " + affected.radius();
    }

    public void error(String message) {
        out.println("error: " + message);
        out.flush();
    }
}
