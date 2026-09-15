package com.repopilot.parse;

import java.util.List;
import java.util.Map;

/**
 * A flat, uniform view of one extracted syntax element. Every element carries file, line range,
 * name and kind, which is the Phase 3 contract of the project.
 */
public record SyntaxNode(SyntaxKind kind, String name, SourceLocation location, Map<String, String> attributes,
                         List<SyntaxNode> children) {

    public SyntaxNode {
        attributes = Map.copyOf(attributes);
        children = List.copyOf(children);
    }

    public static SyntaxNode of(SyntaxKind kind, String name, SourceLocation location) {
        return new SyntaxNode(kind, name, location, Map.of(), List.of());
    }

    public String attribute(String key) {
        return attributes.get(key);
    }
}
