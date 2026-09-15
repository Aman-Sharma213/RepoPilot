package com.repopilot.ai;

/** The AI service could not be used. Deterministic analysis continues unaffected. */
public class AiUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AiUnavailableException(String message) {
        super(message);
    }
}
