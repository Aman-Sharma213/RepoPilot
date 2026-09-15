package com.repopilot.git;

/** Immutable proof of the source tree against which diff locations were interpreted. */
public record RevisionProvenance(String repository, String requestedRevision, String resolvedCommitSha,
                                 String treeId, RevisionSource source, boolean verified,
                                 String verificationMethod) {
    public static RevisionProvenance unverified(String repository, String requested, RevisionSource source) {
        return new RevisionProvenance(repository, requested, null, null, source, false,
                "revision could not be verified");
    }
}
