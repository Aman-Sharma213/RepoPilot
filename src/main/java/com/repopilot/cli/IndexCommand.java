package com.repopilot.cli;

import com.repopilot.analysis.CoverageReport;
import com.repopilot.analysis.RepositoryStatistics;
import com.repopilot.analysis.UnresolvedCategory;
import com.repopilot.concurrent.ExecutionStrategy;
import com.repopilot.incremental.ChangeType;
import com.repopilot.incremental.IncrementalIndexer;
import com.repopilot.incremental.ParseCache;
import com.repopilot.index.IndexingService;
import com.repopilot.index.ParseCoordinator;
import com.repopilot.index.RepoIndex;
import com.repopilot.persist.IndexStore;
import com.repopilot.scan.RepositoryScanner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "index", description = "Index the repository: parse, resolve symbols and build the code graph.")
public class IndexCommand implements Callable<Integer> {

    @Mixin
    RepoOption repo = new RepoOption();

    @Option(names = "--full", description = "Ignore the previous index and re-parse every file.")
    boolean full;

    @Option(names = "--threads", paramLabel = "<n>", description = "Parallelism (default: 1).")
    int threads = 1;

    @Option(names = "--strategy", paramLabel = "<strategy>",
            description = "Thread strategy: ${COMPLETION-CANDIDATES} (default: SINGLE_THREAD).")
    ExecutionStrategy strategy = ExecutionStrategy.SINGLE_THREAD;

    @Option(names = "--no-spring", description = "Disable Spring dependency-injection analysis.")
    boolean noSpring;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        try {
            Path root = repo.resolveRoot();
            IndexStore store = new IndexStore();
            Map<String, com.repopilot.incremental.FileRecord> previous = Map.of();
            ParseCache cache = new ParseCache();
            if (!full && store.exists(root)) {
                try {
                    previous = store.load(root).files();
                    cache = ParseCache.load(root);
                } catch (RuntimeException e) {
                    out.println("previous index unusable (" + e.getMessage() + "); indexing from scratch");
                    previous = Map.of();
                    cache = new ParseCache();
                }
            }
            ParseCoordinator coordinator = ParseCoordinator.with(strategy, Math.max(1, threads));
            IndexingService service = new IndexingService(new RepositoryScanner(), coordinator, !noSpring);
            IncrementalIndexer indexer = new IncrementalIndexer(new RepositoryScanner(), coordinator, service);

            IncrementalIndexer.Outcome outcome = indexer.update(root, previous, cache);
            RepoIndex index = outcome.index();
            store.save(index);
            cache.save(root);
            RepositoryStatistics repositoryStats = RepositoryStatistics.from(index);
            CoverageReport coverage = CoverageReport.from(index.graph());

            Ui ui = new Ui(out);
            ui.header();
            ui.line("Mode:      " + index.stats().mode());
            ui.line("Files:     " + index.stats().files() + " indexed, " + outcome.reparsedFiles().size()
                    + " parsed, " + outcome.reusedFiles().size() + " reused from cache");
            if (!previous.isEmpty()) {
                ui.line("Changes:   " + outcome.changes().count(ChangeType.ADDED) + " added, "
                        + outcome.changes().count(ChangeType.MODIFIED) + " modified, "
                        + outcome.changes().count(ChangeType.DELETED) + " deleted, "
                        + outcome.changes().count(ChangeType.RENAMED) + " renamed");
            }
            ui.line("Symbols:   " + index.stats().symbols());
            ui.line("Graph:     " + index.stats().nodes() + " nodes, " + index.stats().edges() + " edges");
            ui.line("Source:    " + repositoryStats.sourceLines() + " lines in "
                    + repositoryStats.productionFiles() + " production / " + repositoryStats.testFiles()
                    + " test file(s)");
            ui.line("Coverage:  " + coverage.completeness() + ", "
                    + Math.round(coverage.projectResolutionRate() * 100) + "% project-impact relationships");
            ui.line("External:  " + coverage.count(UnresolvedCategory.JDK_EXTERNAL) + " JDK, "
                    + coverage.count(UnresolvedCategory.DEPENDENCY_EXTERNAL) + " dependency relationship(s)");
            ui.line("Risk gaps: " + coverage.count(UnresolvedCategory.PROJECT_LOCAL_UNRESOLVED)
                    + " project-local, " + coverage.count(UnresolvedCategory.DYNAMIC) + " dynamic");
            if (coverage.mayMissProjectFiles()) {
                ui.line("Warning:   affected-file results may be incomplete beyond unresolved project/dynamic calls");
            }
            if (index.stats().filesWithSyntaxErrors() > 0) {
                ui.line("Warning:   " + index.stats().filesWithSyntaxErrors()
                        + " file(s) contain syntax errors and were indexed partially");
            }
            ui.line("Digest:    " + index.stats().digest().substring(0, 16));
            ui.line("Duration:  " + index.stats().millis() + " ms");
            out.flush();
            return ExitCodes.OK;
        } catch (Exception e) {
            return CommandSupport.handle(e, err);
        }
    }
}
