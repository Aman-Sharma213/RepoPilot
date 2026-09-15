package com.repopilot.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.util.concurrent.Callable;

@Command(
        name = "repo-pilot",
        header = "Repo Pilot - evidence-backed contribution planning for Java repositories",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        synopsisSubcommandLabel = "<command>",
        subcommands = {
                InitCommand.class,
                IndexCommand.class,
                StatusCommand.class,
                ImpactCommand.class,
                PrCommand.class,
                DiffCommand.class,
                BenchmarkCommand.class,
                CleanCommand.class
        })
public class RootCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getOut());
        return ExitCodes.OK;
    }

    /** Entry point used by tests and by the Spring runner. */
    public static int execute(String... args) {
        return new CommandLine(new RootCommand()).execute(args);
    }
}
