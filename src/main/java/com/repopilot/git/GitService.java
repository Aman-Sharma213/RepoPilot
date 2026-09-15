package com.repopilot.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Thin wrapper over JGit for the diffs Repo Pilot needs. Absent Git support degrades gracefully. */
public class GitService {

    private final Path root;

    public GitService(Path root) {
        this.root = root;
    }

    public boolean isGitRepository() {
        return Files.isDirectory(root.resolve(".git"));
    }

    public Optional<String> headSha() {
        try (Repository repository = open()) {
            ObjectId head = repository.resolve("HEAD");
            return Optional.ofNullable(head).map(ObjectId::getName);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Optional<String> currentBranch() {
        try (Repository repository = open()) {
            return Optional.ofNullable(repository.getBranch());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Unified diff of the working tree (including staged changes) against HEAD. */
    public List<FileDiff> workingTreeDiff() throws IOException {
        try (Repository repository = open();
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            ObjectId head = repository.resolve("HEAD^{tree}");
            if (head == null) {
                return List.of();
            }
            try (RevWalk walk = new RevWalk(repository)) {
                CanonicalTreeParser headTree = new CanonicalTreeParser();
                headTree.reset(repository.newObjectReader(), head);
                formatter.format(headTree, new FileTreeIterator(repository));
            }
            return new UnifiedDiffParser().parse(out.toString(StandardCharsets.UTF_8));
        }
    }

    /** Unified diff between two commits. */
    public List<FileDiff> diff(String baseSha, String headSha) throws IOException {
        try (Repository repository = open();
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             DiffFormatter formatter = new DiffFormatter(out)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            ObjectId base = repository.resolve(baseSha + "^{tree}");
            ObjectId head = repository.resolve(headSha + "^{tree}");
            if (base == null || head == null) {
                throw new IOException("Cannot resolve " + (base == null ? baseSha : headSha) + " in " + root);
            }
            CanonicalTreeParser baseTree = new CanonicalTreeParser();
            baseTree.reset(repository.newObjectReader(), base);
            CanonicalTreeParser headTree = new CanonicalTreeParser();
            headTree.reset(repository.newObjectReader(), head);
            formatter.format(baseTree, headTree);
            return new UnifiedDiffParser().parse(out.toString(StandardCharsets.UTF_8));
        }
    }

    /** Creates a repository, used by tests and by {@code init} on a fresh directory. */
    public static void init(Path directory) throws GitAPIException {
        try (Git git = Git.init().setDirectory(directory.toFile()).call()) {
            git.getRepository().getConfig();
        }
    }

    private Repository open() throws IOException {
        return new FileRepositoryBuilder().setGitDir(root.resolve(".git").toFile()).readEnvironment().build();
    }
}
