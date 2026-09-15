package com.repopilot.incremental;

import com.repopilot.index.IndexingService;
import com.repopilot.index.ParseCoordinator;
import com.repopilot.index.RepoIndex;
import com.repopilot.scan.RepositoryScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IncrementalIndexingTest {

    private IncrementalIndexer indexer() {
        return new IncrementalIndexer(new RepositoryScanner(), ParseCoordinator.defaultCoordinator(),
                new IndexingService());
    }

    private Path repo(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/A.java"), "package p; class A { void a() { new B().b(); } }");
        Files.writeString(dir.resolve("src/B.java"), "package p; class B { void b() {} }");
        Files.writeString(dir.resolve("src/C.java"), "package p; class C { void c() {} }");
        return dir;
    }

    @Test
    void onlyChangedFilesAreReparsed(@TempDir Path dir) throws Exception {
        repo(dir);
        ParseCache cache = new ParseCache();
        IncrementalIndexer.Outcome first = indexer().update(dir, Map.of(), cache);
        assertEquals(List.of("src/A.java", "src/B.java", "src/C.java"), first.reparsedFiles());
        assertTrue(first.reusedFiles().isEmpty());

        Thread.sleep(10);
        Files.writeString(dir.resolve("src/B.java"), "package p; class B { void b() {} void extra() {} }");

        IncrementalIndexer.Outcome second = indexer().update(dir, first.index().files(), cache);
        assertEquals(List.of("src/B.java"), second.reparsedFiles(), "only B changed");
        assertEquals(List.of("src/A.java", "src/C.java"), second.reusedFiles());
        assertEquals(List.of("src/B.java"), second.changes().paths(ChangeType.MODIFIED));
        assertTrue(second.index().symbols().contains(new com.repopilot.symbol.SymbolId("p.B#extra()")));
        assertEquals("incremental", second.index().stats().mode());
    }

    @Test
    void addedDeletedAndRenamedFilesAreDetected(@TempDir Path dir) throws Exception {
        repo(dir);
        ParseCache cache = new ParseCache();
        RepoIndex first = indexer().update(dir, Map.of(), cache).index();

        Files.writeString(dir.resolve("src/D.java"), "package p; class D {}");
        Files.delete(dir.resolve("src/C.java"));
        Files.move(dir.resolve("src/A.java"), dir.resolve("src/Renamed.java"));

        ChangeSet changes = indexer().update(dir, first.files(), cache).changes();
        assertEquals(List.of("src/D.java"), changes.paths(ChangeType.ADDED));
        assertEquals(List.of("src/C.java"), changes.paths(ChangeType.DELETED));
        assertEquals(List.of("src/Renamed.java"), changes.paths(ChangeType.RENAMED));
        FileChange rename = changes.changes().stream()
                .filter(c -> c.type() == ChangeType.RENAMED).findFirst().orElseThrow();
        assertEquals("src/A.java", rename.previousPath());
    }

    @Test
    void touchingAFileWithoutChangingContentDoesNotReparse(@TempDir Path dir) throws Exception {
        repo(dir);
        ParseCache cache = new ParseCache();
        RepoIndex first = indexer().update(dir, Map.of(), cache).index();
        Files.setLastModifiedTime(dir.resolve("src/A.java"),
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));
        IncrementalIndexer.Outcome second = indexer().update(dir, first.files(), cache);
        assertTrue(second.reparsedFiles().isEmpty(), "content hash proves nothing changed");
    }

    @Test
    void incrementalGraphEqualsFullGraph(@TempDir Path dir) throws Exception {
        repo(dir);
        ParseCache cache = new ParseCache();
        RepoIndex first = indexer().update(dir, Map.of(), cache).index();
        Thread.sleep(10);
        Files.writeString(dir.resolve("src/B.java"), "package p; class B { void b() { new C().c(); } }");
        RepoIndex incremental = indexer().update(dir, first.files(), cache).index();
        RepoIndex full = new IndexingService().index(dir);
        assertEquals(full.graph().digest(), incremental.graph().digest());
    }

    @Test
    void damagedParseCacheIsDiscardedNotTrusted(@TempDir Path dir) throws Exception {
        repo(dir);
        ParseCache cache = new ParseCache();
        indexer().update(dir, Map.of(), cache).index();
        cache.save(dir);
        Files.writeString(ParseCache.fileFor(dir), "not json at all");
        ParseCache reloaded = ParseCache.load(dir);
        assertEquals(0, reloaded.size());
    }
}
