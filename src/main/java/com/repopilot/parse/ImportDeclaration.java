package com.repopilot.parse;

public record ImportDeclaration(String qualifiedName, boolean isStatic, boolean onDemand, SourceLocation location) {

    /** Package part of a single-type import: {@code java.util.List} -&gt; {@code java.util}. */
    public String packagePart() {
        int dot = qualifiedName.lastIndexOf('.');
        return dot > 0 ? qualifiedName.substring(0, dot) : "";
    }

    public String simpleName() {
        return Types.simpleName(qualifiedName);
    }
}
