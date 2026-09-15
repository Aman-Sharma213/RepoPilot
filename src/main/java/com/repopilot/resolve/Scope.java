package com.repopilot.resolve;

import java.util.LinkedHashMap;
import java.util.Map;

/** Names visible at a call site: parameters, locals, fields and type variables. */
public record Scope(String file, String enclosingTypeFqn, Map<String, Variable> variables,
                    Map<String, String> typeVariables, boolean staticContext) {

    public enum VariableKind {PARAMETER, LOCAL, FIELD}

    /** @param bound the declared type as written; for type variables it is the upper bound or null */
    public record Variable(String name, String declaredType, VariableKind kind) {
    }

    public Scope {
        variables = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        // A type variable without a declared bound maps to null, so this map may hold null values.
        typeVariables = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(typeVariables));
    }

    public static Scope of(String file, String enclosingTypeFqn) {
        return new Scope(file, enclosingTypeFqn, Map.of(), Map.of(), false);
    }

    public Scope with(Map<String, Variable> more) {
        Map<String, Variable> merged = new LinkedHashMap<>(variables);
        merged.putAll(more);
        return new Scope(file, enclosingTypeFqn, merged, typeVariables, staticContext);
    }

    public ResolutionLevel levelFor(String variableName) {
        Variable variable = variables.get(variableName);
        if (variable == null) {
            return ResolutionLevel.NONE;
        }
        return switch (variable.kind()) {
            case FIELD -> ResolutionLevel.L4_FIELD_TYPE;
            case PARAMETER, LOCAL -> ResolutionLevel.L5_PARAMETER_TYPE;
        };
    }
}
