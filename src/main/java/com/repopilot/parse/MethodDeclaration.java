package com.repopilot.parse;

import java.util.List;
import java.util.Set;

public record MethodDeclaration(String name, String returnType, List<ParameterDeclaration> parameters,
                                Set<String> modifiers, List<AnnotationUse> annotations, List<String> typeParameters,
                                List<MethodInvocation> invocations, List<ObjectCreation> objectCreations,
                                List<LocalVariable> locals, boolean constructor, boolean hasBody,
                                SourceLocation location) {

    public MethodDeclaration {
        parameters = List.copyOf(parameters);
        modifiers = Set.copyOf(modifiers);
        annotations = List.copyOf(annotations);
        typeParameters = List.copyOf(typeParameters);
        invocations = List.copyOf(invocations);
        objectCreations = List.copyOf(objectCreations);
        locals = List.copyOf(locals);
    }

    public boolean isStatic() {
        return modifiers.contains("static");
    }

    public boolean isAbstract() {
        return modifiers.contains("abstract") || !hasBody;
    }

    public int arity() {
        return parameters.size();
    }

    public boolean isVarargs() {
        return !parameters.isEmpty() && parameters.get(parameters.size() - 1).varargs();
    }
}
