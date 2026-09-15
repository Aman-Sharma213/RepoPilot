package com.repopilot.symbol;

import java.util.List;

/**
 * Stable identity of a symbol.
 *
 * <pre>
 *   type        com.example.auth.AuthService
 *   nested type com.example.auth.AuthService.Inner
 *   method      com.example.auth.AuthService#validate(Token)
 *   constructor com.example.auth.AuthService#&lt;init&gt;(Token)
 *   field       com.example.auth.AuthService#secret
 *   parameter   com.example.auth.AuthService#validate(Token)::token
 * </pre>
 *
 * <p>Parameter types are the erased types <em>as written in the source</em>. That keeps identity
 * computable from syntax alone (Phase 4) while still separating overloads.
 */
public record SymbolId(String value) implements Comparable<SymbolId> {

    public static final String CONSTRUCTOR_NAME = "<init>";

    public SymbolId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Symbol id must not be blank");
        }
    }

    public static SymbolId ofType(String fullyQualifiedName) {
        return new SymbolId(fullyQualifiedName);
    }

    public static SymbolId ofMethod(String ownerFqn, String methodName, List<String> erasedParameterTypes) {
        return new SymbolId(ownerFqn + "#" + methodName + "(" + String.join(",", erasedParameterTypes) + ")");
    }

    public static SymbolId ofConstructor(String ownerFqn, List<String> erasedParameterTypes) {
        return ofMethod(ownerFqn, CONSTRUCTOR_NAME, erasedParameterTypes);
    }

    public static SymbolId ofField(String ownerFqn, String fieldName) {
        return new SymbolId(ownerFqn + "#" + fieldName);
    }

    public static SymbolId ofParameter(SymbolId methodId, String parameterName) {
        return new SymbolId(methodId.value() + "::" + parameterName);
    }

    public boolean isMember() {
        return value.indexOf('#') >= 0;
    }

    public boolean isCallable() {
        return isMember() && value.endsWith(")");
    }

    public boolean isParameter() {
        return value.contains(")::");
    }

    /** Fully qualified name of the declaring type, or the type name itself for a type symbol. */
    public String ownerFqn() {
        int hash = value.indexOf('#');
        return hash < 0 ? value : value.substring(0, hash);
    }

    /** Member name without parameter list; {@code null} for a type symbol. */
    public String memberName() {
        int hash = value.indexOf('#');
        if (hash < 0) {
            return null;
        }
        String rest = value.substring(hash + 1);
        int paren = rest.indexOf('(');
        return paren < 0 ? rest : rest.substring(0, paren);
    }

    public List<String> parameterTypes() {
        int open = value.indexOf('(');
        int close = value.lastIndexOf(')');
        if (open < 0 || close < open) {
            return List.of();
        }
        String inner = value.substring(open + 1, close);
        if (inner.isBlank()) {
            return List.of();
        }
        return List.of(inner.split(","));
    }

    /** Human friendly form used in CLI output: {@code AuthService.validate(Token)}. */
    public String display() {
        String owner = ownerFqn();
        String simpleOwner = owner.substring(owner.lastIndexOf('.') + 1);
        if (!isMember()) {
            return simpleOwner;
        }
        String member = value.substring(value.indexOf('#') + 1);
        if (member.startsWith(CONSTRUCTOR_NAME)) {
            member = simpleOwner + member.substring(CONSTRUCTOR_NAME.length());
        }
        return simpleOwner + "." + member;
    }

    @Override
    public int compareTo(SymbolId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
