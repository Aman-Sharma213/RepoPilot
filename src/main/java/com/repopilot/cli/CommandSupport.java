package com.repopilot.cli;

import com.repopilot.persist.IndexCorruptException;
import com.repopilot.persist.IndexStore;
import com.repopilot.index.RepoIndex;

import java.nio.file.Path;

/** Shared helpers for commands: index loading and error mapping. */
final class CommandSupport {

    private CommandSupport() {
    }

    static RepoIndex requireIndex(Path root) {
        IndexStore store = new IndexStore();
        if (!store.exists(root)) {
            throw new NotIndexedException("No index found in " + root
                    + ". Run: repo-pilot index");
        }
        return store.load(root);
    }

    static int handle(Exception e, java.io.PrintWriter err) {
        if (e instanceof NotIndexedException) {
            err.println("error: " + e.getMessage());
            err.flush();
            return ExitCodes.NOT_INDEXED;
        }
        if (e instanceof IndexCorruptException) {
            err.println("error: " + e.getMessage());
            err.flush();
            return ExitCodes.ERROR;
        }
        if (e instanceof IllegalArgumentException) {
            err.println("error: " + e.getMessage());
            err.flush();
            return ExitCodes.USAGE;
        }
        err.println("error: " + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        err.flush();
        return ExitCodes.ERROR;
    }

    static class NotIndexedException extends RuntimeException {
        NotIndexedException(String message) {
            super(message);
        }
    }
}
