package com.repopilot.incremental;

import com.repopilot.scan.Repository;
import com.repopilot.scan.ScannedFile;
import com.repopilot.util.Hashing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares a fresh scan with the fingerprints stored in the index.
 *
 * <p>Size and modification time are checked first; the content hash is only computed when the cheap
 * check says something might have changed, which keeps re-indexing large repositories fast while
 * staying correct across checkouts that rewrite timestamps.
 */
public class ChangeDetector {

    public ChangeSet detect(Repository repository, Map<String, FileRecord> previous) throws IOException {
        List<FileChange> changes = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Map<String, String> deletedHashes = new LinkedHashMap<>();

        for (ScannedFile file : repository.javaFiles()) {
            seen.add(file.relativePath());
            FileRecord known = previous.get(file.relativePath());
            if (known == null) {
                changes.add(FileChange.of(file.relativePath(), ChangeType.ADDED));
            } else if (known.unchangedMetadata(file)) {
                changes.add(FileChange.of(file.relativePath(), ChangeType.UNCHANGED));
            } else if (Hashing.sha256(file.absolutePath()).equals(known.sha256())) {
                // Timestamp changed but content did not: no reparse needed.
                changes.add(FileChange.of(file.relativePath(), ChangeType.UNCHANGED));
            } else {
                changes.add(FileChange.of(file.relativePath(), ChangeType.MODIFIED));
            }
        }
        for (Map.Entry<String, FileRecord> entry : previous.entrySet()) {
            if (!seen.contains(entry.getKey())) {
                deletedHashes.put(entry.getValue().sha256(), entry.getKey());
                changes.add(FileChange.of(entry.getKey(), ChangeType.DELETED));
            }
        }

        // A new file whose content hash equals a deleted file's hash is a rename.
        List<FileChange> resolved = new ArrayList<>();
        Set<String> renamedFrom = new LinkedHashSet<>();
        for (FileChange change : changes) {
            if (change.type() == ChangeType.ADDED) {
                ScannedFile file = repository.file(change.path()).orElse(null);
                if (file != null) {
                    String hash = Hashing.sha256(file.absolutePath());
                    String origin = deletedHashes.get(hash);
                    if (origin != null) {
                        resolved.add(new FileChange(change.path(), ChangeType.RENAMED, origin));
                        renamedFrom.add(origin);
                        continue;
                    }
                }
            }
            resolved.add(change);
        }
        resolved.removeIf(c -> c.type() == ChangeType.DELETED && renamedFrom.contains(c.path()));
        resolved.sort(Comparator.comparing(FileChange::path));
        return new ChangeSet(resolved);
    }
}
