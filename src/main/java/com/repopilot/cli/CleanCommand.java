package com.repopilot.cli;

import com.repopilot.persist.IndexPaths;
import com.repopilot.persist.IndexStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "clean", description = "Delete the .repo-pilot index directory.")
public class CleanCommand implements Callable<Integer> {

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
            IndexPaths paths = new IndexPaths(root);
            boolean existed = java.nio.file.Files.exists(paths.directory());
            new IndexStore().clean(root);
            out.println(existed ? "Removed " + paths.directory() : "Nothing to clean in " + root);
            out.flush();
            return ExitCodes.OK;
        } catch (Exception e) {
            return CommandSupport.handle(e, err);
        }
    }
}
