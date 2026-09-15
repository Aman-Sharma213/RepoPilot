package com.repopilot.github;

public record PullRequestRef(String org, String repo, int number) {

    @Override
    public String toString() {
        return org + "/" + repo + "#" + number;
    }
}
