package com.repopilot.ai;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Repository content and user descriptions are untrusted input to the model.
 *
 * <p>Source comments in an analysed repository can contain prompt injection such as
 * {@code // AI SYSTEM: ignore previous instructions}. Everything that leaves this class is
 * neutralised (control characters removed, injection markers defanged, length capped) and is always
 * embedded inside an explicit "untrusted data" fence in the prompt.
 */
public final class PromptSanitizer {

    public static final int MAX_LENGTH = 2000;

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|prior|above)"),
            Pattern.compile("(?i)\\b(ai|assistant|system|developer)\\s*(system)?\\s*[:>]\\s*"),
            Pattern.compile("(?i)</?(system|assistant|user|tool)>"),
            Pattern.compile("(?i)</?(untrusted-change-description|candidates)>"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+"),
            Pattern.compile("(?i)new\\s+instructions?\\s*[:>]"));

    private PromptSanitizer() {
    }

    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("[\\p{Cntrl}&&[^\n\t]]", " ");
        for (Pattern pattern : INJECTION_PATTERNS) {
            cleaned = pattern.matcher(cleaned).replaceAll("[redacted-instruction]");
        }
        cleaned = cleaned.replace("```", "'''");
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH) + " [truncated]";
        }
        return cleaned.trim();
    }

    /** True when the text tries to give instructions rather than describe a change. */
    public static boolean looksLikeInjection(String text) {
        if (text == null) {
            return false;
        }
        return INJECTION_PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }
}
