package com.repopilot.parse;

/** Kinds of syntax elements extracted from the concrete syntax tree. */
public enum SyntaxKind {
    PACKAGE, IMPORT, CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE,
    METHOD, CONSTRUCTOR, FIELD, PARAMETER, LOCAL_VARIABLE,
    METHOD_INVOCATION, OBJECT_CREATION, ANNOTATION_USE, EXTENDS, IMPLEMENTS
}
