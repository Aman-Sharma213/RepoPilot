package com.repopilot.cli;

import com.repopilot.ai.RankedTarget;
import com.repopilot.ai.TargetResolution;
import com.repopilot.impact.AffectedSymbol;
import com.repopilot.impact.ImpactKind;
import com.repopilot.impact.ImpactResult;
import com.repopilot.index.RepoIndex;

import java.util.List;

/** Converts verified candidates and graph paths into a contributor-oriented reading plan. */
public class ContributionPlanRenderer {

    public void render(Ui ui, String description, String mode, RepoIndex index,
                       TargetResolution resolution, ImpactResult result) {
        ui.section("CONTRIBUTION PLAN");
        ui.line("Requirement: " + description);
        ui.line("Mode: " + mode);
        ui.line("Target ranking: " + resolution.source());
        ui.blank();

        RankedTarget first = resolution.targets().get(0);
        ui.section("START HERE");
        ui.line("  " + first.symbol().display());
        index.symbols().find(first.symbol()).ifPresent(symbol -> ui.line("  " + symbol.file() + ':'
                + symbol.location().startLine()));
        ui.line("  Why: " + first.reason());
        ui.blank();

        ui.section("LIKELY MODIFY");
        ui.tree(resolution.targets().stream().map(target -> index.symbols().find(target.symbol())
                .map(symbol -> target.symbol().display() + "   [" + symbol.file() + "]")
                .orElse(target.symbol().display())).toList());
        ui.blank();

        List<AffectedSymbol> readNext = result.affected().stream()
                .filter(a -> a.radius() > 0 && a.kind() != ImpactKind.TEST_IMPACT)
                .limit(8).toList();
        if (!readNext.isEmpty()) {
            ui.section("READ NEXT / INSPECT FOR IMPACT");
            ui.tree(readNext.stream().map(a -> "R" + a.radius() + " " + a.symbol().display()
                    + "   [" + a.file() + "]").toList());
            ui.blank();
        }

        List<AffectedSymbol> tests = result.ofKind(ImpactKind.TEST_IMPACT);
        if (!tests.isEmpty()) {
            ui.section("RELEVANT TESTS");
            ui.tree(tests.stream().limit(8).map(a -> a.symbol().display() + "   [" + a.file() + "]").toList());
            ui.blank();
        }

        ui.line("Interpretation: LIKELY MODIFY is a recommendation, not a guarantee. "
                + "READ NEXT items are impact-inspection targets and may not require edits.");
        ui.blank();
    }
}
