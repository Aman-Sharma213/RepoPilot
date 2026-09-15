package com.repopilot.cli;

/** Stable process exit codes. Scripts depend on these, so they must not change silently. */
public final class ExitCodes {
    public static final int OK = 0;
    /** Recoverable runtime failure (IO error, network error, corrupt index...). */
    public static final int ERROR = 1;
    /** Bad command line usage (unknown command, bad argument). */
    public static final int USAGE = 2;
    /** The requested target could not be resolved unambiguously. */
    public static final int AMBIGUOUS = 3;
    /** No index present for the repository. */
    public static final int NOT_INDEXED = 4;

    private ExitCodes() {
    }
}
