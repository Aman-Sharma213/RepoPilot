package com.repopilot.resolve;

import com.repopilot.parse.Types;
import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;
import com.repopilot.symbol.SymbolKind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Selects the method a call site refers to, given the already resolved receiver type.
 *
 * <p>Overload selection uses arity first and then the argument types that could be resolved
 * statically. When the choice cannot be made, the result is {@link ResolutionStatus#AMBIGUOUS}
 * carrying every candidate; the analysis never picks one at random.
 */
public class MethodResolver {

    private final SymbolIndex index;
    private final InheritanceResolver inheritance;

    public MethodResolver(SymbolIndex index, InheritanceResolver inheritance) {
        this.index = index;
        this.inheritance = inheritance;
    }

    public Resolution resolveMethod(String ownerFqn, String methodName, int argumentCount,
                                    List<String> resolvedArgumentTypes) {
        if (index.type(ownerFqn).isEmpty()) {
            return Resolution.unresolved("receiver type " + ownerFqn + " is outside the indexed sources");
        }
        List<String> closure = inheritance.supertypeClosure(ownerFqn);
        List<Symbol> candidates = new ArrayList<>();
        Set<String> seenSignatures = new LinkedHashSet<>();
        List<ResolutionLevel> levels = new ArrayList<>();
        for (int depth = 0; depth < closure.size(); depth++) {
            String owner = closure.get(depth);
            boolean ownerIsInterface = index.type(owner)
                    .map(s -> s.kind() == SymbolKind.INTERFACE).orElse(false);
            for (Symbol method : index.methodsOf(owner, methodName)) {
                String signature = methodName + "(" + String.join(",", method.parameterTypes()) + ")";
                if (seenSignatures.add(signature)) {
                    candidates.add(method);
                    levels.add(depth == 0 ? ResolutionLevel.L1_SAME_CLASS
                            : ownerIsInterface ? ResolutionLevel.L8_INTERFACE : ResolutionLevel.L7_INHERITANCE);
                }
            }
        }
        if (candidates.isEmpty()) {
            List<String> external = new ArrayList<>();
            for (String type : closure) {
                external.addAll(inheritance.externalSupertypes(type));
            }
            if (!external.isEmpty()) {
                return Resolution.unresolved("no indexed declaration of " + methodName + " on " + ownerFqn
                        + "; it may be inherited from " + String.join(", ", external));
            }
            return Resolution.unresolved("type " + ownerFqn + " declares no method named " + methodName);
        }

        if (argumentCount < 0) {
            // Method reference: the functional interface decides the arity.
            if (candidates.size() == 1) {
                return Resolution.resolved(candidates.get(0).id(), 0.7, levels.get(0),
                        "method reference to the only " + methodName + " declared on " + ownerFqn);
            }
            return Resolution.ambiguous(candidates.stream().map(Symbol::id).toList(), ResolutionLevel.L9_OVERLOAD,
                    "method reference to an overloaded method; the functional interface decides the arity");
        }

        List<Symbol> byArity = new ArrayList<>();
        List<ResolutionLevel> arityLevels = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Symbol candidate = candidates.get(i);
            if (matchesArity(candidate, argumentCount)) {
                byArity.add(candidate);
                arityLevels.add(levels.get(i));
            }
        }
        if (byArity.isEmpty()) {
            return Resolution.unresolved(ownerFqn + "." + methodName + " has no overload taking "
                    + argumentCount + " argument(s)");
        }
        if (byArity.size() == 1) {
            Symbol only = byArity.get(0);
            ResolutionLevel level = arityLevels.get(0);
            double confidence = candidates.size() == 1 ? 1.0 : 0.95;
            return Resolution.resolved(only.id(), confidence, level,
                    "unique " + argumentCount + "-argument overload of " + methodName + " on " + only.ownerFqn()
                            + " (" + level.description() + ")");
        }

        List<Symbol> byTypes = new ArrayList<>();
        for (Symbol candidate : byArity) {
            if (argumentTypesMatch(candidate, resolvedArgumentTypes)) {
                byTypes.add(candidate);
            }
        }
        if (byTypes.size() == 1) {
            return Resolution.resolved(byTypes.get(0).id(), 0.9, ResolutionLevel.L9_OVERLOAD,
                    "overload selected by argument types " + resolvedArgumentTypes);
        }
        List<Symbol> remaining = byTypes.isEmpty() ? byArity : byTypes;
        return Resolution.ambiguous(remaining.stream().map(Symbol::id).toList(), ResolutionLevel.L9_OVERLOAD,
                remaining.size() + " overloads of " + methodName + " accept " + argumentCount
                        + " argument(s) and the argument types are not statically decidable");
    }

    /** Constructor resolution for {@code new X(...)}. */
    public Resolution resolveConstructor(String ownerFqn, int argumentCount, List<String> resolvedArgumentTypes) {
        if (index.type(ownerFqn).isEmpty()) {
            return Resolution.unresolved("type " + ownerFqn + " is outside the indexed sources");
        }
        List<Symbol> constructors = index.membersOf(ownerFqn).stream()
                .filter(s -> s.kind() == SymbolKind.CONSTRUCTOR).toList();
        if (constructors.isEmpty()) {
            // A class without a declared constructor has an implicit default constructor: the
            // instantiation targets the type itself, which the caller records as INSTANTIATES.
            return Resolution.unresolved("type " + ownerFqn + " declares no explicit constructor");
        }
        List<Symbol> byArity = constructors.stream().filter(c -> matchesArity(c, argumentCount)).toList();
        if (byArity.size() == 1) {
            return Resolution.resolved(byArity.get(0).id(), 0.95, ResolutionLevel.L1_SAME_CLASS,
                    "unique constructor of " + ownerFqn + " taking " + argumentCount + " argument(s)");
        }
        if (byArity.isEmpty()) {
            return Resolution.unresolved(ownerFqn + " has no constructor taking " + argumentCount + " argument(s)");
        }
        List<Symbol> byTypes = byArity.stream().filter(c -> argumentTypesMatch(c, resolvedArgumentTypes)).toList();
        if (byTypes.size() == 1) {
            return Resolution.resolved(byTypes.get(0).id(), 0.9, ResolutionLevel.L9_OVERLOAD,
                    "constructor selected by argument types " + resolvedArgumentTypes);
        }
        return Resolution.ambiguous(byArity.stream().map(Symbol::id).toList(), ResolutionLevel.L9_OVERLOAD,
                "several constructors of " + ownerFqn + " accept " + argumentCount + " argument(s)");
    }

    /** Looks up the declared return type of a method for chained-call resolution (level 6). */
    public Resolution resolveReturnTypeCarrier(String ownerFqn, String methodName, int argumentCount) {
        return resolveMethod(ownerFqn, methodName, argumentCount, List.of());
    }

    public SymbolIndex index() {
        return index;
    }

    private boolean matchesArity(Symbol candidate, int argumentCount) {
        int declared = candidate.parameterTypes().size();
        if (declared == argumentCount) {
            return true;
        }
        boolean varargs = declared > 0 && candidate.parameterTypes().get(declared - 1).endsWith("[]");
        return varargs && argumentCount >= declared - 1;
    }

    /**
     * Compares resolved argument types with a candidate signature. Unknown argument types (null)
     * match anything, so this method never rejects a candidate on missing information.
     */
    private boolean argumentTypesMatch(Symbol candidate, List<String> resolvedArgumentTypes) {
        List<String> declared = candidate.parameterTypes();
        if (resolvedArgumentTypes.isEmpty() || declared.size() != resolvedArgumentTypes.size()) {
            return false;
        }
        boolean anyKnown = false;
        for (int i = 0; i < declared.size(); i++) {
            String actual = resolvedArgumentTypes.get(i);
            if (actual == null) {
                continue;
            }
            anyKnown = true;
            String declaredSimple = Types.simpleName(declared.get(i));
            String actualSimple = Types.simpleName(actual);
            boolean compatible = declaredSimple.equals(actualSimple)
                    || isWideningNumeric(declaredSimple, actualSimple)
                    || (actual.equals("null") && !Types.isPrimitive(declared.get(i)))
                    || inheritance.isSubtypeOf(actual, declared.get(i));
            if (!compatible) {
                return false;
            }
        }
        return anyKnown;
    }

    private boolean isWideningNumeric(String declared, String actual) {
        List<String> order = List.of("byte", "short", "char", "int", "long", "float", "double");
        int from = order.indexOf(actual);
        int to = order.indexOf(declared);
        return from >= 0 && to >= 0 && to >= from;
    }
}
