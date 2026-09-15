package com.repopilot.concurrent;

import com.repopilot.index.IndexingService;
import com.repopilot.index.ParseCoordinator;
import com.repopilot.index.RepoIndex;
import com.repopilot.scan.RepositoryScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {

    private Path repo(Path dir, int classes) throws Exception {
        Files.createDirectories(dir.resolve("src/p"));
        for (int i = 0; i < classes; i++) {
            Files.writeString(dir.resolve("src/p/C" + i + ".java"), """
                    package p;
                    class C%d {
                      private C%d next = new C%d();
                      void run() { next.run(); helper(); }
                      void helper() {}
                    }
                    """.formatted(i, (i + 1) % classes, (i + 1) % classes));
        }
        return dir;
    }

    @Test
    void everyStrategyProducesTheSameGraph(@TempDir Path dir) throws Exception {
        repo(dir, 12);
        List<String> digests = new ArrayList<>();
        for (ExecutionStrategy strategy : ExecutionStrategy.values()) {
            RepoIndex index = new IndexingService(new RepositoryScanner(),
                    ParseCoordinator.with(strategy, 4), true).index(dir);
            digests.add(index.stats().digest());
        }
        assertEquals(1, new java.util.LinkedHashSet<>(digests).size(),
                "graph must not depend on the thread strategy: " + digests);
    }

    @Test
    void repeatedRunsAreIdentical(@TempDir Path dir) throws Exception {
        repo(dir, 8);
        RepoIndex first = new IndexingService(new RepositoryScanner(),
                ParseCoordinator.with(ExecutionStrategy.FIXED_POOL, 4), true).index(dir);
        RepoIndex second = new IndexingService(new RepositoryScanner(),
                ParseCoordinator.with(ExecutionStrategy.FIXED_POOL, 4), true).index(dir);
        assertEquals(first.graph().digest(), second.graph().digest());
    }

    @Test
    void resultsKeepInputOrderNotCompletionOrder() {
        try (IndexingExecutor executor = new IndexingExecutor(ExecutionStrategy.VIRTUAL_THREADS, 4)) {
            List<Integer> inputs = java.util.stream.IntStream.range(0, 30).boxed().toList();
            List<Integer> results = executor.map(inputs, i -> {
                try {
                    // Reverse the natural completion order: late inputs finish first.
                    Thread.sleep((30 - i));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return i;
            }, (i, error) -> -1);
            assertEquals(inputs, results);
        }
    }

    @Test
    void failingTaskDoesNotAbortTheRun() {
        try (IndexingExecutor executor = new IndexingExecutor(ExecutionStrategy.FIXED_POOL, 4)) {
            List<Integer> inputs = List.of(1, 2, 3, 4, 5);
            List<String> results = executor.map(inputs, i -> {
                if (i == 3) {
                    throw new IllegalStateException("boom " + i);
                }
                return "ok " + i;
            }, (i, error) -> "failed " + i + ": " + error.getMessage());
            assertEquals(List.of("ok 1", "ok 2", "failed 3: boom 3", "ok 4", "ok 5"), results);
            assertEquals(5, executor.completedTasks());
        }
    }

    @Test
    void parallelismIsBounded() {
        try (IndexingExecutor executor = new IndexingExecutor(ExecutionStrategy.VIRTUAL_THREADS, 2)) {
            AtomicInteger inFlight = new AtomicInteger();
            AtomicInteger peak = new AtomicInteger();
            List<Integer> inputs = java.util.stream.IntStream.range(0, 40).boxed().toList();
            executor.map(inputs, i -> {
                int current = inFlight.incrementAndGet();
                peak.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                inFlight.decrementAndGet();
                return i;
            }, (i, error) -> -1);
            assertTrue(peak.get() <= 2, "peak concurrency was " + peak.get());
        }
    }

    @Test
    void cancellationStopsWork() {
        IndexingExecutor executor = new IndexingExecutor(ExecutionStrategy.SINGLE_THREAD, 1);
        AtomicInteger executed = new AtomicInteger();
        List<Integer> inputs = java.util.stream.IntStream.range(0, 50).boxed().toList();
        List<Integer> results = executor.map(inputs, i -> {
            if (executed.incrementAndGet() == 5) {
                executor.cancel();
            }
            return i;
        }, (i, error) -> -1);
        assertTrue(executor.isCancelled());
        assertEquals(5, results.size());
    }

    @Test
    void invalidParallelismIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new IndexingExecutor(ExecutionStrategy.FIXED_POOL, 0));
    }
}
