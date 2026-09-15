package com.repopilot.git;

/** A deterministic resource observation. It is deliberately not a Java graph edge. */
public record CrossLanguageObservation(String kind, String rawReference, String possibleTarget,
                                       boolean semanticallyVerified, String limitation) {
}
