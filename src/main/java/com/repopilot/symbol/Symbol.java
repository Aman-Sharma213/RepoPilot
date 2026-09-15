package com.repopilot.symbol;

import com.repopilot.parse.SourceLocation;

import java.util.List;
import java.util.Set;

/**
 * One declared program element.
 *
 * @param type       return type for methods, declared type for fields and parameters, null for types
 * @param superTypes raw supertype names exactly as written in the source (resolved in Phase 5)
 */
public record Symbol(SymbolId id, SymbolKind kind, String simpleName, String packageName, String ownerFqn,
                     List<String> parameterTypes, String type, List<String> superTypes, Set<String> modifiers,
                     List<String> annotations, List<String> typeParameters, SourceLocation location) {

    public Symbol {
        parameterTypes = List.copyOf(parameterTypes);
        superTypes = List.copyOf(superTypes);
        modifiers = Set.copyOf(modifiers);
        annotations = List.copyOf(annotations);
        typeParameters = List.copyOf(typeParameters);
    }

    public String file() {
        return location.file();
    }

    public boolean isStatic() {
        return modifiers.contains("static");
    }

    public boolean isType() {
        return kind.isType();
    }

    public boolean hasAnnotation(String simpleAnnotationName) {
        return annotations.stream().anyMatch(a -> a.equals(simpleAnnotationName)
                || a.endsWith("." + simpleAnnotationName));
    }

    /** True when this symbol is declared inside a test source tree. */
    public boolean isTestSymbol() {
        String f = location.file();
        return f.contains("src/test/") || f.contains("/test/java/") || simpleName.endsWith("Test")
                || simpleName.endsWith("Tests") || simpleName.endsWith("IT");
    }
}
