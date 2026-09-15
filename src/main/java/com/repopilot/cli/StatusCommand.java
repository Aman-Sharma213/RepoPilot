package com.repopilot.cli;

import com.repopilot.analysis.CoverageReport;
import com.repopilot.analysis.RepositoryStatistics;
import com.repopilot.analysis.UnresolvedCategory;
import com.repopilot.git.GitService;
import com.repopilot.graph.EdgeType;
import com.repopilot.index.RepoIndex;
import com.repopilot.persist.IndexPaths;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "status", description = "Show what is currently indexed and how much of it resolved.")
public class StatusCommand implements Callable<Integer> {

    @Mixin
    RepoOption repo = new RepoOption();

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        try {
            Path root = repo.resolveRoot();
            Ui ui = new Ui(out);
            ui.header();
            ui.line("Repository: " + root);
            GitService git = new GitService(root);
            ui.line("Git:        " + (git.isGitRepository()
                    ? git.currentBranch().orElse("(detached)") + " @ "
                    + git.headSha().map(s -> s.substring(0, Math.min(8, s.length()))).orElse("no commits")
                    : "not a git repository"));
            IndexPaths paths = new IndexPaths(root);
            if (!paths.hasIndex()) {
                ui.line("Index:      none (run: repo-pilot index)");
                out.flush();
                return ExitCodes.NOT_INDEXED;
            }
            RepoIndex index = CommandSupport.requireIndex(root);
            RepositoryStatistics repositoryStats = RepositoryStatistics.from(index);
            CoverageReport coverage = CoverageReport.from(index.graph());
            var metadata = new com.repopilot.persist.IndexStore().readMetadata(root);
            ui.line("Index:      " + paths.directory());
            ui.line("Created:    " + metadata.createdAt() + " by repo-pilot " + metadata.toolVersion());
            ui.line("Mode:       " + index.stats().mode());
            ui.blank();
            ui.line("REPOSITORY OVERVIEW");
            ui.line("Files:      " + repositoryStats.indexedFiles() + " Java indexed ("
                    + repositoryStats.productionFiles() + " production, " + repositoryStats.testFiles()
                    + " test)");
            ui.line("Source:     " + repositoryStats.sourceLines() + " lines, "
                    + repositoryStats.sourceBytes() + " bytes");
            ui.line("Symbols:    " + index.stats().symbols() + " total (" + repositoryStats.typeSymbols()
                    + " types, " + repositoryStats.callableSymbols() + " callables)");
            ui.line("Graph:      " + index.stats().nodes() + " nodes, " + index.stats().edges() + " edges");
            ui.line("Duration:   " + index.stats().millis() + " ms");
            ui.blank();
            ui.line("ANALYSIS COVERAGE");
            ui.line("Status:     " + coverage.completeness());
            ui.line("Project:    " + Math.round(coverage.projectResolutionRate() * 100)
                    + "% of project-impact relationships resolved");
            ui.line("JDK external:        " + coverage.count(UnresolvedCategory.JDK_EXTERNAL));
            ui.line("Dependency external: " + coverage.count(UnresolvedCategory.DEPENDENCY_EXTERNAL));
            ui.line("Project unresolved:  " + coverage.count(UnresolvedCategory.PROJECT_LOCAL_UNRESOLVED));
            ui.line("Dynamic:             " + coverage.count(UnresolvedCategory.DYNAMIC));
            if (coverage.mayMissProjectFiles()) {
                ui.line("Warning: additional affected files may exist beyond project-local/dynamic gaps.");
            }
            ui.blank();
            ui.line("Edges by type");
            Map<String, Integer> byType = new LinkedHashMap<>();
            for (EdgeType type : EdgeType.values()) {
                int count = index.graph().edgesOfType(type).size();
                if (count > 0) {
                    byType.put(type.name(), count);
                }
            }
            byType.forEach((type, count) -> ui.line("  " + type + ": " + count));
            ui.blank();
            ui.line("Top unresolved reasons (diagnostic)");
            index.graph().unresolvedReferences().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            u -> u.status() + " " + shorten(u.reason()), java.util.stream.Collectors.counting()))
                    .entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(e -> ui.line("  " + e.getValue() + "x " + e.getKey()));
            out.flush();
            return ExitCodes.OK;
        } catch (Exception e) {
            return CommandSupport.handle(e, err);
        }
    }

    private String shorten(String reason) {
        String[] words = reason.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.contains(".") || word.contains("(")) {
                sb.append("<symbol> ");
            } else {
                sb.append(word).append(' ');
            }
            if (sb.length() > 70) {
                break;
            }
        }
        return sb.toString().trim();
    }
}
