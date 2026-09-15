package com.repopilot.parse;

public record ParameterDeclaration(String name, String type, boolean varargs, SourceLocation location) {

    /** Erasure used for symbol identity (array dimensions preserved, varargs treated as array). */
    public String erasedType() {
        return Types.parameterErasure(type, varargs);
    }
}
