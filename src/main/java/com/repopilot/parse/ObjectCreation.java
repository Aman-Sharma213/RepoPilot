package com.repopilot.parse;

public record ObjectCreation(String type, int argumentCount, boolean anonymousClass, SourceLocation location) {

    public String erasedType() {
        return Types.erase(type);
    }
}
