package com.repopilot.incremental;

/** @param previousPath set only for {@link ChangeType#RENAMED} */
public record FileChange(String path, ChangeType type, String previousPath) {

    public static FileChange of(String path, ChangeType type) {
        return new FileChange(path, type, null);
    }
}
