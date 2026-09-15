package com.repopilot.resolve;

import com.repopilot.parse.Types;
import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a type name written in source into a fully qualified name of an indexed type.
 *
 * <p>Only types that exist in the index can be resolved. A name that belongs to the JDK or to a
 * third-party jar is reported as {@link ResolutionStatus#UNRESOLVED} with an explicit reason -
 * guessing would create fake symbols.
 */
public class ImportResolver {

    private final SymbolIndex index;

    public ImportResolver(SymbolIndex index) {
        this.index = index;
    }

    public Resolution resolveType(CompilationUnitContext unit, String enclosingTypeFqn, String writtenName) {
        if (writtenName == null || writtenName.isBlank()) {
            return Resolution.unresolved("empty type name");
        }
        String erased = Types.erase(writtenName);
        if (erased.isEmpty()) {
            return Resolution.unresolved("empty type name");
        }
        if (Types.isPrimitive(erased)) {
            return Resolution.unresolved("primitive type " + erased);
        }
        boolean generic = Types.isGeneric(writtenName);

        // Fully qualified name written out in full.
        if (erased.contains(".") && index.type(erased).isPresent()) {
            return Resolution.resolved(SymbolId.ofType(erased), generic ? 0.9 : 1.0,
                    ResolutionLevel.L2_EXPLICIT_IMPORT, "fully qualified name " + erased);
        }

        String simple = Types.simpleName(erased);
        String outerOfNested = erased.contains(".") ? erased.substring(0, erased.indexOf('.')) : null;

        // Nested type of a type declared in this compilation unit, or the enclosing type chain.
        for (String candidate : nestedCandidates(unit, enclosingTypeFqn, erased)) {
            if (index.type(candidate).isPresent()) {
                return Resolution.resolved(SymbolId.ofType(candidate), generic ? 0.9 : 1.0,
                        ResolutionLevel.L1_SAME_CLASS, "type declared in enclosing scope: " + candidate);
            }
        }

        // Level 2: explicit single-type import.
        String imported = unit.singleTypeImports().get(outerOfNested != null ? outerOfNested : simple);
        if (imported != null) {
            String candidate = outerOfNested != null
                    ? imported + erased.substring(erased.indexOf('.'))
                    : imported;
            if (index.type(candidate).isPresent()) {
                return Resolution.resolved(SymbolId.ofType(candidate), generic ? 0.9 : 1.0,
                        ResolutionLevel.L2_EXPLICIT_IMPORT, "explicit import of " + candidate);
            }
            return Resolution.unresolved("imported type " + candidate + " is outside the indexed sources");
        }

        // Level 3: same package.
        String samePackage = unit.packageName().isEmpty() ? erased : unit.packageName() + "." + erased;
        if (index.type(samePackage).isPresent()) {
            return Resolution.resolved(SymbolId.ofType(samePackage), generic ? 0.9 : 1.0,
                    ResolutionLevel.L3_SAME_PACKAGE, "same package as " + unit.file());
        }

        // Wildcard imports: only usable when exactly one indexed package provides the name.
        List<SymbolId> wildcardHits = new ArrayList<>();
        for (String pkg : unit.onDemandPackages()) {
            index.type(pkg + "." + erased).ifPresent(s -> wildcardHits.add(s.id()));
        }
        if (wildcardHits.size() == 1) {
            return Resolution.resolved(wildcardHits.get(0), 0.85, ResolutionLevel.L2_EXPLICIT_IMPORT,
                    "on-demand import " + wildcardHits.get(0));
        }
        if (wildcardHits.size() > 1) {
            return Resolution.ambiguous(wildcardHits, ResolutionLevel.L2_EXPLICIT_IMPORT,
                    "several on-demand imports provide " + erased);
        }

        // Last resort: a unique simple name in the whole repository is a safe answer; several are not.
        List<Symbol> byName = index.typesNamed(simple);
        if (byName.size() == 1 && !erased.contains(".")) {
            return Resolution.resolved(byName.get(0).id(), 0.6, ResolutionLevel.L3_SAME_PACKAGE,
                    "unique type named " + simple + " in the repository (no import found)");
        }
        if (byName.size() > 1 && !erased.contains(".")) {
            return Resolution.ambiguous(byName.stream().map(Symbol::id).toList(), ResolutionLevel.L3_SAME_PACKAGE,
                    byName.size() + " indexed types are named " + simple + " and no import disambiguates it");
        }
        return Resolution.unresolved("type " + writtenName + " is not part of the indexed sources");
    }

    private List<String> nestedCandidates(CompilationUnitContext unit, String enclosingTypeFqn, String erased) {
        List<String> candidates = new ArrayList<>();
        String scope = enclosingTypeFqn;
        while (scope != null && !scope.isEmpty()) {
            candidates.add(scope + "." + erased);
            int dot = scope.lastIndexOf('.');
            scope = dot < 0 ? null : scope.substring(0, dot);
        }
        for (String declared : unit.declaredTypes()) {
            if (declared.endsWith("." + erased) || declared.equals(erased)) {
                candidates.add(declared);
            }
        }
        return candidates;
    }
}
