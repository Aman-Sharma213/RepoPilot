package com.repopilot.incremental;

import java.util.List;

public record ChangeSet(List<FileChange> changes) {

    public ChangeSet {
        changes = List.copyOf(changes);
    }

    public List<String> paths(ChangeType type) {
        return changes.stream().filter(c -> c.type() == type).map(FileChange::path).sorted().toList();
    }

    /** Files that must be parsed again. */
    public List<String> requiresParsing() {
        return changes.stream()
                .filter(c -> c.type() == ChangeType.ADDED || c.type() == ChangeType.MODIFIED
                        || c.type() == ChangeType.RENAMED)
                .map(FileChange::path).sorted().toList();
    }

    public boolean isEmpty() {
        return changes.stream().allMatch(c -> c.type() == ChangeType.UNCHANGED);
    }

    public int count(ChangeType type) {
        return (int) changes.stream().filter(c -> c.type() == type).count();
    }
}
