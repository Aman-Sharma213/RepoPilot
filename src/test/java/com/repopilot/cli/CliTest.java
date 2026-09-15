package com.repopilot.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliTest {

    private record Run(int exitCode, String out, String err) {
    }

    private Run run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cmd = new CommandLine(new RootCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.setErr(new PrintWriter(err, true));
        int code = cmd.execute(args);
        return new Run(code, out.toString(), err.toString());
    }

    @Test
    void noArgumentsPrintsUsage() {
        Run r = run();
        assertEquals(ExitCodes.OK, r.exitCode());
        assertTrue(r.out().contains("repo-pilot"));
        assertTrue(r.out().contains("init"));
    }

    @Test
    void helpListsCommands() {
        Run r = run("--help");
        assertEquals(ExitCodes.OK, r.exitCode());
        assertTrue(r.out().contains("Commands:"));
    }

    @Test
    void versionIsPrinted() {
        Run r = run("--version");
        assertEquals(ExitCodes.OK, r.exitCode());
        assertTrue(r.out().startsWith("repo-pilot "));
    }

    @Test
    void unknownCommandIsUsageError() {
        Run r = run("frobnicate");
        assertEquals(ExitCodes.USAGE, r.exitCode());
        assertTrue(r.err().contains("Unmatched argument") || r.err().contains("Unknown"));
    }

    @Test
    void unknownOptionIsUsageError() {
        assertEquals(ExitCodes.USAGE, run("init", "--nope").exitCode());
    }

    @Test
    void initCreatesWorkspace(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/A.java"), "class A {}");
        Run r = run("init", "--repo-path", dir.toString());
        assertEquals(ExitCodes.OK, r.exitCode());
        assertTrue(Files.isDirectory(dir.resolve(".repo-pilot")));
        assertEquals("1", Files.readString(dir.resolve(".repo-pilot/version")).trim());
        assertTrue(r.out().contains("Java files discovered: 1"));
    }

    @Test
    void initOnMissingDirectoryFailsWithUsageError(@TempDir Path dir) {
        Run r = run("init", "--repo-path", dir.resolve("nope").toString());
        assertEquals(ExitCodes.USAGE, r.exitCode());
        assertTrue(r.err().contains("Not a directory"));
    }

    @Test
    void initAppendsWorkspaceToExistingGitignore(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve(".gitignore"), "target/\n");
        run("init", "--repo-path", dir.toString());
        assertTrue(Files.readString(dir.resolve(".gitignore")).contains(".repo-pilot/"));
        run("init", "--repo-path", dir.toString());
        long occurrences = Files.readString(dir.resolve(".gitignore")).lines()
                .filter(l -> l.trim().equals(".repo-pilot/")).count();
        assertEquals(1, occurrences, "gitignore entry must not be duplicated");
    }
}
