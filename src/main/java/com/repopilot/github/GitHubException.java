package com.repopilot.github;

/** Network, authentication or rate-limit failure while talking to GitHub. */
public class GitHubException extends RuntimeException {

    private final int statusCode;

    public GitHubException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public GitHubException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean isRateLimited() {
        return statusCode == 403 || statusCode == 429;
    }
}
