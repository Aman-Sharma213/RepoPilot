package com.repopilot.index;

import com.repopilot.github.GitHubClient;
import com.repopilot.github.GitHubException;
import com.repopilot.github.PullRequestRef;
import com.repopilot.impact.ImpactEngine;
import com.repopilot.persist.IndexCorruptException;
import com.repopilot.persist.IndexPaths;
import com.repopilot.persist.IndexStore;
import com.repopilot.symbol.SymbolId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 17: the tool must fail gracefully, never silently and never with a stack trace. */
class FailureEngineeringTest {

    @Test
    void emptyRepositoryIndexesToAnEmptyGraph(@TempDir Path dir) throws Exception {
        RepoIndex index = new IndexingService().index(dir);
        assertEquals(0, index.stats().files());
        assertEquals(0, index.symbols().size());
        assertEquals(0, index.graph().edgeCount());
        assertNotNull(index.stats().digest());
    }

    @Test
    void malformedJavaIsIndexedPartiallyAndFlagged(@TempDir Path dir) throws Exception {
        // Error-recoverable damage: Tree-sitter still yields the surrounding declarations.
        Files.writeString(dir.resolve("Broken.java"), """
                package p;
                class Broken {
                  void ok() {}
                  void broken( {}
                }
                """);
        // Unrecoverable damage: the file has no complete declaration at all.
        Files.writeString(dir.resolve("Hopeless.java"), "package p; class Hopeless { void x( {");
        Files.writeString(dir.resolve("Fine.java"), "package p; class Fine { void m() {} }");
        RepoIndex index = new IndexingService().index(dir);
        assertEquals(2, index.stats().filesWithSyntaxErrors());
        assertTrue(index.symbols().contains(new SymbolId("p.Fine#m()")), "healthy files still index");
        assertTrue(index.symbols().contains(new SymbolId("p.Broken#ok()")),
                "declarations around a syntax error are still indexed");
        assertTrue(index.symbols().type("p.Hopeless").isEmpty(),
                "nothing is invented for a file without a complete declaration");
    }

    @Test
    void duplicateClassNamesInTheSamePackageDoNotCrash(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("one"));
        Files.createDirectories(dir.resolve("two"));
        Files.writeString(dir.resolve("one/Dup.java"), "package p; class Dup { void m() {} }");
        Files.writeString(dir.resolve("two/Dup.java"), "package p; class Dup { void m() {} }");
        RepoIndex index = new IndexingService().index(dir);
        assertEquals(1, index.symbols().typesNamed("Dup").size(), "the last declaration wins deterministically");
        assertNotNull(index.stats().digest());
    }

    @Test
    void brokenImportsAndMissingTypesBecomeUnresolvedReferences(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("A.java"), """
                package p;
                import com.nowhere.Missing;
                class A {
                  private Missing missing;
                  void go() { missing.doThing(); }
                }
                """);
        RepoIndex index = new IndexingService().index(dir);
        assertTrue(index.stats().unresolvedReferences() > 0);
        assertTrue(index.graph().unresolvedReferences().stream()
                .allMatch(u -> u.reason() != null && !u.reason().isBlank()),
                "every unresolved reference must explain itself");
    }

    @Test
    void reflectionAndLambdasAreReportedAsUnresolvedNotInvented(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("R.java"), """
                package p;
                class R {
                  void go() throws Exception {
                    Class<?> type = Class.forName("p.R");
                    type.getMethod("go").invoke(null);
                    Runnable runnable = () -> go();
                    runnable.run();
                  }
                }
                """);
        RepoIndex index = new IndexingService().index(dir);
        assertTrue(index.graph().unresolvedReferences().stream()
                .anyMatch(u -> u.rawText().contains("invoke") || u.rawText().contains("getMethod")));
        assertTrue(index.graph().edges().stream().noneMatch(e -> e.target().value().contains("Class")));
    }

    @Test
    void deletedFileAfterIndexingDoesNotBreakImpactAnalysis(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("A.java"), "package p; class A { void a() { new B().b(); } }");
        Files.writeString(dir.resolve("B.java"), "package p; class B { void b() {} }");
        RepoIndex index = new IndexingService().index(dir);
        new IndexStore().save(index);
        Files.delete(dir.resolve("B.java"));
        RepoIndex loaded = new IndexStore().load(dir);
        var result = new ImpactEngine(loaded.graph())
                .analyse(List.of(new SymbolId("p.B#b()")), ImpactEngine.Options.defaults());
        assertFalse(result.affected().isEmpty(), "the stored index is still usable until re-indexed");
    }

    @Test
    void corruptIndexProducesAnActionableMessage(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("A.java"), "package p; class A {}");
        IndexStore store = new IndexStore();
        store.save(new IndexingService().index(dir));
        Files.writeString(new IndexPaths(dir).edgesFile(), "{\"edges\": [ {\"source\": \"nope\"} ]}");
        IndexCorruptException e = assertThrows(IndexCorruptException.class, () -> store.load(dir));
        assertTrue(e.getMessage().contains("repo-pilot"));
    }

    @Test
    void interruptedIndexingLeavesNoHalfWrittenIndex(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("A.java"), "package p; class A {}");
        IndexStore store = new IndexStore();
        store.save(new IndexingService().index(dir));
        // Simulate a crash during the next save: a stale temporary file must not be picked up.
        Files.writeString(new IndexPaths(dir).symbolsFile().resolveSibling("symbols.json.tmp"), "partial");
        assertDoesNotThrow(() -> store.load(dir));
    }

    @Test
    void networkFailureToGitHubIsReportedClearly() {
        GitHubClient client = new GitHubClient(null, "http://127.0.0.1:1", Duration.ofMillis(300));
        GitHubException e = assertThrows(GitHubException.class,
                () -> client.fetchPullRequest(new PullRequestRef("apache", "kafka", 1402)));
        assertTrue(e.getMessage().contains("Cannot reach GitHub") || e.getMessage().contains("timed out"));
    }

    @Test
    void impactOnAHugeSyntheticRepositoryStaysWithinTimeBudget(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("src/p"));
        int classes = 400;
        for (int i = 0; i < classes; i++) {
            Files.writeString(dir.resolve("src/p/C" + i + ".java"), """
                    package p;
                    class C%d {
                      private C%d next = new C%d();
                      void run() { next.run(); }
                    }
                    """.formatted(i, (i + 1) % classes, (i + 1) % classes));
        }
        RepoIndex index = new IndexingService().index(dir);
        assertEquals(classes, index.stats().files());
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var result = new ImpactEngine(index.graph())
                    .analyse(List.of(new SymbolId("p.C0#run()")), ImpactEngine.Options.defaults());
            assertFalse(result.affected().isEmpty());
        });
    }
}
