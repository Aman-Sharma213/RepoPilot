package com.repopilot.ai;

import java.util.Locale;

/** User-controlled AI policy. Core parsing and graph analysis remain local in every mode. */
public enum AiMode {
    LOCAL,
    AI_ASSISTED,
    AUTO;

    public static AiMode parse(String value) {
        if (value == null) return AUTO;
        return switch (value.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "local" -> LOCAL;
            case "ai", "assisted", "ai_assisted" -> AI_ASSISTED;
            case "auto" -> AUTO;
            default -> throw new IllegalArgumentException("Unknown AI mode '" + value
                    + "'. Use local, ai, or auto.");
        };
    }
}
