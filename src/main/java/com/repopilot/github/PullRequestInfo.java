package com.repopilot.github;

import com.repopilot.git.FileDiff;

import java.util.List;

public record PullRequestInfo(PullRequestRef ref, String title, String baseSha, String headSha, String state,
                              List<FileDiff> files) {

    public PullRequestInfo {
        files = List.copyOf(files);
    }
}
