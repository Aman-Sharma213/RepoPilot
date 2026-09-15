package com.repopilot.cli;

import com.repopilot.impact.ImpactEngine;
import com.repopilot.impact.ImpactResult;
import com.repopilot.index.RepoIndex;
import com.repopilot.symbol.SymbolId;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "impact", description = "Show what depends on a symbol.")
public class ImpactCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "<symbol>",
            description = "Symbol id or name, e.g. com.foo.AuthService#validate(Token) or AuthService.validate")
    String symbol;

    @Mixin
    RepoOption repo = new RepoOption();

    @Option(names = "--radius", paramLabel = "<n>", description = "Maximum dependency radius (default: 3).")
    int radius = 3;

    @Option(names = "--no-tests", description = "Exclude test sources from the impact set.")
    boolean noTests;

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
            Path root = repo.resolveRoot();
            RepoIndex index = CommandSupport.requireIndex(root);
            var match = new SymbolLookup(index.symbols()).find(symbol);
            if (match.candidates().isEmpty()) {
                err.println("error: no indexed symbol matches '" + symbol + "'");
                err.flush();
                return ExitCodes.ERROR;
            }
            if (!match.isUnique()) {
                err.println("Ambiguous symbol '" + symbol + "'.");
                err.println();
                err.println("Possible targets:");
                int i = 1;
                for (SymbolId candidate : match.candidates().stream().limit(10).toList()) {
                    err.println(i++ + ". " + candidate.value());
                }
                err.flush();
                return ExitCodes.AMBIGUOUS;
            }
            ImpactEngine engine = new ImpactEngine(index.graph());
            ImpactResult result = engine.analyse(List.of(match.candidates().get(0)),
                    new ImpactEngine.Options(radius, !noTests, true, 0.0));
            new Ui(out).impact(result, new Ui.ImpactDisplayOptions(explain, showUnresolved, unresolvedLimit));
            return ExitCodes.OK;
        } catch (Exception e) {
            return CommandSupport.handle(e, err);
        }
    }
}
