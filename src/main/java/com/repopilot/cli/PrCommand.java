package com.repopilot.cli;

import com.repopilot.git.ChangedTarget;
import com.repopilot.git.FileDiff;
import com.repopilot.git.GitRevisionSnapshotService;
import com.repopilot.git.GitService;
import com.repopilot.git.PrAnalysisResult;
import com.repopilot.git.RevisionAwareChangedTargetLocator;
import com.repopilot.git.RevisionSnapshot;
import com.repopilot.git.TargetMappingStatus;
import com.repopilot.git.UnifiedDiffParser;
import com.repopilot.github.GitHubClient;
import com.repopilot.github.GitHubException;
import com.repopilot.github.PullRequestInfo;
import com.repopilot.github.PullRequestRef;
import com.repopilot.impact.ImpactEngine;
import com.repopilot.impact.ImpactResult;
import com.repopilot.index.RepoIndex;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

@Command(name = "pr", description = "Analyse a pull request or a diff file deterministically.")
public class PrCommand implements Callable<Integer> {

    @Mixin
    RepoOption repo = new RepoOption();

    @Option(names = "--org", paramLabel = "<org>", description = "GitHub organisation or user.")
    String org;

    @Option(names = "--repo", paramLabel = "<repo>", description = "GitHub repository name.")
    String repository;

    @Option(names = "--pr", paramLabel = "<number>", description = "Pull request number.")
    Integer pr;

    @Option(names = "--diff-file", paramLabel = "<file>",
            description = "Analyse a unified diff from a file instead of calling GitHub.")
    Path diffFile;

    @Option(names = "--base-sha", paramLabel = "<sha>", description = "Verified base revision for --diff-file.")
    String baseSha;

    @Option(names = "--head-sha", paramLabel = "<sha>", description = "Verified head revision for --diff-file.")
    String headSha;

    @Option(names = "--local", description = "Analyse uncommitted changes in the working tree.")
    boolean local;

    @Option(names = "--radius", paramLabel = "<n>", description = "Maximum dependency radius (default: 3).")
    int radius = 3;

    @Option(names = "--explain", description = "Print the full evidence path for every affected symbol.")
    boolean explain;

    @Option(names = "--show-unresolved", description = "Print unresolved impact evidence (hidden by default).")
    boolean showUnresolved;

