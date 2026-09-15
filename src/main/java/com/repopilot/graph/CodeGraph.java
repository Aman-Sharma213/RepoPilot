package com.repopilot.graph;

import com.repopilot.symbol.SymbolId;
import com.repopilot.util.Hashing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The semantic code graph: symbols as nodes, resolved relationships as typed edges.
 *
 * <p>Iteration order is deterministic (sorted by symbol id) so that two indexing runs over the same
 * sources produce an identical graph and an identical {@link #digest()}.
 */
public class CodeGraph {

    private final Map<SymbolId, GraphNode> nodes = new TreeMap<>();
    private final Map<SymbolId, List<GraphEdge>> outgoing = new TreeMap<>();
    private final Map<SymbolId, List<GraphEdge>> incoming = new TreeMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private final List<UnresolvedReference> unresolved = new ArrayList<>();

    public void addNode(GraphNode node) {
        nodes.put(node.id(), node);
    }

    /** Adds an edge. Edges whose endpoints are not indexed symbols are rejected, never invented. */
    public boolean addEdge(GraphEdge edge) {
        if (!nodes.containsKey(edge.source()) || !nodes.containsKey(edge.target())) {
            return false;
        }
        edges.add(edge);
        outgoing.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
        incoming.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge);
        return true;
    }

    public void addUnresolved(UnresolvedReference reference) {
        unresolved.add(reference);
    }

    public Optional<GraphNode> node(SymbolId id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public Collection<GraphNode> nodes() {
        return List.copyOf(nodes.values());
    }

    public List<GraphEdge> edges() {
        return List.copyOf(edges);
    }

    public List<UnresolvedReference> unresolvedReferences() {
        return List.copyOf(unresolved);
    }

    public List<GraphEdge> outgoing(SymbolId id) {
        return List.copyOf(outgoing.getOrDefault(id, List.of()));
    }

    /** Edges pointing at the symbol: the dependents used by reverse impact traversal. */
    public List<GraphEdge> incoming(SymbolId id) {
        return List.copyOf(incoming.getOrDefault(id, List.of()));
    }

    public List<GraphEdge> edgesOfType(EdgeType type) {
        return edges.stream().filter(e -> e.type() == type).toList();
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    /** Removes every node and edge that originates from the given file (incremental re-indexing). */
    public void removeFile(String relativePath) {
        List<SymbolId> removedNodes = nodes.values().stream()
                .filter(n -> n.file().equals(relativePath)).map(GraphNode::id).toList();
        removedNodes.forEach(nodes::remove);
        edges.removeIf(e -> e.location().file().equals(relativePath) || removedNodes.contains(e.source())
                || removedNodes.contains(e.target()));
        unresolved.removeIf(u -> u.location().file().equals(relativePath) || removedNodes.contains(u.source()));
        rebuildAdjacency();
    }

    public void sortDeterministically() {
        edges.sort(Comparator.comparing((GraphEdge e) -> e.source().value())
                .thenComparing(e -> e.target().value())
                .thenComparing(e -> e.type().name())
                .thenComparingInt(e -> e.location().startLine())
                .thenComparing(GraphEdge::evidence));
        unresolved.sort(Comparator.comparing((UnresolvedReference u) -> u.source().value())
                .thenComparing(u -> u.type().name())
                .thenComparingInt(u -> u.location().startLine())
                .thenComparing(UnresolvedReference::rawText));
        rebuildAdjacency();
    }

    private void rebuildAdjacency() {
        outgoing.clear();
        incoming.clear();
        for (GraphEdge edge : edges) {
            outgoing.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge);
        }
    }

    /** Content hash of the graph, used to prove that indexing is deterministic. */
    public String digest() {
        StringBuilder sb = new StringBuilder();
        nodes.keySet().forEach(id -> sb.append("N ").append(id.value()).append('\n'));
        edges.stream()
                .map(e -> "E " + e.source() + " " + e.type() + " " + e.target() + " " + e.location().startLine()
                        + " " + e.status() + " " + String.format(java.util.Locale.ROOT, "%.4f", e.confidence()))
                .sorted()
                .forEach(line -> sb.append(line).append('\n'));
        unresolved.stream()
                .map(u -> "U " + u.source() + " " + u.type() + " " + u.rawText() + " " + u.location().startLine())
                .sorted()
                .forEach(line -> sb.append(line).append('\n'));
        return Hashing.sha256(sb.toString());
    }

    /** Coverage statistics: how much of the code the deterministic analysis actually resolved. */
    public Map<String, Integer> statistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("nodes", nodes.size());
        stats.put("edges", edges.size());
        stats.put("unresolved", unresolved.size());
        for (EdgeType type : EdgeType.values()) {
            int count = (int) edges.stream().filter(e -> e.type() == type).count();
            if (count > 0) {
                stats.put(type.name().toLowerCase(), count);
            }
        }
        return stats;
    }
}
