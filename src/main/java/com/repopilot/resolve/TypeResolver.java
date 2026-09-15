package com.repopilot.resolve;

import com.repopilot.parse.Types;
import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Determines the static type of a receiver expression.
 *
 * <p>This is the heart of the semantic phase. Tree-sitter only tells us that the text before the
 * dot is {@code validator}; this class decides whether that is a local, a parameter, a field, a
 * type name or a chained call, and what indexed type it denotes.
 */
public class TypeResolver {

    private final SymbolIndex index;
    private final ImportResolver importResolver;
    private final InheritanceResolver inheritance;
    private final MethodResolver methodResolver;
    private final Map<String, CompilationUnitContext> unitsByFile;

    public TypeResolver(SymbolIndex index, ImportResolver importResolver, InheritanceResolver inheritance,
                        MethodResolver methodResolver, Map<String, CompilationUnitContext> unitsByFile) {
        this.index = index;
        this.importResolver = importResolver;
        this.inheritance = inheritance;
        this.methodResolver = methodResolver;
        this.unitsByFile = Map.copyOf(unitsByFile);
    }

    /** Resolves a type name written in the source of a given file. */
    public Resolution resolveTypeName(CompilationUnitContext unit, Scope scope, String writtenType) {
        if (writtenType == null || writtenType.isBlank()) {
            return Resolution.unresolved("no type given");
        }
        String erased = Types.erase(writtenType);
        if (scope != null && scope.typeVariables().containsKey(erased)) {
            String bound = scope.typeVariables().get(erased);
            if (bound == null || bound.isBlank()) {
                return Resolution.unresolved("type variable " + erased + " has no declared bound");
            }
            Resolution boundResolution = importResolver.resolveType(unit, scope.enclosingTypeFqn(), bound);
            if (boundResolution.isResolved()) {
                return Resolution.resolved(boundResolution.candidates().get(0), 0.7, ResolutionLevel.L10_GENERICS,
                        "type variable " + erased + " is bounded by " + bound);
            }
            return Resolution.unresolved("type variable " + erased + " is bounded by the external type " + bound);
        }
        return importResolver.resolveType(unit, scope == null ? null : scope.enclosingTypeFqn(), writtenType);
    }

    /**
     * Resolves the static type of a receiver expression, e.g. {@code validator},
     * {@code this.repo}, {@code factory.create()} or {@code new Helper()}.
     */
    public Resolution resolveExpressionType(CompilationUnitContext unit, Scope scope, String expression) {
        String expr = expression == null ? "" : expression.trim();
        if (expr.isEmpty() || expr.equals("this")) {
            return Resolution.resolved(SymbolId.ofType(scope.enclosingTypeFqn()), 1.0,
                    ResolutionLevel.L1_SAME_CLASS, "call on the enclosing type " + scope.enclosingTypeFqn());
        }
        if (expr.equals("super")) {
            List<SymbolId> supers = inheritance.directSupertypes(scope.enclosingTypeFqn());
            if (supers.isEmpty()) {
                return Resolution.unresolved("superclass of " + scope.enclosingTypeFqn()
                        + " is outside the indexed sources");
            }
            return Resolution.resolved(supers.get(0), 0.95, ResolutionLevel.L7_INHERITANCE,
                    "super refers to " + supers.get(0));
        }
        if (expr.startsWith("\"")) {
            return Resolution.unresolved("receiver is a String literal (java.lang.String is not indexed)");
        }
        if (expr.contains("::") || expr.contains("->")) {
            return Resolution.dynamic("lambda or method-reference receiver is decided at runtime");
        }
        if (expr.startsWith("(")) {
            return Resolution.unresolved("parenthesised or cast receiver expressions are not resolved");
        }
        if (expr.contains("[")) {
            return Resolution.unresolved("array access receivers are not resolved");
        }
        if (expr.startsWith("new ")) {
            String created = expr.substring(4).trim();
            int paren = created.indexOf('(');
            if (paren > 0) {
                created = created.substring(0, paren);
            }
            return resolveTypeName(unit, scope, created);
        }
        if (expr.endsWith(")")) {
            return resolveChainedCall(unit, scope, expr);
        }

        int dot = topLevelDot(expr);
        if (dot < 0) {
            return resolveSimpleName(unit, scope, expr);
        }
        String head = expr.substring(0, dot);
        String tail = expr.substring(dot + 1);

        // Fully qualified type reference such as com.example.Config.INSTANCE handled by type lookup first.
        Resolution asType = importResolver.resolveType(unit, scope.enclosingTypeFqn(), expr);
        if (asType.isResolved()) {
            return asType;
        }
        Resolution headType = resolveExpressionType(unit, scope, head);
        if (!headType.isResolved()) {
            return headType.status() == ResolutionStatus.AMBIGUOUS ? headType
                    : Resolution.unresolved("cannot resolve receiver '" + head + "': " + headType.reason());
        }
        return resolveFieldChain(unit, scope, headType.candidates().get(0).value(), tail);
    }

    private Resolution resolveFieldChain(CompilationUnitContext unit, Scope scope, String ownerFqn, String chain) {
        String currentOwner = ownerFqn;
        for (String segment : chain.split("\\.")) {
            Optional<Symbol> field = findField(currentOwner, segment);
            if (field.isEmpty()) {
                return Resolution.unresolved("no indexed field named " + segment + " on " + currentOwner);
            }
            CompilationUnitContext fieldUnit = unitsByFile.getOrDefault(field.get().file(), unit);
            Resolution fieldType = resolveTypeName(fieldUnit,
                    Scope.of(field.get().file(), field.get().ownerFqn()), field.get().type());
            if (!fieldType.isResolved()) {
                return Resolution.unresolved("field " + currentOwner + "." + segment + " has type "
                        + field.get().type() + " which is outside the indexed sources");
            }
            currentOwner = fieldType.candidates().get(0).value();
        }
        return Resolution.resolved(SymbolId.ofType(currentOwner), 0.9, ResolutionLevel.L4_FIELD_TYPE,
                "field access chain resolves to " + currentOwner);
    }

