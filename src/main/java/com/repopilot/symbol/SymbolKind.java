package com.repopilot.symbol;

import com.repopilot.parse.SyntaxKind;

public enum SymbolKind {
    CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE, METHOD, CONSTRUCTOR, FIELD, PARAMETER;

    public boolean isType() {
        return this == CLASS || this == INTERFACE || this == ENUM || this == RECORD || this == ANNOTATION_TYPE;
    }

    public boolean isCallable() {
        return this == METHOD || this == CONSTRUCTOR;
    }

    public static SymbolKind fromSyntax(SyntaxKind kind) {
        return switch (kind) {
            case INTERFACE -> INTERFACE;
            case ENUM -> ENUM;
            case RECORD -> RECORD;
            case ANNOTATION_TYPE -> ANNOTATION_TYPE;
            default -> CLASS;
        };
    }
}
