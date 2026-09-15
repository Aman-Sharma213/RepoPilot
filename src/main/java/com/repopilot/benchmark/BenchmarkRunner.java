package com.repopilot.benchmark;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.concurrent.ExecutionStrategy;
import com.repopilot.ai.CandidateRetriever;
import com.repopilot.impact.AffectedSymbol;
import com.repopilot.impact.ImpactEngine;
import com.repopilot.impact.ImpactResult;
import com.repopilot.index.IndexingService;
import com.repopilot.index.ParseCoordinator;
import com.repopilot.index.RepoIndex;
import com.repopilot.scan.RepositoryScanner;
import com.repopilot.symbol.SymbolId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Measures impact-analysis quality against known-good expectations.
 *
 * <p>A benchmark directory contains one sub-directory per case, each with Java sources and an
 * {@code expected.json} describing the changed symbols and the symbols that must be reported as
 * affected. Nothing here is repository specific: the runner only reads the case definitions.
 */
public class BenchmarkRunner {

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public List<BenchmarkCase> discover(Path benchmarksDirectory) throws IOException {
        List<BenchmarkCase> cases = new ArrayList<>();
        if (!Files.isDirectory(benchmarksDirectory)) {
            throw new IOException("Benchmark directory not found: " + benchmarksDirectory);
        }
        try (var stream = Files.list(benchmarksDirectory)) {
            for (Path directory : stream.filter(Files::isDirectory).sorted().toList()) {
                Path definitionFile = directory.resolve("expected.json");
                if (!Files.isRegularFile(definitionFile)) {
                    continue;
                }
                BenchmarkCase.Definition definition =
                        mapper.readValue(definitionFile.toFile(), BenchmarkCase.Definition.class);
                cases.add(new BenchmarkCase(
                        definition.name() == null ? directory.getFileName().toString() : definition.name(),
                        directory, definition.targets(), definition.expectedAffected(),
                        definition.maxRadius() == null ? 3 : definition.maxRadius(), definition.description(),
                        definition.expectedTopFiles() == null ? List.of() : definition.expectedTopFiles(),
                        definition.topK() == null ? 5 : definition.topK(), definition.sourceUrl()));
            }
        }
        return cases;
    }

    public BenchmarkMetrics run(BenchmarkCase benchmarkCase) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        long indexStart = System.nanoTime();
        RepoIndex index = new IndexingService(new RepositoryScanner(),
                ParseCoordinator.defaultCoordinator(), true).index(benchmarkCase.repository());
        long indexMillis = (System.nanoTime() - indexStart) / 1_000_000;

        List<SymbolId> targets = benchmarkCase.targets().stream().map(SymbolId::new).toList();
        long queryStart = System.nanoTime();
        ImpactResult result = new ImpactEngine(index.graph())
                .analyse(targets, ImpactEngine.Options.defaults().withMaxRadius(benchmarkCase.maxRadius()));
        long queryMillis = (System.nanoTime() - queryStart) / 1_000_000;
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        Set<String> expected = new LinkedHashSet<>(benchmarkCase.expectedAffected());
        Set<String> actual = new LinkedHashSet<>();
        for (AffectedSymbol affected : result.affected()) {
            if (affected.radius() > 0) {
                actual.add(affected.symbol().value());
            }
        }
        List<String> falsePositives = actual.stream().filter(s -> !expected.contains(s)).sorted().toList();
        List<String> falseNegatives = expected.stream().filter(s -> !actual.contains(s)).sorted().toList();
        int truePositives = (int) actual.stream().filter(expected::contains).count();

        IssueRankingMetrics ranking = issueRanking(index, benchmarkCase);
        return new BenchmarkMetrics(benchmarkCase.name(), truePositives, falsePositives.size(),
                falseNegatives.size(), falsePositives, falseNegatives, indexMillis, queryMillis,
                Math.max(0, memoryAfter - memoryBefore), index.stats().symbols(), index.stats().edges(), ranking);
    }

    private IssueRankingMetrics issueRanking(RepoIndex index, BenchmarkCase benchmarkCase) {
        if (benchmarkCase.description() == null || benchmarkCase.description().isBlank()
                || benchmarkCase.expectedTopFiles().isEmpty()) {
            return IssueRankingMetrics.disabled();
        }
        List<String> predicted = new CandidateRetriever(index.symbols())
                .retrieve(benchmarkCase.description(), 50).stream()
                .map(candidate -> candidate.file()).distinct().limit(benchmarkCase.topK()).toList();
        Set<String> expected = new LinkedHashSet<>(benchmarkCase.expectedTopFiles());
        List<String> missed = expected.stream().filter(file -> !predicted.contains(file)).sorted().toList();
        int hits = (int) expected.stream().filter(predicted::contains).count();
        return new IssueRankingMetrics(true, benchmarkCase.topK(), hits, expected.size(), predicted, missed);
    }

    /**
     * Compares indexing strategies on the same sources and verifies that they all produce the same
     * graph. Determinism is a correctness requirement, not an optimisation detail.
     */
    public Map<ExecutionStrategy, StrategyResult> compareStrategies(Path repository, int parallelism)
            throws IOException {
        Map<ExecutionStrategy, StrategyResult> results = new LinkedHashMap<>();
        for (ExecutionStrategy strategy : ExecutionStrategy.values()) {
            long start = System.nanoTime();
            RepoIndex index = new IndexingService(new RepositoryScanner(),
                    ParseCoordinator.with(strategy, parallelism), true).index(repository);
            long millis = (System.nanoTime() - start) / 1_000_000;
            results.put(strategy, new StrategyResult(millis, index.stats().digest(), index.stats().symbols()));
        }
        requireEquivalentStrategies(results);
        return results;
    }

    static void requireEquivalentStrategies(Map<ExecutionStrategy, StrategyResult> results) {
        if (results.isEmpty()) {
            throw new IllegalStateException("No indexing strategy results were produced");
        }
        StrategyResult expected = results.values().iterator().next();
        for (var entry : results.entrySet()) {
            StrategyResult actual = entry.getValue();
            if (!expected.digest().equals(actual.digest()) || expected.symbols() != actual.symbols()) {
                throw new IllegalStateException("Indexing strategies produced different graphs: " + results);
            }
        }
    }

    public record StrategyResult(long millis, String digest, int symbols) {
    }
}
