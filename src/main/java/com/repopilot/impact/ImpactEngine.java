package com.repopilot.impact;

import com.repopilot.graph.CodeGraph;
import com.repopilot.graph.EdgeType;
import com.repopilot.graph.GraphEdge;
import com.repopilot.graph.GraphNode;
import com.repopilot.symbol.SymbolId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reverse traversal of the code graph: who depends on the changed symbols.
 *
 * <p>The traversal is a breadth-first search over incoming edges, so the radius of a symbol is the
 * length of the shortest dependency path back to a changed target. A visited set makes cyclic
 * graphs safe, and every visited symbol keeps the edge path that reached it, which is what makes
 * the output explainable.
 */
public class ImpactEngine {

    /** @param separateTests report impacted test code as TEST_IMPACT instead of INDIRECT */
    public record Options(int maxRadius, boolean includeTests, boolean separateTests, double minConfidence) {

        public Options {
            if (maxRadius < 0) {
                throw new IllegalArgumentException("maximum impact radius must be zero or greater");
            }
            if (!Double.isFinite(minConfidence) || minConfidence < 0 || minConfidence > 1) {
                throw new IllegalArgumentException("minimum confidence must be a finite number between 0 and 1");
            }
        }

        public static Options defaults() {
            return new Options(3, true, true, 0.0);
        }

        public Options withMaxRadius(int radius) {
            return new Options(radius, includeTests, separateTests, minConfidence);
        }
    }

    private final CodeGraph graph;
    private final ImpactClassifier classifier;

    public ImpactEngine(CodeGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.classifier = new ImpactClassifier(graph);
    }

    public ImpactResult analyse(List<SymbolId> targets, Options options) {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(options, "options");
        List<String> notes = new ArrayList<>();
        Set<SymbolId> knownSet = new LinkedHashSet<>();
        List<SymbolId> requested = targets.stream()
                .map(target -> Objects.requireNonNull(target, "target")).distinct().sorted().toList();
        for (SymbolId target : requested) {
            if (graph.node(target).isPresent()) {
                knownSet.add(target);
            } else {
                notes.add("target " + target.value() + " is not part of the index and was skipped");
            }
        }
        List<SymbolId> known = knownSet.stream().sorted().toList();

        Map<SymbolId, AffectedSymbol> best = new HashMap<>();
        Deque<Frame> queue = new ArrayDeque<>();
        Set<SymbolId> targetSet = new LinkedHashSet<>(known);

        for (SymbolId target : known) {
            GraphNode node = graph.node(target).orElseThrow();
            best.put(target, new AffectedSymbol(target, node.file(), 0, List.of(), 1.0, ImpactKind.DIRECT,
                    "changed symbol"));
            queue.add(new Frame(target, 0, 1.0, List.of()));
        }

        boolean truncated = false;
        while (!queue.isEmpty()) {
            Frame frame = queue.poll();
            AffectedSymbol current = best.get(frame.symbol);
            if (current == null || current.radius() != frame.radius
                    || Double.compare(current.confidence(), frame.confidence) != 0
                    || !current.path().equals(frame.path)) {
                continue; // a better path was queued after this frame
            }
            if (frame.radius >= options.maxRadius()) {
                truncated |= hasUnseenDependent(frame, options, targetSet, best);
                continue;
            }
            for (GraphEdge edge : propagationEdges(frame.symbol)) {
                if (!edge.type().propagatesImpact()) {
                    continue;
                }
                // Reverse edges start at the dependent; a forward OVERRIDES edge points at the
                // contract the changed method implements, so the "dependent" is its target.
                boolean forwardOverride = edge.source().equals(frame.symbol)
                        && edge.type() == EdgeType.OVERRIDES;
                SymbolId dependent = forwardOverride ? edge.target() : edge.source();
                if (targetSet.contains(dependent)) {
                    continue;
                }
                GraphNode node = graph.node(dependent).orElse(null);
                if (node == null) {
                    continue;
                }
                if (!options.includeTests() && node.testCode()) {
                    continue;
                }
                // Dispatch through a supertype declaration is a weaker signal than a direct call:
                // other implementations may be the ones actually invoked at runtime.
                double edgeConfidence = requireConfidence(edge.confidence(), "graph edge confidence");
                double stepConfidence = forwardOverride ? edgeConfidence * 0.8 : edgeConfidence;
                double confidence = round(requireConfidence(frame.confidence, "path confidence") * stepConfidence);
                if (confidence < options.minConfidence()) {
                    continue;
                }
                int radius = frame.radius + 1;
                List<GraphEdge> path = new ArrayList<>();
                path.add(edge);
                path.addAll(frame.path);

                ImpactKind kind = classifier.classify(dependent, radius, path, confidence, options.separateTests());
                AffectedSymbol candidate = new AffectedSymbol(dependent, node.file(), radius, path, confidence, kind,
                        classifier.reason(path, kind, confidence));
                AffectedSymbol existing = best.get(dependent);
                boolean better = better(candidate, existing);
                if (!better) {
                    continue;
                }
                best.put(dependent, candidate);
                queue.add(new Frame(dependent, radius, confidence, path));
            }
        }

        List<AffectedSymbol> affected = new ArrayList<>(best.values());
        affected.sort(Comparator.comparingInt(AffectedSymbol::radius)
                .thenComparing((AffectedSymbol a) -> -a.confidence())
                .thenComparing(a -> a.symbol().value()));
        int maxRadius = affected.stream().mapToInt(AffectedSymbol::radius).max().orElse(0);
        if (truncated) {
            notes.add("traversal stopped at radius " + options.maxRadius()
                    + "; use --radius to look further");
        }
        Set<SymbolId> affectedIds = affected.stream().map(AffectedSymbol::symbol)
                .collect(java.util.stream.Collectors.toSet());
        List<UnresolvedImpact> unresolvedImpacts = graph.unresolvedReferences().stream()
                .filter(reference -> affectedIds.contains(reference.source()))
                .map(UnresolvedImpact::from)
                .sorted(Comparator.comparing((UnresolvedImpact finding) -> finding.source().value())
                        .thenComparing(finding -> finding.status().name())
                        .thenComparing(finding -> finding.relationshipType().name())
                        .thenComparingInt(finding -> finding.location().startLine())
                        .thenComparing(UnresolvedImpact::rawText))
                .toList();
        if (!unresolvedImpacts.isEmpty()) {
            notes.add(unresolvedImpacts.size() + " unresolved/dynamic relationship(s) originate from impacted "
                    + "symbols; no target was inferred");
        }
        long dynamicRefs = graph.unresolvedReferences().stream()
                .filter(u -> u.type() == EdgeType.CALLS || u.type() == EdgeType.DYNAMIC).count();
        if (dynamicRefs > 0) {
            notes.add(dynamicRefs + " call site(s) could not be resolved statically and are not part of this "
                    + "impact set (see repo-pilot status)");
        }
        return new ImpactResult(known, affected, maxRadius, notes, unresolvedImpacts);
    }

