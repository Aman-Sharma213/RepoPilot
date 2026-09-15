package com.repopilot.persist;

import com.repopilot.index.RepoIndex;
import com.repopilot.index.TestRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IndexStoreTest {

    private RepoIndex sample(Path dir) throws Exception {
        return TestRepo.of()
                .file("src/a/Api.java", "package a; public interface Api { void run(); }")
                .file("src/a/Impl.java", """
                        package a;
                        public class Impl implements Api {
                          private final Dep dep = new Dep();
                          public void run() { dep.work(); }
                        }
                        """)
                .file("src/a/Dep.java", "package a; class Dep { void work() {} }")
                .writeTo(dir);
    }

    @Test
    void savedIndexIsLoadedIdentically(@TempDir Path dir) throws Exception {
        RepoIndex original = sample(dir);
        IndexStore store = new IndexStore();
        store.save(original);

        assertTrue(new IndexPaths(dir).hasIndex());
        RepoIndex loaded = store.load(dir);
        assertEquals(original.symbols().size(), loaded.symbols().size());
        assertEquals(original.graph().nodeCount(), loaded.graph().nodeCount());
        assertEquals(original.graph().edgeCount(), loaded.graph().edgeCount());
        assertEquals(original.graph().unresolvedReferences().size(),
                loaded.graph().unresolvedReferences().size());
        assertEquals(original.graph().digest(), loaded.graph().digest(),
                "a round trip through disk must not change the graph");
        assertEquals(original.files().keySet(), loaded.files().keySet());
    }

    @Test
    void loadingWithoutAnIndexFails(@TempDir Path dir) {
        assertThrows(IndexCorruptException.class, () -> new IndexStore().load(dir));
    }

    @Test
    void corruptSymbolsFileIsReportedNotIgnored(@TempDir Path dir) throws Exception {
        IndexStore store = new IndexStore();
        store.save(sample(dir));
        Files.writeString(new IndexPaths(dir).symbolsFile(), "{ this is not json");
        IndexCorruptException e = assertThrows(IndexCorruptException.class, () -> store.load(dir));
        assertTrue(e.getMessage().contains("unreadable"));
    }

    @Test
    void unsupportedFormatVersionIsRejected(@TempDir Path dir) throws Exception {
        IndexStore store = new IndexStore();
        store.save(sample(dir));
        Files.writeString(new IndexPaths(dir).versionFile(), "9999");
        assertTrue(assertThrows(IndexCorruptException.class, () -> store.load(dir))
                .getMessage().contains("not supported"));
    }

    @Test
    void cleanRemovesTheWorkspace(@TempDir Path dir) throws Exception {
        IndexStore store = new IndexStore();
        store.save(sample(dir));
        store.clean(dir);
        assertFalse(Files.exists(new IndexPaths(dir).directory()));
        store.clean(dir); // idempotent
    }
}
