package com.repopilot.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 20: the commands work together on a real directory. */
class CliEndToEndTest {

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

    private Path repo(Path dir) throws Exception {
        Path pkg = dir.resolve("src/main/java/demo");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Token.java"), "package demo; public class Token {}");
        Files.writeString(pkg.resolve("Validator.java"), """
                package demo;
                public class Validator {
                  public boolean validate(Token token) { return token != null; }
                }
                """);
        Files.writeString(pkg.resolve("Service.java"), """
                package demo;
                public class Service {
                  private final Validator validator = new Validator();
                  public boolean check(Token token) { return validator.validate(token); }
                }
                """);
        return dir;
    }

    @Test
    void fullWorkflow(@TempDir Path dir) throws Exception {
        repo(dir);
        String[] revisions = createVerifiedRevisions(dir);
        String path = dir.toString();

        assertEquals(ExitCodes.NOT_INDEXED, run("impact", "Validator.validate", "-r", path).exitCode());
        assertEquals(ExitCodes.NOT_INDEXED, run("status", "-r", path).exitCode());

        Run index = run("index", "-r", path);
        assertEquals(ExitCodes.OK, index.exitCode(), index.err());
        assertTrue(index.out().contains("Symbols:"));

        Run status = run("status", "-r", path);
        assertEquals(ExitCodes.OK, status.exitCode());
        assertTrue(status.out().contains("Edges by type"));
        assertTrue(status.out().contains("CALLS"));
        assertTrue(status.out().contains("REPOSITORY OVERVIEW"));
        assertTrue(status.out().contains("ANALYSIS COVERAGE"));

        Run impact = run("impact", "demo.Validator#validate(Token)", "-r", path, "--explain");
        assertEquals(ExitCodes.OK, impact.exitCode(), impact.err());
        assertTrue(impact.out().contains("DIRECTLY MODIFIED"));
        assertTrue(impact.out().contains("src/main/java/demo/Validator.java"));
        assertTrue(impact.out().contains("Service.check(Token)"));
        assertTrue(impact.out().contains("INDIRECTLY AFFECTED - Radius 1"));
        assertFalse(impact.out().contains("\nAFFECTED - Radius"));
        assertTrue(impact.out().contains("path confidence 98%"),
                "field-type receiver resolution is intentionally capped at 98% confidence");
        assertTrue(impact.out().contains("RESOLVED/"));
        assertTrue(impact.out().contains("evidence:"));
        assertTrue(impact.out().contains("changed target: Validator.validate(Token)"));
        assertTrue(impact.out().contains("Impact:"));
        assertTrue(impact.out().contains("Resolved-path strength:"));

        Run unknown = run("impact", "demo.Nope#missing()", "-r", path);
        assertEquals(ExitCodes.ERROR, unknown.exitCode());
        assertTrue(unknown.err().contains("no indexed symbol matches"));

        Run ambiguous = run("impact", "validate", "-r", path);
        assertEquals(ExitCodes.OK, ambiguous.exitCode(), "a unique simple name is not ambiguous");

        Run diff = run("diff", "make token validation stricter", "-r", path, "--threshold", "0.99");
        assertEquals(ExitCodes.AMBIGUOUS, diff.exitCode());
        assertTrue(diff.err().contains("Ambiguous target"));
        assertTrue(diff.err().contains("Possible targets"));

        Run plan = run("plan", "validate token", "-r", path, "--mode", "local", "--threshold", "0.50");
        assertEquals(ExitCodes.OK, plan.exitCode(), plan.err());
        assertTrue(plan.out().contains("CONTRIBUTION PLAN"), plan.out());
        assertTrue(plan.out().contains("START HERE"), plan.out());
        assertTrue(plan.out().contains("LIKELY MODIFY"), plan.out());

        Run badThreshold = run("diff", "anything", "-r", path, "--threshold", "5");
        assertEquals(ExitCodes.USAGE, badThreshold.exitCode());

        Path diffFile = dir.resolve("change.diff");
        Files.writeString(diffFile, """
                diff --git a/src/main/java/demo/Validator.java b/src/main/java/demo/Validator.java
                --- a/src/main/java/demo/Validator.java
                +++ b/src/main/java/demo/Validator.java
                @@ -2,4 +2,4 @@ package demo;
                 public class Validator {
                -  public boolean validate(Token token) { return token != null; }
                +  public boolean validate(Token token) { return token != null && token.hashCode() != 0; }
                 }
                """);
        Run pr = run("pr", "-r", path, "--diff-file", diffFile.toString(),
                "--base-sha", revisions[0], "--head-sha", revisions[1]);
        assertEquals(ExitCodes.OK, pr.exitCode(), pr.err());
        assertTrue(pr.out().contains("Validator.validate(Token)"));
        assertTrue(pr.out().contains("Service.check(Token)"));

        assertEquals(ExitCodes.USAGE, run("pr", "-r", path).exitCode(), "pr needs a source");
        assertEquals(ExitCodes.USAGE, run("pr", "-r", path, "--diff-file", "nope.diff").exitCode());

        Run clean = run("clean", "-r", path);
        assertEquals(ExitCodes.OK, clean.exitCode());
        assertFalse(Files.exists(dir.resolve(".repo-pilot")));
        assertEquals(ExitCodes.OK, run("clean", "-r", path).exitCode(), "clean is idempotent");
    }

    private String[] createVerifiedRevisions(Path dir) throws Exception {
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.init()
                .setDirectory(dir.toFile()).call()) {
            git.add().addFilepattern(".").call();
            String base = git.commit().setMessage("base")
                    .setAuthor("Repo Pilot Test", "test@example.com").call().getId().name();
            Files.writeString(dir.resolve("src/main/java/demo/Validator.java"), """
                    package demo;
                    public class Validator {
                      public boolean validate(Token token) { return token != null && token.hashCode() != 0; }
                    }
                    """);
            git.add().addFilepattern("src/main/java/demo/Validator.java").call();
            String head = git.commit().setMessage("head")
                    .setAuthor("Repo Pilot Test", "test@example.com").call().getId().name();
            return new String[]{base, head};
        }
    }

    @Test
    void benchmarkCommandRunsTheBundledCases() {
        Path benchmarks = Path.of("benchmarks");
        if (!Files.isDirectory(benchmarks)) {
            return;
        }
        Run run = run("benchmark", "--dir", benchmarks.toString());
        assertEquals(ExitCodes.OK, run.exitCode(), run.out() + run.err());
        assertTrue(run.out().contains("Macro F1"));
        assertTrue(run.out().contains("F1 1.00"), run.out());
    }

    @Test
    void benchmarkWithoutCasesIsAUsageError(@TempDir Path dir) {
        assertEquals(ExitCodes.USAGE, run("benchmark", "--dir", dir.toString()).exitCode());
    }

    @Test
    void initCreatesGitignoreAndIsIdempotent(@TempDir Path dir) throws Exception {
        Files.createDirectory(dir.resolve(".git"));

        Run first = run("init", "-r", dir.toString());
        assertEquals(ExitCodes.OK, first.exitCode(), first.err());
        assertEquals(".repo-pilot/", Files.readString(dir.resolve(".gitignore")).trim());

        Run second = run("init", "-r", dir.toString());
        assertEquals(ExitCodes.OK, second.exitCode(), second.err());
        assertEquals(1, Files.readAllLines(dir.resolve(".gitignore")).stream()
                .filter(line -> line.trim().equals(".repo-pilot/")).count());
    }
}
