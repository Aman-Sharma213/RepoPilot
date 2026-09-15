package com.repopilot.concurrent;

/** Thread strategies compared by the Phase 9 benchmark. */
public enum ExecutionStrategy {
    /** No threads at all: the reference implementation and the correctness baseline. */
    SINGLE_THREAD,
    /** Platform threads sized to the CPU: appropriate for CPU-bound parsing. */
    FIXED_POOL,
    /**
     * Virtual threads. Cheap to create, but parsing is CPU bound and each task pins a carrier
     * thread while the native parser runs, so this is not automatically faster.
     */
    VIRTUAL_THREADS
}
