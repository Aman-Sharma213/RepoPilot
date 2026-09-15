package com.repopilot.cli;

import com.repopilot.benchmark.BenchmarkCase;
import com.repopilot.benchmark.BenchmarkMetrics;
import com.repopilot.benchmark.BenchmarkRunner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "benchmark",
        description = "Evaluate impact precision/recall against the cases in a benchmark directory.")
public class BenchmarkCommand implements Callable<Integer> {

    @Option(names = "--dir", paramLabel = "<dir>", description = "Benchmark directory (default: benchmarks).")
    Path directory = Path.of("benchmarks");

    @Option(names = "--strategies", description = "Also compare indexing strategies on each case.")
    boolean strategies;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        try {
            BenchmarkRunner runner = new BenchmarkRunner();
            List<BenchmarkCase> cases = runner.discover(directory);
            if (cases.isEmpty()) {
                err.println("error: no benchmark cases with expected.json found in " + directory);
                err.flush();
                return ExitCodes.USAGE;
            }
            Ui ui = new Ui(out);
            ui.header();
            List<BenchmarkMetrics> all = new ArrayList<>();
            for (BenchmarkCase benchmarkCase : cases) {
                BenchmarkMetrics metrics = runner.run(benchmarkCase);
                all.add(metrics);
                out.printf("%-16s P %.2f  R %.2f  F1 %.2f   TP %d FP %d FN %d   index %d ms, query %d ms%n",
                        metrics.name(), metrics.precision(), metrics.recall(), metrics.f1(),
                        metrics.truePositives(), metrics.falsePositives(), metrics.falseNegatives(),
                        metrics.indexMillis(), metrics.queryMillis());
                metrics.falsePositiveSymbols().forEach(s -> out.println("    false positive: " + s));
                metrics.falseNegativeSymbols().forEach(s -> out.println("    false negative: " + s));
                if (metrics.issueRanking().enabled()) {
                    out.printf("    issue-to-file Recall@%d %.2f (%d/%d)%n", metrics.issueRanking().topK(),
                            metrics.issueRanking().recallAtK(), metrics.issueRanking().hits(),
                            metrics.issueRanking().expected());
                    metrics.issueRanking().missedFiles().forEach(s -> out.println("    missed expected file: " + s));
                }
                if (strategies) {
                    runner.compareStrategies(benchmarkCase.repository(),
                            Math.max(2, Runtime.getRuntime().availableProcessors()))
                            .forEach((strategy, result) -> out.printf(
                                    "    %-16s %5d ms  digest %s%n", strategy, result.millis(),
                                    result.digest().substring(0, 12)));
                }
            }
            out.println();
            out.println(Ui.RULE);
            out.printf("Cases: %d%n", all.size());
            out.printf("Macro precision: %.3f%n",
                    all.stream().mapToDouble(BenchmarkMetrics::precision).average().orElse(0));
            out.printf("Macro recall:    %.3f%n",
                    all.stream().mapToDouble(BenchmarkMetrics::recall).average().orElse(0));
            out.printf("Macro F1:        %.3f%n",
                    all.stream().mapToDouble(BenchmarkMetrics::f1).average().orElse(0));
            out.printf("Total index time: %d ms, total query time: %d ms%n",
                    all.stream().mapToLong(BenchmarkMetrics::indexMillis).sum(),
                    all.stream().mapToLong(BenchmarkMetrics::queryMillis).sum());
            out.printf("Peak measured heap delta: %.1f MiB%n",
                    all.stream().mapToLong(BenchmarkMetrics::memoryBytes).max().orElse(0) / (1024.0 * 1024.0));
            out.flush();
            boolean perfect = all.stream().allMatch(m -> m.falsePositives() == 0 && m.falseNegatives() == 0
                    && m.issueRanking().passed());
            return perfect ? ExitCodes.OK : ExitCodes.ERROR;
        } catch (Exception e) {
            return CommandSupport.handle(e, err);
        }
    }
}
