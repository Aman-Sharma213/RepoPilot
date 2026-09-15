package com.repopilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates model output before it is allowed anywhere near the graph.
 *
 * <p>Rules enforced here:
 * <ul>
 *   <li>the response must be JSON with a {@code targets} array;</li>
 *   <li>every symbol must exist in the index and must have been offered as a candidate;</li>
 *   <li>confidence must be a number in [0, 1];</li>
 *   <li>duplicates are collapsed, unknown symbols are dropped with an explicit note.</li>
 * </ul>
 */
public class AiResponseValidator {

    public static final int MAX_TARGETS = 5;
    public static final int MAX_RESPONSE_CHARS = 1_000_000;

    public record Validated(List<RankedTarget> targets, List<String> notes) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final SymbolIndex index;

    public AiResponseValidator(SymbolIndex index) {
        this.index = index;
    }

    public Validated validate(String rawJson, List<TargetCandidate> offered) {
        List<RankedTarget> targets = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> allowed = new LinkedHashSet<>();
        offered.forEach(c -> allowed.add(c.symbol().value()));

        if (rawJson == null || rawJson.isBlank()) {
            throw new AiUnavailableException("Groq returned an empty response");
        }
        if (rawJson.length() > MAX_RESPONSE_CHARS) {
            throw new AiUnavailableException("Groq response exceeded the validation size limit");
        }
        JsonNode root;
        try {
            root = mapper.readTree(rawJson);
        } catch (Exception e) {
            throw new AiUnavailableException("Groq returned malformed JSON");
        }
        JsonNode array = root.path("targets");
        if (!array.isArray()) {
            throw new AiUnavailableException("Groq response has no 'targets' array");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode node : array) {
            if (targets.size() >= MAX_TARGETS) {
                notes.add("ignored AI targets beyond the maximum of " + MAX_TARGETS);
                break;
            }
            String symbol = node.path("symbol").asText("").trim();
            if (symbol.isEmpty()) {
                notes.add("dropped an AI target without a symbol");
                continue;
            }
            if (!allowed.contains(symbol)) {
                notes.add("dropped hallucinated AI target '" + symbol + "' (not among the offered candidates)");
                continue;
            }
            if (!index.contains(new SymbolId(symbol))) {
                notes.add("dropped AI target '" + symbol + "' (not present in the index)");
                continue;
            }
            if (!seen.add(symbol)) {
                continue;
            }
            JsonNode confidenceNode = node.path("confidence");
            if (!confidenceNode.isNumber()) {
                notes.add("dropped AI target '" + symbol + "' (confidence is not a number)");
                continue;
            }
            double confidence = confidenceNode.asDouble();
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                notes.add("dropped AI target '" + symbol + "' (confidence is outside [0,1])");
                continue;
            }
            String reason = PromptSanitizer.sanitize(node.path("reason").asText(""));
            targets.add(new RankedTarget(new SymbolId(symbol), confidence,
                    reason.isBlank() ? "ranked by AI" : reason));
        }
        if (targets.isEmpty()) {
            throw new AiUnavailableException("no valid AI target survived validation");
        }
        targets.sort((a, b) -> {
            int confidenceOrder = Double.compare(b.confidence(), a.confidence());
            return confidenceOrder != 0 ? confidenceOrder : a.symbol().compareTo(b.symbol());
        });
        return new Validated(targets, notes);
    }
}
