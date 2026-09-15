package com.repopilot.parse;

import java.util.List;
import java.util.Set;

public record FieldDeclaration(String name, String type, Set<String> modifiers, List<AnnotationUse> annotations,
                               String initializerType, SourceLocation location) {

    public FieldDeclaration {
        modifiers = Set.copyOf(modifiers);
        annotations = List.copyOf(annotations);
    }

    public String erasedType() {
        return Types.erase(type);
    }

    public boolean isStatic() {
        return modifiers.contains("static");
    }
}
