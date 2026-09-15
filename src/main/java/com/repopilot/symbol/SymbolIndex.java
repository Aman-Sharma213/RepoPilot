package com.repopilot.symbol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * All symbols of a repository, with the lookup structures the resolver needs.
 *
 * <p>Every lookup returns results in a deterministic order (sorted by symbol id), so two runs over
 * the same sources produce byte-identical output.
 */
public class SymbolIndex {

    private final Map<SymbolId, Symbol> symbols = new TreeMap<>();
    private final Map<String, Symbol> typesByFqn = new TreeMap<>();
    private final Map<String, List<Symbol>> typesBySimpleName = new TreeMap<>();
    private final Map<String, List<Symbol>> membersByOwner = new TreeMap<>();
    private final Map<String, List<Symbol>> symbolsByFile = new TreeMap<>();

    public void add(Symbol symbol) {
        Symbol previous = symbols.put(symbol.id(), symbol);
        if (previous != null) {
            removeFromLookups(previous);
        }
        if (symbol.isType()) {
            typesByFqn.put(symbol.id().value(), symbol);
            typesBySimpleName.computeIfAbsent(symbol.simpleName(), k -> new ArrayList<>()).add(symbol);
        } else {
            membersByOwner.computeIfAbsent(symbol.ownerFqn(), k -> new ArrayList<>()).add(symbol);
        }
        symbolsByFile.computeIfAbsent(symbol.file(), k -> new ArrayList<>()).add(symbol);
        sortLists();
    }

    public void addAll(Collection<Symbol> newSymbols) {
        newSymbols.forEach(this::add);
    }

    /** Removes every symbol declared in the given file (used by incremental indexing). */
    public void removeFile(String relativePath) {
        List<Symbol> declared = symbolsByFile.remove(relativePath);
        if (declared == null) {
            return;
        }
        for (Symbol symbol : declared) {
            symbols.remove(symbol.id());
            removeFromLookups(symbol);
        }
    }

    private void removeFromLookups(Symbol symbol) {
        if (symbol.isType()) {
            typesByFqn.remove(symbol.id().value(), symbol);
            List<Symbol> bySimple = typesBySimpleName.get(symbol.simpleName());
            if (bySimple != null) {
                bySimple.removeIf(s -> s.id().equals(symbol.id()));
                if (bySimple.isEmpty()) {
                    typesBySimpleName.remove(symbol.simpleName());
                }
            }
        } else {
            List<Symbol> members = membersByOwner.get(symbol.ownerFqn());
            if (members != null) {
                members.removeIf(s -> s.id().equals(symbol.id()));
                if (members.isEmpty()) {
                    membersByOwner.remove(symbol.ownerFqn());
                }
            }
        }
    }

    private void sortLists() {
        typesBySimpleName.values().forEach(l -> l.sort(java.util.Comparator.comparing(Symbol::id)));
        membersByOwner.values().forEach(l -> l.sort(java.util.Comparator.comparing(Symbol::id)));
        symbolsByFile.values().forEach(l -> l.sort(java.util.Comparator.comparing(Symbol::id)));
    }

    public Optional<Symbol> find(SymbolId id) {
        return Optional.ofNullable(symbols.get(id));
    }

    public boolean contains(SymbolId id) {
        return symbols.containsKey(id);
    }

    public Optional<Symbol> type(String fullyQualifiedName) {
        return Optional.ofNullable(typesByFqn.get(fullyQualifiedName));
    }

    /** All types sharing a simple name. More than one entry means the name is ambiguous. */
    public List<Symbol> typesNamed(String simpleName) {
        return List.copyOf(typesBySimpleName.getOrDefault(simpleName, List.of()));
    }

    public List<Symbol> membersOf(String ownerFqn) {
        return List.copyOf(membersByOwner.getOrDefault(ownerFqn, List.of()));
    }

    public List<Symbol> methodsOf(String ownerFqn, String methodName) {
        List<Symbol> result = new ArrayList<>();
        for (Symbol member : membersByOwner.getOrDefault(ownerFqn, List.of())) {
            if (member.kind().isCallable() && member.simpleName().equals(methodName)) {
                result.add(member);
            }
        }
        return List.copyOf(result);
    }

    public Optional<Symbol> fieldOf(String ownerFqn, String fieldName) {
        return membersByOwner.getOrDefault(ownerFqn, List.of()).stream()
                .filter(m -> m.kind() == SymbolKind.FIELD && m.simpleName().equals(fieldName))
                .findFirst();
    }

    public List<Symbol> symbolsInFile(String relativePath) {
        return List.copyOf(symbolsByFile.getOrDefault(relativePath, List.of()));
    }

    public List<String> files() {
        return List.copyOf(symbolsByFile.keySet());
    }

    public Collection<Symbol> all() {
        return Collections.unmodifiableCollection(symbols.values());
    }

    public List<Symbol> types() {
        return List.copyOf(typesByFqn.values());
    }

    public int size() {
        return symbols.size();
    }

    public Map<SymbolId, Symbol> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(symbols));
    }
}
