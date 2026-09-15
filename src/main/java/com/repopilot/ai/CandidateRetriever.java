package com.repopilot.ai;

import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolIndex;
import com.repopilot.symbol.SymbolKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic candidate retrieval: finds symbols whose names, owners and annotations overlap the
 * words of the request.
 *
 * <p>This runs before any AI call. The model only ever ranks symbols that really exist, which makes
 * hallucinated targets impossible to introduce, and it gives the tool a full fallback path when
 * Groq is unavailable.
 */
public class CandidateRetriever {

    private static final Set<String> STOP_WORDS = Set.of("the", "a", "an", "to", "for", "of", "in", "on", "and",
            "or", "with", "into", "from", "change", "changes", "refactor", "refactoring", "update", "updating",
            "modify", "fix", "add", "adding", "remove", "support", "accept", "make", "new", "use", "using");

    private final SymbolIndex index;

    public CandidateRetriever(SymbolIndex index) {
        this.index = index;
    }

    public List<TargetCandidate> retrieve(String description, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> queryTokens = tokenize(description);
        List<TargetCandidate> candidates = new ArrayList<>();
        for (Symbol symbol : index.all()) {
            if (symbol.kind() == SymbolKind.PARAMETER) {
                continue;
            }
            double score = score(symbol, queryTokens);
            if (score <= 0) {
                continue;
            }
            candidates.add(new TargetCandidate(symbol.id(), symbol.kind().name(), symbol.file(),
                    symbol.location().startLine(), round(score), signature(symbol)));
        }
        candidates.sort(Comparator.comparingDouble(TargetCandidate::score).reversed()
                .thenComparing(c -> c.symbol().value()));
        return candidates.stream().limit(limit).toList();
    }

    private double score(Symbol symbol, List<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return 0;
        }
        Set<String> symbolTokens = new LinkedHashSet<>(tokenize(symbol.simpleName()));
        symbolTokens.addAll(tokenize(symbol.ownerFqn()));
        symbol.annotations().forEach(a -> symbolTokens.addAll(tokenize(a)));
        if (symbol.type() != null) {
            symbolTokens.addAll(tokenize(symbol.type()));
        }
        symbol.parameterTypes().forEach(p -> symbolTokens.addAll(tokenize(p)));

        double matched = 0;
        for (String token : queryTokens) {
            if (symbolTokens.contains(token)) {
                matched += 1.0;
            } else if (symbolTokens.stream().anyMatch(t -> t.length() > 3 && token.length() > 3
                    && (t.startsWith(token) || token.startsWith(t)))) {
                matched += 0.5;
            }
        }
        if (matched == 0) {
            return 0;
        }
        double coverage = matched / queryTokens.size();
        double kindWeight = switch (symbol.kind()) {
            case METHOD -> 1.0;
            case CONSTRUCTOR -> 0.8;
            case CLASS, INTERFACE, RECORD, ENUM -> 0.75;
            case FIELD -> 0.5;
            default -> 0.4;
        };
        // Own name matches count more than owner or type matches.
        double nameBonus = tokenize(symbol.simpleName()).stream().anyMatch(queryTokens::contains) ? 0.25 : 0;
        return Math.min(1.0, coverage * kindWeight + nameBonus);
    }

    private String signature(Symbol symbol) {
        if (symbol.kind().isCallable()) {
            return symbol.type() + " " + symbol.simpleName() + "(" + String.join(", ", symbol.parameterTypes())
                    + ")";
        }
        return symbol.kind().name().toLowerCase(Locale.ROOT) + " " + symbol.simpleName();
    }

    /** Splits identifiers and prose into lowercase words: {@code AuthTokenValidator} -&gt; auth, token, validator. */
    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : text.split("[^A-Za-z0-9]+")) {
            if (part.isBlank()) {
                continue;
            }
            for (String word : part.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) {
                String lower = word.toLowerCase(Locale.ROOT);
                if (lower.length() > 1 && !STOP_WORDS.contains(lower)) {
                    tokens.add(lower);
                }
            }
        }
        return tokens;
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
