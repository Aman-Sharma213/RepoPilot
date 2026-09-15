package com.repopilot.cli;

import com.repopilot.persist.IndexPaths;
import com.repopilot.scan.Repository;
import com.repopilot.scan.RepositoryScanner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "init", description = "Prepare a repository for analysis and create the .repo-pilot workspace.")
public class InitCommand implements Callable<Integer> {

    @Mixin
    RepoOption repo = new RepoOption();

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
        try {
            Path root = repo.resolveRoot();
            IndexPaths paths = new IndexPaths(root);
            boolean existed = paths.exists();
            Files.createDirectories(paths.directory());
            Files.writeString(paths.versionFile(), IndexPaths.FORMAT_VERSION + System.lineSeparator());
            ensureGitIgnored(root);

            Repository repository = new RepositoryScanner().scan(root);
            out.println("Repo Pilot");
            out.println("────────────────────────────────────");
            out.println((existed ? "Workspace already present: " : "Workspace created: ") + paths.directory());
            out.println("Git repository: " + (RepositoryScanner.isGitRepository(root) ? "yes" : "no"));
            out.printf("Java files discovered: %d (%d skipped)%n",
                    repository.metadata().javaFileCount(), repository.metadata().ignoredCount());
            out.println();
            out.println("Next: repo-pilot index");
            out.flush();
            return ExitCodes.OK;
        } catch (IllegalArgumentException e) {
            err.println("error: " + e.getMessage());
            err.flush();
            return ExitCodes.USAGE;
        } catch (Exception e) {
            err.println("error: " + e.getMessage());
            err.flush();
            return ExitCodes.ERROR;
        }
    }

    /** Adds the workspace directory to .gitignore for Git repositories without following links. */
    private void ensureGitIgnored(Path root) throws java.io.IOException {
        Path gitignore = root.resolve(".gitignore");
        if (!Files.exists(gitignore, LinkOption.NOFOLLOW_LINKS)) {
            if (RepositoryScanner.isGitRepository(root)) {
                Files.writeString(gitignore, IndexPaths.DIRECTORY + "/" + System.lineSeparator());
            }
            return;
        }
        if (Files.isSymbolicLink(gitignore)
                || !Files.isRegularFile(gitignore, LinkOption.NOFOLLOW_LINKS)) {
            throw new java.io.IOException("Refusing to modify non-regular .gitignore");
        }
        String content = Files.readString(gitignore);
        if (content.lines().anyMatch(l -> l.trim().equals(IndexPaths.DIRECTORY) || l.trim().equals(IndexPaths.DIRECTORY + "/"))) {
            return;
        }
        String suffix = content.endsWith("\n") || content.isEmpty() ? "" : System.lineSeparator();
        Files.writeString(gitignore, suffix + IndexPaths.DIRECTORY + "/" + System.lineSeparator(),
                java.nio.file.StandardOpenOption.APPEND);
    }
}