    private Resolution resolveSimpleName(CompilationUnitContext unit, Scope scope, String name) {
        Scope.Variable variable = scope.variables().get(name);
        if (variable != null) {
            Resolution type = resolveTypeName(unit, scope, variable.declaredType());
            if (type.isResolved()) {
                ResolutionLevel level = scope.levelFor(name);
                return Resolution.resolved(type.candidates().get(0), Math.min(type.confidence(), 0.98), level,
                        name + " is a " + variable.kind().name().toLowerCase() + " of type "
                                + variable.declaredType());
            }
            return Resolution.unresolved(name + " has type " + variable.declaredType()
                    + " which is outside the indexed sources");
        }
        Optional<Symbol> field = findField(scope.enclosingTypeFqn(), name);
        if (field.isPresent()) {
            CompilationUnitContext fieldUnit = unitsByFile.getOrDefault(field.get().file(), unit);
            Resolution type = resolveTypeName(fieldUnit, Scope.of(field.get().file(), field.get().ownerFqn()),
                    field.get().type());
            if (type.isResolved()) {
                return Resolution.resolved(type.candidates().get(0), 0.95, ResolutionLevel.L4_FIELD_TYPE,
                        name + " is a field of type " + field.get().type());
            }
            return Resolution.unresolved("field " + name + " has type " + field.get().type()
                    + " which is outside the indexed sources");
        }
        // Static call on a type name.
        Resolution asType = importResolver.resolveType(unit, scope.enclosingTypeFqn(), name);
        if (asType.status() != ResolutionStatus.UNRESOLVED) {
            return asType;
        }
        return Resolution.unresolved("'" + name + "' is neither a visible variable, field nor indexed type");
    }

    private Resolution resolveChainedCall(CompilationUnitContext unit, Scope scope, String expr) {
        int open = matchingOpenParen(expr);
        if (open < 0) {
            return Resolution.unresolved("unbalanced call expression: " + expr);
        }
        String beforeArgs = expr.substring(0, open);
        String args = expr.substring(open + 1, expr.length() - 1);
        int dot = topLevelDot(beforeArgs);
        String innerReceiver = dot < 0 ? "" : beforeArgs.substring(0, dot);
        String methodName = dot < 0 ? beforeArgs : beforeArgs.substring(dot + 1);
        if (methodName.isBlank() || !methodName.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            return Resolution.unresolved("unsupported call receiver: " + expr);
        }
        Resolution receiverType = resolveExpressionType(unit, scope, innerReceiver);
        if (!receiverType.isResolved()) {
            return Resolution.unresolved("receiver of chained call " + expr + " is unresolved: "
                    + receiverType.reason());
        }
        String ownerFqn = receiverType.candidates().get(0).value();
        Resolution method = methodResolver.resolveReturnTypeCarrier(ownerFqn, methodName, countArguments(args));
        if (!method.isResolved()) {
            return Resolution.unresolved("cannot determine the return type of " + ownerFqn + "." + methodName
                    + ": " + method.reason());
        }
        Symbol methodSymbol = index.find(method.candidates().get(0)).orElse(null);
        if (methodSymbol == null) {
            return Resolution.unresolved("resolved method is missing from the index");
        }
        CompilationUnitContext methodUnit = unitsByFile.getOrDefault(methodSymbol.file(), unit);
        Resolution returnType = resolveTypeName(methodUnit,
                Scope.of(methodSymbol.file(), methodSymbol.ownerFqn()), methodSymbol.type());
        if (!returnType.isResolved()) {
            return Resolution.unresolved("return type " + methodSymbol.type() + " of " + methodSymbol.id()
                    + " is outside the indexed sources");
        }
        return Resolution.resolved(returnType.candidates().get(0), Math.min(0.85, returnType.confidence()),
                ResolutionLevel.L6_RETURN_TYPE,
                "chained call: " + methodSymbol.id() + " returns " + methodSymbol.type());
    }

    private Optional<Symbol> findField(String ownerFqn, String fieldName) {
        for (String type : inheritance.supertypeClosure(ownerFqn)) {
            Optional<Symbol> field = index.fieldOf(type, fieldName);
            if (field.isPresent()) {
                return field;
            }
        }
        return Optional.empty();
    }

    /** Index of the '(' that opens the trailing call of an expression ending with ')'. */
    private int matchingOpenParen(String expr) {
        int depth = 0;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Last '.' that is not inside parentheses, angle brackets or a string literal. */
    private int topLevelDot(String expr) {
        int depth = 0;
        boolean inString = false;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == ')' || c == '>' || c == ']') {
                    depth++;
                } else if (c == '(' || c == '<' || c == '[') {
                    depth--;
                } else if (c == '.' && depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int countArguments(String args) {
        String trimmed = args.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        int depth = 0;
        int count = 1;
        boolean inString = false;
        for (char c : trimmed.toCharArray()) {
            if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '(' || c == '<' || c == '[') {
                    depth++;
                } else if (c == ')' || c == '>' || c == ']') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    count++;
                }
            }
        }
        return count;
    }
}
