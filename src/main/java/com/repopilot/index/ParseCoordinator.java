package com.repopilot.index;

import com.repopilot.concurrent.ExecutionStrategy;
import com.repopilot.concurrent.IndexingExecutor;
import com.repopilot.parse.ParseResult;
import com.repopilot.parse.TreeSitterParser;
import com.repopilot.scan.ScannedFile;
import com.repopilot.security.ResourceLimits;

import java.util.List;

/**
 * Parses a list of files, optionally in parallel.
 *
 * <p>Parsers are thread-confined ({@link TreeSitterParser} keeps one native parser per thread), and
 * results keep input order, so the resulting graph does not depend on the execution strategy.
 */
public class ParseCoordinator {

    private final IndexingExecutor executor;
    private final ResourceLimits limits;

    public ParseCoordinator(IndexingExecutor executor, ResourceLimits limits) {
        this.executor = executor;
        this.limits = limits;
    }

    public static ParseCoordinator defaultCoordinator() {
        return new ParseCoordinator(new IndexingExecutor(ExecutionStrategy.SINGLE_THREAD, 1),
                ResourceLimits.defaults());
    }

    public static ParseCoordinator with(ExecutionStrategy strategy, int parallelism) {
        return new ParseCoordinator(new IndexingExecutor(strategy, parallelism), ResourceLimits.defaults());
    }

    public List<ParseResult> parseAll(List<ScannedFile> files) {
        TreeSitterParser parser = new TreeSitterParser(limits);
        return executor.map(files,
                file -> parser.parseFile(file.absolutePath(), file.relativePath()),
                (file, error) -> ParseResult.failed(file.relativePath(),
                        "parsing failed: " + error.getClass().getSimpleName()
                                + (error.getMessage() == null ? "" : " - " + error.getMessage())));
    }

    public IndexingExecutor executor() {
        return executor;
    }
}
