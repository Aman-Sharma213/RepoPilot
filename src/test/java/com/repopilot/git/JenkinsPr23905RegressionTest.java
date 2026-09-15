package com.repopilot.git;

import com.repopilot.index.IndexingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Mandatory regression for the incorrect Jenkins#getPlugins(Class) Phase 10 result. */
class JenkinsPr23905RegressionTest {
    @Test
    void mapsActualMembersWithoutClassFallbackAndRetainsResources(@TempDir Path dir) throws Exception {
        Path base = Files.createDirectories(dir.resolve("base"));
        Path head = Files.createDirectories(dir.resolve("head"));
        String oldJava = """
                package jenkins.model;
                class Jenkins {
                  private String systemMessage;
                  String getSystemMessage() { return systemMessage; }
                  Object getPlugins(Class type) { return null; }
                  synchronized void doConfigSubmit(Object req, Object rsp) {
                    systemMessage = "old";
                  }
                  void save() {}
                }
                """;
        String newJava = """
                package jenkins.model;
                class Jenkins {
                  private String systemMessage;
                  private String systemMessageSeverity = "INFO";
                  String getSystemMessage() { return systemMessage; }
                  Object getPlugins(Class type) { return null; }
                  synchronized String getSystemMessageSeverity() { return systemMessageSeverity; }
                  synchronized void setSystemMessageSeverity(String value) { systemMessageSeverity = value; save(); }
                  synchronized void doConfigSubmit(Object req, Object rsp) {
                    systemMessage = "old";
                    systemMessageSeverity = "WARNING";
                  }
                  void save() {}
                }
                """;
        write(base, "core/src/main/java/jenkins/model/Jenkins.java", oldJava);
        write(head, "core/src/main/java/jenkins/model/Jenkins.java", newJava);
        String[] resources = {
                "core/src/main/resources/hudson/model/View/index.jelly",
                "core/src/main/resources/hudson/model/View/new-view-page.jelly",
                "core/src/main/resources/jenkins/model/Jenkins/configure.jelly",
                "core/src/main/resources/lib/layout/layout.jelly",
                "core/src/main/resources/lib/layout/main-panel.jelly",
                "src/main/scss/components/_index.scss", "src/main/scss/components/_system-message.scss",
                "src/main/scss/pages/_dashboard.scss",
                "war/src/main/resources/images/symbols/construct-outline.svg",
                "war/src/main/resources/images/symbols/warning-outline.svg"};
        for (String resource : resources) {
            write(base, resource, "old\n");
            write(head, resource, resource.endsWith(".jelly")
                    ? "${app.systemMessageSeverity} <l:icon src=\"symbol-warning-outline\"/>\n"
                    : resource.endsWith(".scss") ? "@use \"system-message\"; .system-message {}\n" : "<svg id=\"icon\"/>\n");
        }
        // Generate a real Git-style diff through a local fixture repository in the executable
        // integration test; here the mapper contract is exercised with the same verified snapshots.
        String diffText = fixtureDiff(resources);
        var provenance = new RevisionProvenance("fixture", "base", "base", "tree-base",
                RevisionSource.LOCAL_GIT_COMMIT, true, "test fixture");
        var headProvenance = new RevisionProvenance("fixture", "head", "head", "tree-head",
                RevisionSource.LOCAL_GIT_COMMIT, true, "test fixture");
        try (var baseSnapshot = new RevisionSnapshot(base, provenance, new IndexingService().index(base));
             var headSnapshot = new RevisionSnapshot(head, headProvenance, new IndexingService().index(head))) {
            var result = new RevisionAwareChangedTargetLocator().locate(
                    new UnifiedDiffParser().parse(diffText), baseSnapshot, headSnapshot);
            Set<String> ids = result.targets().stream().flatMap(t ->
                    java.util.stream.Stream.of(t.baseSymbol(), t.headSymbol())).filter(java.util.Objects::nonNull)
                    .map(Object::toString).collect(Collectors.toSet());
            assertTrue(ids.contains("jenkins.model.Jenkins#systemMessageSeverity"));
            assertTrue(ids.contains("jenkins.model.Jenkins#getSystemMessageSeverity()"));
            assertTrue(ids.contains("jenkins.model.Jenkins#setSystemMessageSeverity(String)"));
            assertTrue(ids.stream().anyMatch(s -> s.startsWith("jenkins.model.Jenkins#doConfigSubmit(")));
            assertFalse(ids.stream().anyMatch(s -> s.contains("getPlugins")));
            assertFalse(ids.contains("jenkins.model.Jenkins"), "uncertain hunks must not fall back to the class");
            assertEquals(11, result.targets().stream().map(t -> t.newPath() == null ? t.oldPath() : t.newPath())
                    .distinct().count());
            assertTrue(result.targets().stream().filter(t -> !"java".equals(t.language()))
                    .allMatch(t -> t.status() == TargetMappingStatus.FILE_LEVEL_CHANGE));
            assertTrue(result.targets().stream().filter(t -> t.status() == TargetMappingStatus.EXACT)
                    .allMatch(t -> t.evidence() != null && t.baseRevision().verified() && t.headRevision().verified()));
            assertTrue(result.targets().stream().flatMap(t -> t.observations().stream())
                    .anyMatch(o -> o.kind().equals("JELLY_PROPERTY_REFERENCE")));
            assertTrue(result.targets().stream().flatMap(t -> t.observations().stream())
                    .anyMatch(o -> o.kind().equals("STYLESHEET_IMPORT")));
            assertTrue(result.targets().stream().flatMap(t -> t.observations().stream())
                    .anyMatch(o -> o.kind().equals("SVG_SYMBOL")));
        }
    }

    private static void write(Path root, String relative, String text) throws Exception {
        Path file = root.resolve(relative); Files.createDirectories(file.getParent()); Files.writeString(file, text);
    }

    private static String fixtureDiff(String[] resources) {
        StringBuilder d = new StringBuilder("""
                diff --git a/core/src/main/java/jenkins/model/Jenkins.java b/core/src/main/java/jenkins/model/Jenkins.java
                --- a/core/src/main/java/jenkins/model/Jenkins.java
                +++ b/core/src/main/java/jenkins/model/Jenkins.java
                @@ -1,10 +1,13 @@
                 package jenkins.model;
                 class Jenkins {
                   private String systemMessage;
                +  private String systemMessageSeverity = "INFO";
                   String getSystemMessage() { return systemMessage; }
                   Object getPlugins(Class type) { return null; }
                +  synchronized String getSystemMessageSeverity() { return systemMessageSeverity; }
                +  synchronized void setSystemMessageSeverity(String value) { systemMessageSeverity = value; save(); }
                   synchronized void doConfigSubmit(Object req, Object rsp) {
                     systemMessage = "old";
                +    systemMessageSeverity = "WARNING";
                   }
                   void save() {}
                 }
                """);
        for (String r : resources) d.append("diff --git a/").append(r).append(" b/").append(r)
                .append("\n--- a/").append(r).append("\n+++ b/").append(r)
                .append("\n@@ -1,1 +1,1 @@\n-old\n+")
                .append(r.endsWith(".jelly") ? "${app.systemMessageSeverity} <l:icon src=\"symbol-warning-outline\"/>"
                        : r.endsWith(".scss") ? "@use \"system-message\"; .system-message {}" : "<svg id=\"icon\"/>")
                .append('\n');
        return d.toString();
    }
}
