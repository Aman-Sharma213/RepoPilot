package com.repopilot.parse;

/**
 * A local variable declaration inside a method body.
 *
 * @param initializerType type of the initializer expression when it is syntactically obvious
 *                        (e.g. {@code new Foo()}), otherwise null
 */
public record LocalVariable(String name, String declaredType, String initializerType, SourceLocation location) {
}
