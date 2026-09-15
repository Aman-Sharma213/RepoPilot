package com.repopilot.benchmark;

import com.repopilot.concurrent.ExecutionStrategy;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkRunnerTest {

    @Test
    void metricsCalculatePrecisionRecallAndF1() {
        BenchmarkMetrics metrics = new BenchmarkMetrics("case", 3, 1, 2,
                List.of("fp"), List.of("fn1", "fn2"), 10, 2, 100, 4, 3,
                IssueRankingMetrics.disabled());
        assertEquals(.75, metrics.precision());
        assertEquals(.60, metrics.recall());
        assertEquals(2 * .75 * .60 / (.75 + .60), metrics.f1(), 1e-12);
    }

    @Test
    void bundledCasesArePerfectAndStrategiesAreEquivalent() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner();
        List<BenchmarkCase> cases = runner.discover(Path.of("benchmarks"));
        assertEquals(6, cases.size());
        for (BenchmarkCase benchmarkCase : cases) {
            BenchmarkMetrics metrics = runner.run(benchmarkCase);
            assertEquals(0, metrics.falsePositives(), benchmarkCase.name());
            assertEquals(0, metrics.falseNegatives(), benchmarkCase.name());
            assertTrue(metrics.issueRanking().passed(), benchmarkCase.name());
            Map<ExecutionStrategy, BenchmarkRunner.StrategyResult> strategies =
                    runner.compareStrategies(benchmarkCase.repository(), 4);
            assertEquals(ExecutionStrategy.values().length, strategies.size());
            assertEquals(1, strategies.values().stream().map(BenchmarkRunner.StrategyResult::digest).distinct().count());
        }
    }

    @Test
    void mismatchedStrategyGraphsAreRejected() {
        Map<ExecutionStrategy, BenchmarkRunner.StrategyResult> results = new LinkedHashMap<>();
        results.put(ExecutionStrategy.SINGLE_THREAD, new BenchmarkRunner.StrategyResult(1, "one", 2));
        results.put(ExecutionStrategy.FIXED_POOL, new BenchmarkRunner.StrategyResult(1, "two", 2));
        assertThrows(IllegalStateException.class, () -> BenchmarkRunner.requireEquivalentStrategies(results));
    }
}
