package com.repopilot.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Runs indexing tasks with bounded parallelism.
 *
 * <p>Guarantees:
 * <ul>
 *   <li><b>Deterministic output</b> - results are returned in input order, never completion order.</li>
 *   <li><b>Exception isolation</b> - one failing file cannot abort the run; the failure is mapped
 *       to a result by the caller supplied error mapper.</li>
 *   <li><b>Bounded resources</b> - a semaphore caps in-flight tasks even with virtual threads.</li>
 *   <li><b>Cancellation</b> - {@link #cancel()} stops submitting and interrupts running tasks.</li>
 *   <li><b>No shared parser state</b> - tasks must create or thread-confine their own parser.</li>
 * </ul>
 */
public class IndexingExecutor implements AutoCloseable {

    private final ExecutionStrategy strategy;
    private final int parallelism;
    private final AtomicInteger completed = new AtomicInteger();
    private volatile boolean cancelled;
    private volatile ExecutorService running;

    public IndexingExecutor(ExecutionStrategy strategy, int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be >= 1");
        }
        this.strategy = strategy;
        this.parallelism = parallelism;
    }

    public static IndexingExecutor defaultExecutor() {
        return new IndexingExecutor(ExecutionStrategy.FIXED_POOL,
                Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public ExecutionStrategy strategy() {
        return strategy;
    }

    public int completedTasks() {
        return completed.get();
    }

    public void cancel() {
        cancelled = true;
        ExecutorService service = running;
        if (service != null) {
            service.shutdownNow();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Maps every input through {@code task}, returning results in input order.
     *
     * @param onError converts a failing input plus its exception into a result, keeping the run alive
     */
    public <I, O> List<O> map(List<I> inputs, Function<I, O> task, java.util.function.BiFunction<I, Throwable, O> onError) {
        if (inputs.isEmpty()) {
            return List.of();
        }
        if (strategy == ExecutionStrategy.SINGLE_THREAD || inputs.size() == 1) {
            List<O> results = new ArrayList<>(inputs.size());
            for (I input : inputs) {
                if (cancelled) {
                    break;
                }
                try {
                    results.add(task.apply(input));
                } catch (RuntimeException e) {
                    results.add(onError.apply(input, e));
                }
                completed.incrementAndGet();
            }
            return results;
        }

        Semaphore permits = new Semaphore(parallelism);
        List<Future<O>> futures = new ArrayList<>(inputs.size());
        try (ExecutorService service = newExecutor()) {
            running = service;
            for (I input : inputs) {
                if (cancelled) {
                    break;
                }
                Callable<O> callable = () -> {
                    permits.acquire();
                    try {
                        return task.apply(input);
                    } finally {
                        permits.release();
                        completed.incrementAndGet();
                    }
                };
                futures.add(service.submit(callable));
            }
            List<O> results = new ArrayList<>(futures.size());
            int i = 0;
            for (Future<O> future : futures) {
                I input = inputs.get(i++);
                try {
                    results.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.add(onError.apply(input, e));
                } catch (CancellationException e) {
                    results.add(onError.apply(input, e));
                } catch (ExecutionException e) {
                    results.add(onError.apply(input, e.getCause() == null ? e : e.getCause()));
                }
            }
            return results;
        } finally {
            running = null;
        }
    }

    private ExecutorService newExecutor() {
        return switch (strategy) {
            case VIRTUAL_THREADS -> Executors.newVirtualThreadPerTaskExecutor();
            case FIXED_POOL -> Executors.newFixedThreadPool(parallelism, namedFactory());
            case SINGLE_THREAD -> Executors.newSingleThreadExecutor(namedFactory());
        };
    }

    private ThreadFactory namedFactory() {
        AtomicInteger counter = new AtomicInteger();
        return r -> {
            Thread thread = new Thread(r, "repo-pilot-index-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public void close() {
        ExecutorService service = running;
        if (service != null) {
            service.shutdownNow();
        }
    }
}
