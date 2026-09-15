package com.repopilot.parse;

import java.util.List;
import java.util.Set;

/**
 * A declared type. {@code nestedName} is the dotted name relative to the compilation unit
 * ({@code Outer.Inner}), so the fully qualified name is {@code package + '.' + nestedName}.
 */
public record TypeDeclaration(String simpleName, String nestedName, SyntaxKind kind, String superclass,
                              List<String> interfaces, List<String> typeParameters, Set<String> modifiers,
                              List<AnnotationUse> annotations, List<MethodDeclaration> methods,
                              List<FieldDeclaration> fields, List<TypeDeclaration> nestedTypes,
                              List<String> enumConstants, List<ParameterDeclaration> recordComponents,
                              SourceLocation location) {

    public TypeDeclaration {
        interfaces = List.copyOf(interfaces);
        typeParameters = List.copyOf(typeParameters);
        modifiers = Set.copyOf(modifiers);
        annotations = List.copyOf(annotations);
        methods = List.copyOf(methods);
        fields = List.copyOf(fields);
        nestedTypes = List.copyOf(nestedTypes);
        enumConstants = List.copyOf(enumConstants);
        recordComponents = List.copyOf(recordComponents);
    }

    public boolean isInterface() {
        return kind == SyntaxKind.INTERFACE;
    }

    /** This type and every type nested inside it, depth first. */
    public List<TypeDeclaration> selfAndNested() {
        List<TypeDeclaration> all = new java.util.ArrayList<>();
        all.add(this);
        for (TypeDeclaration nested : nestedTypes) {
            all.addAll(nested.selfAndNested());
        }
        return List.copyOf(all);
    }
}