    /**
     * Edges that carry impact away from a symbol: everything that depends on it, plus the
     * supertype declarations it overrides (callers use the declared type, not the implementation).
     */
    private List<GraphEdge> propagationEdges(SymbolId symbol) {
        List<GraphEdge> edges = new ArrayList<>(graph.incoming(symbol));
        for (GraphEdge edge : graph.outgoing(symbol)) {
            if (edge.type() == EdgeType.OVERRIDES) {
                edges.add(edge);
            }
        }
        edges.sort(Comparator.comparing((GraphEdge e) -> e.source().value())
                .thenComparing(e -> e.target().value()).thenComparing(e -> e.type().name())
                .thenComparingInt(e -> e.location().startLine()).thenComparing(GraphEdge::evidence));
        return edges;
    }

    private boolean better(AffectedSymbol candidate, AffectedSymbol existing) {
        if (existing == null) return true;
        if (candidate.radius() != existing.radius()) return candidate.radius() < existing.radius();
        int candidateCertainty = candidate.kind() == ImpactKind.UNCERTAIN ? 0 : 1;
        int existingCertainty = existing.kind() == ImpactKind.UNCERTAIN ? 0 : 1;
        if (candidateCertainty != existingCertainty) return candidateCertainty > existingCertainty;
        int confidence = Double.compare(candidate.confidence(), existing.confidence());
        if (confidence != 0) return confidence > 0;
        return pathKey(candidate.path()).compareTo(pathKey(existing.path())) < 0;
    }

    private boolean hasUnseenDependent(Frame frame, Options options, Set<SymbolId> targetSet,
                                       Map<SymbolId, AffectedSymbol> best) {
        for (GraphEdge edge : propagationEdges(frame.symbol)) {
            if (!edge.type().propagatesImpact()) continue;
            boolean forwardOverride = edge.source().equals(frame.symbol) && edge.type() == EdgeType.OVERRIDES;
            SymbolId dependent = forwardOverride ? edge.target() : edge.source();
            if (targetSet.contains(dependent) || best.containsKey(dependent)) continue;
            GraphNode node = graph.node(dependent).orElse(null);
            if (node == null || (!options.includeTests() && node.testCode())) continue;
            double edgeConfidence = requireConfidence(edge.confidence(), "graph edge confidence");
            double step = forwardOverride ? edgeConfidence * 0.8 : edgeConfidence;
            if (round(frame.confidence * step) >= options.minConfidence()) return true;
        }
        return false;
    }

    private String pathKey(List<GraphEdge> path) {
        StringBuilder key = new StringBuilder();
        for (GraphEdge edge : path) {
            key.append(edge.source()).append('|').append(edge.type()).append('|').append(edge.target())
                    .append('|').append(edge.location()).append('|').append(edge.status()).append(';');
        }
        return key.toString();
    }

    private static double round(double value) {
        requireConfidence(value, "calculated path confidence");
        return Math.round(value * 10000d) / 10000d;
    }

    private static double requireConfidence(double value, String label) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(label + " must be a finite number between 0 and 1: " + value);
        }
        return value;
    }

    private record Frame(SymbolId symbol, int radius, double confidence, List<GraphEdge> path) {
    }
}
