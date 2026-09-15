package com.repopilot.git;

import com.repopilot.index.TestRepo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffAnalysisTest {

    private final UnifiedDiffParser parser = new UnifiedDiffParser();

    @Test
    void parsesModifiedFileHunks() {
        List<FileDiff> diffs = parser.parse("""
                diff --git a/src/A.java b/src/A.java
                index 111..222 100644
                --- a/src/A.java
                +++ b/src/A.java
                @@ -10,7 +10,8 @@ class A {
                   void keep() {}
                -  void old() {}
                +  void changed() {}
                +  void added() {}
                   void tail() {}
                """);
        assertEquals(1, diffs.size());
        FileDiff diff = diffs.get(0);
        assertEquals(DiffChangeType.MODIFIED, diff.changeType());
        assertEquals("src/A.java", diff.path());
        assertEquals(List.of(new FileDiff.LineRange(11, 12)), diff.changedLines());
        assertEquals(List.of(new FileDiff.LineRange(11, 11)), diff.removedLines());
        assertEquals(1, diff.hunks().size());
        assertEquals("@@ -10,7 +10,8 @@ class A {", diff.hunks().get(0).header());
        assertEquals(List.of("  void changed() {}", "  void added() {}"), diff.hunks().get(0).addedLines());
        assertEquals(List.of("  void old() {}"), diff.hunks().get(0).removedLines());
    }

    @Test
    void parsesAddedDeletedRenamedAndBinaryFiles() {
        List<FileDiff> diffs = parser.parse("""
                diff --git a/src/New.java b/src/New.java
                new file mode 100644
                --- /dev/null
                +++ b/src/New.java
                @@ -0,0 +1,2 @@
                +class New {}
                +
                diff --git a/src/Gone.java b/src/Gone.java
                deleted file mode 100644
                --- a/src/Gone.java
                +++ /dev/null
                @@ -1,1 +0,0 @@
                -class Gone {}
                diff --git a/src/Old.java b/src/Renamed.java
                similarity index 95%
                --- a/src/Old.java
                +++ b/src/Renamed.java
                @@ -1,2 +1,2 @@
                 class Renamed {
                -  int a;
                +  int b;
                diff --git a/logo.png b/logo.png
                index 333..444 100644
                Binary files a/logo.png and b/logo.png differ
                """);
        assertEquals(4, diffs.size());
        assertEquals(DiffChangeType.ADDED, diffs.get(0).changeType());
        assertEquals(DiffChangeType.DELETED, diffs.get(1).changeType());
        assertEquals(DiffChangeType.RENAMED, diffs.get(2).changeType());
        assertEquals("src/Old.java", diffs.get(2).oldPath());
        assertEquals(DiffChangeType.BINARY, diffs.get(3).changeType());
        assertTrue(diffs.get(3).binary());
    }

    @Test
    void emptyDiffYieldsNoFiles() {
        assertTrue(parser.parse("").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }

    @Test
    void preservesMultipleHunksAndNoNewlineMarker() {
        var file = parser.parse("""
                diff --git a/A.java b/A.java
                --- a/A.java
                +++ b/A.java
                @@ -1,1 +1,1 @@ first
                -old
                +new
                @@ -10,1 +10,1 @@ second
                -tail-old
                +tail-new
                \\ No newline at end of file
                """).get(0);
        assertEquals(2, file.hunks().size());
        assertEquals(10, file.hunks().get(1).oldStart());
        assertEquals(1, file.hunks().get(1).oldCount());
        assertEquals(10, file.hunks().get(1).newStart());
        assertEquals(1, file.hunks().get(1).newCount());
        assertTrue(file.hunks().get(1).lines().stream()
                .anyMatch(l -> l.kind() == DiffHunk.LineKind.NO_NEWLINE));
    }

    @Test
    void parsesQuotedPathsContainingSpaces() {
        var file = parser.parse("""
                diff --git "a/src/file name.java" "b/src/file name.java"
                --- "a/src/file name.java"
                +++ "b/src/file name.java"
                @@ -1,1 +1,1 @@
                -class Old {}
                +class New {}
                """).get(0);
        assertEquals("src/file name.java", file.oldPath());
        assertEquals("src/file name.java", file.newPath());
    }

    @Test
    void malformedHunkIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                diff --git a/A.java b/A.java
                --- a/A.java
                +++ b/A.java
                @@ nonsense @@
                +bad
                """));
    }

    @Test
    void changedLinesMapToTheMostSpecificSymbol() throws Exception {
        var index = TestRepo.of().file("src/a/Service.java", """
                package a;
                class Service {
                  private int counter;
                  void first() {
                    counter++;
                  }
                  void second() {
                    counter--;
                  }
                }
                """).index();
        List<FileDiff> diffs = List.of(new FileDiff("src/a/Service.java", "src/a/Service.java",
                DiffChangeType.MODIFIED, List.of(new FileDiff.LineRange(5, 5)), List.of(), false));
        var located = new ChangedSymbolLocator(index.symbols()).locate(diffs);
        assertEquals(List.of("a.Service#first()"), located.symbols().stream().map(s -> s.value()).toList());
    }

    @Test
    void nonJavaBinaryDeletedAndUnindexedFilesAreReportedWithReasons() throws Exception {
        var index = TestRepo.of().file("src/a/A.java", "package a; class A {}").index();
        List<FileDiff> diffs = List.of(
                new FileDiff("README.md", "README.md", DiffChangeType.MODIFIED,
                        List.of(new FileDiff.LineRange(1, 1)), List.of(), false),
                new FileDiff("logo.png", "logo.png", DiffChangeType.BINARY, List.of(), List.of(), true),
                new FileDiff(null, "src/a/Gone.java", DiffChangeType.DELETED, List.of(), List.of(), false),
                new FileDiff("src/b/Unknown.java", "src/b/Unknown.java", DiffChangeType.MODIFIED,
                        List.of(new FileDiff.LineRange(1, 3)), List.of(), false));
        var located = new ChangedSymbolLocator(index.symbols()).locate(diffs);
        assertTrue(located.symbols().isEmpty());
        assertEquals("not a Java source file", located.unmappedFiles().get("README.md"));
        assertEquals("binary file", located.unmappedFiles().get("logo.png"));
        assertTrue(located.unmappedFiles().get("src/a/Gone.java").contains("deleted"));
        assertTrue(located.unmappedFiles().get("src/b/Unknown.java").contains("no indexed symbols"));
    }

    @Test
    void commentOnlyChangesReportNoSymbol() throws Exception {
        var index = TestRepo.of().file("src/a/A.java", """
                // header comment
                package a;
                class A { void m() {} }
                """).index();
        var located = new ChangedSymbolLocator(index.symbols()).locate(List.of(
                new FileDiff("src/a/A.java", "src/a/A.java", DiffChangeType.MODIFIED,
                        List.of(new FileDiff.LineRange(1, 1)), List.of(), false)));
        assertTrue(located.symbols().isEmpty());
        assertTrue(located.unmappedFiles().get("src/a/A.java").contains("outside any declaration"));
    }
}
