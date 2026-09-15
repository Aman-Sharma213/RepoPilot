package com.repopilot.cli;

import com.repopilot.ai.GroqClient;
import com.repopilot.ai.NlTargetResolver;
import com.repopilot.ai.RankedTarget;
import com.repopilot.ai.TargetResolution;
import com.repopilot.impact.ImpactEngine;
import com.repopilot.impact.ImpactResult;
import com.repopilot.index.RepoIndex;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "diff", aliases = "plan",
        description = "Analyse a described (not yet written) change: \"Refactor AuthToken validation\".")
public class DiffCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<description>", description = "Natural-language change description.")
    String description;

    @Mixin
    RepoOption repo = new RepoOption();

    @Mixin
    AiOptions ai = new AiOptions();

    @Option(names = "--threshold", paramLabel = "<0..1>",
            description = "Minimum target confidence to proceed (default: 0.80).")
    double threshold = NlTargetResolver.DEFAULT_THRESHOLD;

    @Option(names = "--radius", paramLabel = "<n>", description = "Maximum dependency radius (default: 3).")
    int radius = 3;

    @Option(names = "--explain", description = "Print the full evidence path for every affected symbol.")
    boolean explain;

    @Option(names = "--show-unresolved", description = "Print unresolved impact evidence (hidden by default).")
    boolean showUnresolved;

    @Option(names = "--unresolved-limit", paramLabel = "<n>", defaultValue = "20",
            description = "Maximum unresolved details to print (default: 20).")
    int unresolvedLimit = 20;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        try {
            if (threshold < 0 || threshold > 1) {
                throw new IllegalArgumentException("--threshold must be between 0 and 1");
            }
            Path root = repo.resolveRoot();
            RepoIndex index = CommandSupport.requireIndex(root);
            TargetResolution resolution = new NlTargetResolver(index.symbols(), ai.client())
                    .resolve(description, threshold);

            if (resolution.ambiguous()) {
                err.println("Ambiguous target.");
                err.println();
                if (resolution.alternatives().isEmpty()) {
                    err.println("No candidate symbol matched the description.");
                } else {
                    err.println("Possible targets:");
                    int i = 1;
                    for (RankedTarget target : resolution.alternatives()) {
                        err.printf("%d. %-60s %.2f%n", i++, target.symbol().display(), target.confidence());
                    }
                    err.println();
                    err.println("Re-run with a more specific description or lower --threshold "
                            + "(current: " + threshold + ").");
                }
                for (String note : resolution.notes()) {
                    err.println("note: " + note);
                }
                err.flush();
                return ExitCodes.AMBIGUOUS;
            }

            ImpactResult result = new ImpactEngine(index.graph()).analyse(
                    resolution.targets().stream().map(RankedTarget::symbol).toList(),
                    new ImpactEngine.Options(radius, true, true, 0.0));
            Ui ui = new Ui(out);
            for (String note : resolution.notes()) {
                ui.line("note: " + note);
            }
            ui.blank();
            new ContributionPlanRenderer().render(ui, description, ai.displayMode(), index, resolution, result);
            ui.impact(result, new Ui.ImpactDisplayOptions(explain, showUnresolved, unresolvedLimit));
            return ExitCodes.OK;
        } catch (Exception e) {
            return CommandSupport.handle(e, err);
        }
    }
}
