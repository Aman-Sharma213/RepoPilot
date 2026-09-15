package com.repopilot.analysis;

import com.repopilot.graph.UnresolvedReference;
import com.repopilot.impact.UnresolvedImpact;
import com.repopilot.resolve.ResolutionStatus;

import java.util.Locale;
import java.util.Set;

/**
 * Separates harmless external gaps from project-local gaps that can hide affected files.
 * This is deliberately conservative: anything not recognisably external remains project-local.
 */
public final class UnresolvedClassifier {

    private static final Set<String> JDK_TYPES = Set.of(
            "arraylist", "arrays", "atomicinteger", "boolean", "byte", "class", "collections",
            "countdownlatch", "double", "exception", "executorservice", "float", "future",
            "hashmap", "hashset", "identityhashmap", "integer", "iterable", "list", "long",
            "map", "math", "object", "optional", "override", "random", "runnable", "runtimeexception",
            "set", "short", "string", "stringbuilder", "thread", "throwable", "timeunit", "void");

    private static final Set<String> KNOWN_DEPENDENCY_MARKERS = Set.of(
            "logger", "slf4j", "junit", "easymock", "powermock", "whitebox", "mockito",
            "iexpectationsetters", "ianswer", "capture");

    private UnresolvedClassifier() {
    }

    public static UnresolvedCategory classify(UnresolvedReference reference) {
        return classify(reference.status(), reference.rawText(), reference.reason());
    }

    public static UnresolvedCategory classify(UnresolvedImpact impact) {
        return classify(impact.status(), impact.rawText(), impact.reason());
    }

    static UnresolvedCategory classify(ResolutionStatus status, String rawText, String reason) {
        if (status == ResolutionStatus.DYNAMIC) {
            return UnresolvedCategory.DYNAMIC;
        }
        String evidence = ((rawText == null ? "" : rawText) + " " + (reason == null ? "" : reason))
                .toLowerCase(Locale.ROOT);
        if (evidence.contains("java.") || evidence.contains("javax.") || evidence.contains("jdk.")) {
            return UnresolvedCategory.JDK_EXTERNAL;
        }
        for (String type : JDK_TYPES) {
            if (containsWord(evidence, type)) {
                return UnresolvedCategory.JDK_EXTERNAL;
            }
        }
        if ((evidence.contains("imported type org.") || evidence.contains("imported type com.")
                || evidence.contains("outside the indexed sources"))
                && KNOWN_DEPENDENCY_MARKERS.stream().anyMatch(evidence::contains)) {
            return UnresolvedCategory.DEPENDENCY_EXTERNAL;
        }
        if (KNOWN_DEPENDENCY_MARKERS.stream().anyMatch(evidence::contains)) {
            return UnresolvedCategory.DEPENDENCY_EXTERNAL;
        }
        return UnresolvedCategory.PROJECT_LOCAL_UNRESOLVED;
    }

    private static boolean containsWord(String text, String word) {
        return text.matches(".*(^|[^a-z0-9_])" + java.util.regex.Pattern.quote(word)
                + "([^a-z0-9_]|$).*");
    }
}