    @Option(names = "--unresolved-limit", paramLabel = "<n>", defaultValue = "20",
            description = "Maximum unresolved details to print (default: 20).")
    int unresolvedLimit = 20;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        try {
            Path root = repo.resolveRoot();
            List<FileDiff> diffs;
            String sourceLabel;
            PrAnalysisResult analysis;

            if (diffFile != null) {
                if (!Files.isRegularFile(diffFile)) {
                    throw new IllegalArgumentException("Diff file not found: " + diffFile);
                }
                diffs = new UnifiedDiffParser().parse(Files.readString(diffFile));
                sourceLabel = "diff file " + diffFile.getFileName();
                if (baseSha != null && headSha != null) {
                    analysis = analyseVerified(root, baseSha, headSha, diffs);
                } else {
                    analysis = new RevisionAwareChangedTargetLocator()
                            .locateUnverified(diffs, root.toString(), false, false);
                }
            } else if (local) {
                GitService git = new GitService(root);
                if (!git.isGitRepository()) {
                    throw new IllegalArgumentException(root + " is not a git repository");
                }
                diffs = git.workingTreeDiff();
                sourceLabel = "working tree vs HEAD";
                analysis = analyseWorkingTree(root, git, diffs);
            } else {
                if (org == null || repository == null || pr == null) {
                    throw new IllegalArgumentException(
                            "Specify --org, --repo and --pr, or use --diff-file / --local");
                }
                PullRequestInfo info = new GitHubClient().fetchPullRequest(
                        new PullRequestRef(org, repository, pr));
                diffs = info.files();
                sourceLabel = info.ref() + " (" + info.state() + "): " + info.title()
                        + " [" + shortSha(info.baseSha()) + ".." + shortSha(info.headSha()) + "]";
                if (!new GitService(root).isGitRepository()) {
                    analysis = new RevisionAwareChangedTargetLocator()
                            .locateUnverified(diffs, root.toString(), false, false);
                } else {
                    analysis = analyseVerified(root, info.baseSha(), info.headSha(), diffs);
                }
            }

            Ui ui = new Ui(out);
            ui.header();
            ui.line("Source: " + sourceLabel);
            ui.line("Changed files: " + diffs.size());
            analysis.targets().stream().findFirst().ifPresent(target -> {
                ui.line("Base revision: " + provenance(target.baseRevision()));
                ui.line("Head revision: " + provenance(target.headRevision()));
            });
            ui.line("Deterministic changed targets:");
            for (ChangedTarget target : analysis.targets()) {
                String path = target.newPath() != null ? target.newPath() : target.oldPath();
                String symbol = target.headSymbol() != null ? target.headSymbol().value()
                        : target.baseSymbol() != null ? target.baseSymbol().value() : "<file>";
                ui.line("  " + target.status() + " " + target.kind() + " " + path + " :: " + symbol);
                target.observations().forEach(o -> ui.line("    observation " + o.kind() + ": " + o.rawReference()));
            }
            analysis.diagnostics().forEach(d -> ui.line("note: " + d));

            List<com.repopilot.symbol.SymbolId> exactHeadTargets = analysis.targets().stream()
                    .filter(t -> t.status() == TargetMappingStatus.EXACT)
                    .map(ChangedTarget::headSymbol).filter(Objects::nonNull).distinct().toList();
            if (exactHeadTargets.isEmpty() || analysis.headIndex() == null) {
                ui.blank();
                ui.line("No verified head symbols are available for impact traversal.");
                out.flush();
                return ExitCodes.OK;
            }
            ImpactResult result = new ImpactEngine(analysis.headIndex().graph())
                    .analyse(exactHeadTargets, new ImpactEngine.Options(radius, true, true, 0.0));
            ui.blank();
            ui.line("Target mapping is revision-verified. Any Confidence value below is graph-path "
                    + "confidence, not changed-target correctness.");
            ui.impact(result, new Ui.ImpactDisplayOptions(explain, showUnresolved, unresolvedLimit));
            return ExitCodes.OK;
        } catch (GitHubException e) {
            err.println("error: " + e.getMessage());
            err.flush();
            return ExitCodes.ERROR;
        } catch (Exception e) {
            return CommandSupport.handle(e, err);
        }
    }

    private PrAnalysisResult analyseVerified(Path root, String base, String head, List<FileDiff> diffs)
            throws Exception {
        GitRevisionSnapshotService snapshots = new GitRevisionSnapshotService();
        RevisionSnapshot baseSnapshot;
        try {
            baseSnapshot = snapshots.load(root, base);
        } catch (Exception baseFailure) {
            var baseMissing = com.repopilot.git.RevisionProvenance.unverified(root.toString(), base,
                    com.repopilot.git.RevisionSource.LOCAL_GIT_COMMIT);
            com.repopilot.git.RevisionProvenance headProvenance;
            try (RevisionSnapshot verifiedHead = snapshots.load(root, head)) {
                headProvenance = verifiedHead.provenance();
            } catch (Exception ignored) {
                headProvenance = com.repopilot.git.RevisionProvenance.unverified(root.toString(), head,
                        com.repopilot.git.RevisionSource.LOCAL_GIT_COMMIT);
            }
            return new RevisionAwareChangedTargetLocator().locateUnavailable(diffs, baseMissing, headProvenance,
                    TargetMappingStatus.BASE_REVISION_UNVERIFIED);
        }
        try (baseSnapshot) {
            RevisionSnapshot headSnapshot;
            try {
                headSnapshot = snapshots.load(root, head);
            } catch (Exception headFailure) {
                var headMissing = com.repopilot.git.RevisionProvenance.unverified(root.toString(), head,
                        com.repopilot.git.RevisionSource.LOCAL_GIT_COMMIT);
                return new RevisionAwareChangedTargetLocator().locateUnavailable(diffs,
                        baseSnapshot.provenance(), headMissing, TargetMappingStatus.HEAD_REVISION_UNVERIFIED);
            }
            try (headSnapshot) {
                return new RevisionAwareChangedTargetLocator().locate(diffs, baseSnapshot, headSnapshot);
            }
        }
    }

    private PrAnalysisResult analyseWorkingTree(Path root, GitService git, List<FileDiff> diffs) throws Exception {
        String committedBase = git.headSha().orElse(null);
        if (committedBase == null) {
            return new RevisionAwareChangedTargetLocator().locateUnverified(diffs, root.toString(), false, false);
        }
        GitRevisionSnapshotService snapshots = new GitRevisionSnapshotService();
        try (RevisionSnapshot verifiedBase = snapshots.load(root, committedBase)) {
            var workingTree = com.repopilot.git.RevisionProvenance.unverified(root.toString(), "WORKING_TREE",
                    com.repopilot.git.RevisionSource.WORKING_TREE);
            return new RevisionAwareChangedTargetLocator().locateUnavailable(diffs, verifiedBase.provenance(),
                    workingTree, TargetMappingStatus.HEAD_REVISION_UNVERIFIED);
        }
    }

    private String provenance(com.repopilot.git.RevisionProvenance value) {
        if (value == null || !value.verified()) return "UNVERIFIED (requested "
                + (value == null ? "unknown" : value.requestedRevision()) + ")";
        return value.resolvedCommitSha() + " (tree " + value.treeId() + ", "
                + value.verificationMethod() + ")";
    }

    private String shortSha(String sha) {
        return sha == null || sha.length() < 8 ? String.valueOf(sha) : sha.substring(0, 8);
    }
}
