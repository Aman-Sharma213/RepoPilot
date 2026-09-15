package com.repopilot.git;

import com.repopilot.index.IndexingService;
import com.repopilot.security.SafePath;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves, verifies and indexes an exact local Git commit without changing the working tree. */
public class GitRevisionSnapshotService {
    public RevisionSnapshot load(Path repositoryRoot, String requestedRevision) throws IOException {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        try (Repository repository = new FileRepositoryBuilder().setGitDir(root.resolve(".git").toFile())
                .readEnvironment().build()) {
            ObjectId id = repository.resolve(requestedRevision + "^{commit}");
            if (id == null) throw new IOException("Cannot verify revision " + requestedRevision);
            RevCommit commit;
            try (RevWalk walk = new RevWalk(repository)) { commit = walk.parseCommit(id); }
            Path snapshotRoot = Files.createTempDirectory("repo-pilot-revision-");
            boolean complete = false;
            try {
                try (TreeWalk tree = new TreeWalk(repository)) {
                    tree.addTree(commit.getTree());
                    tree.setRecursive(true);
                    while (tree.next()) {
                        FileMode mode = tree.getFileMode(0);
                        if (mode == FileMode.SYMLINK || mode == FileMode.GITLINK) continue;
                        Path target = SafePath.resolveInside(snapshotRoot, tree.getPathString());
                        Files.createDirectories(target.getParent());
                        try (var output = Files.newOutputStream(target)) {
                            repository.open(tree.getObjectId(0)).copyTo(output);
                        }
                    }
                }
                RevisionProvenance provenance = new RevisionProvenance(root.toString(), requestedRevision,
                        commit.getId().name(), commit.getTree().getId().name(), RevisionSource.LOCAL_GIT_COMMIT,
                        true, "JGit commit and tree object resolution");
                RevisionSnapshot result = new RevisionSnapshot(snapshotRoot, provenance,
                        new IndexingService().index(snapshotRoot));
                complete = true;
                return result;
            } finally {
                if (!complete) delete(snapshotRoot);
            }
        }
    }

    private void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
