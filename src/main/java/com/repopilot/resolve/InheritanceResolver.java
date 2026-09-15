package com.repopilot.resolve;

import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;
import com.repopilot.symbol.SymbolKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Computes supertype closures and override relationships over the indexed types.
 *
 * <p>Cycles in the (invalid but possible) inheritance graph are tolerated via a visited set, and
 * supertypes that live outside the indexed sources are simply absent from the closure - they are
 * reported by {@link #externalSupertypes(String)} instead of being invented.
 */
public class InheritanceResolver {

    private final SymbolIndex index;
    private final ImportResolver importResolver;
    private final Map<String, CompilationUnitContext> unitsByFile;
    private final Map<String, List<String>> closureCache = new LinkedHashMap<>();
    private final Map<String, List<String>> externalCache = new LinkedHashMap<>();

    public InheritanceResolver(SymbolIndex index, ImportResolver importResolver,
                               Map<String, CompilationUnitContext> unitsByFile) {
        this.index = index;
        this.importResolver = importResolver;
        this.unitsByFile = Map.copyOf(unitsByFile);
    }

    /** Direct supertypes that could be resolved to indexed types, in declaration order. */
    public List<SymbolId> directSupertypes(String typeFqn) {
        Optional<Symbol> type = index.type(typeFqn);
        if (type.isEmpty()) {
            return List.of();
        }
        CompilationUnitContext unit = unitsByFile.get(type.get().file());
        List<SymbolId> result = new ArrayList<>();
        for (String written : type.get().superTypes()) {
            if (unit == null) {
                continue;
            }
            importResolver.resolveType(unit, typeFqn, written).single().ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    /** Supertype names that are not part of the indexed sources (JDK, libraries, missing files). */
    public List<String> externalSupertypes(String typeFqn) {
        return externalCache.computeIfAbsent(typeFqn, fqn -> {
            Optional<Symbol> type = index.type(fqn);
            if (type.isEmpty()) {
                return List.of();
            }
            CompilationUnitContext unit = unitsByFile.get(type.get().file());
            List<String> external = new ArrayList<>();
            for (String written : type.get().superTypes()) {
                if (unit == null || importResolver.resolveType(unit, fqn, written).single().isEmpty()) {
                    external.add(written);
                }
            }
            return List.copyOf(external);
        });
    }

    /** The type itself followed by all transitive supertypes, breadth first, cycle safe. */
    public List<String> supertypeClosure(String typeFqn) {
        return closureCache.computeIfAbsent(typeFqn, fqn -> {
            List<String> ordered = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            List<String> queue = new ArrayList<>();
            queue.add(fqn);
            while (!queue.isEmpty()) {
                String current = queue.remove(0);
                if (!seen.add(current)) {
                    continue;
                }
                ordered.add(current);
                for (SymbolId parent : directSupertypes(current)) {
                    queue.add(parent.value());
                }
            }
            return List.copyOf(ordered);
        });
    }

    public boolean isSubtypeOf(String candidate, String supertype) {
        return supertypeClosure(candidate).contains(supertype);
    }

    /**
     * The method a given method overrides, if any: same name and same erased parameter list in a
     * supertype. Interface methods count as overridden too.
     */
    public Optional<Symbol> overriddenMethod(Symbol method) {
        if (method.kind() != SymbolKind.METHOD) {
            return Optional.empty();
        }
        List<String> closure = supertypeClosure(method.ownerFqn());
        for (int i = 1; i < closure.size(); i++) {
            for (Symbol candidate : index.methodsOf(closure.get(i), method.simpleName())) {
                if (candidate.parameterTypes().equals(method.parameterTypes())) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /** All indexed types that declare the given type as a direct supertype. */
    public List<String> directSubtypes(String typeFqn) {
        List<String> subtypes = new ArrayList<>();
        for (Symbol type : index.types()) {
            if (directSupertypes(type.id().value()).stream().anyMatch(id -> id.value().equals(typeFqn))) {
                subtypes.add(type.id().value());
            }
        }
        return List.copyOf(subtypes);
    }
}
