package com.repopilot.git;

import com.repopilot.symbol.Symbol;
import com.repopilot.symbol.SymbolId;
import com.repopilot.symbol.SymbolIndex;
import com.repopilot.symbol.SymbolKind;
import com.repopilot.util.Hashing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Maps old hunks only to the verified base and new hunks only to the verified head. */
public class RevisionAwareChangedTargetLocator {
    private final NonJavaChangeAnalyzer resources = new NonJavaChangeAnalyzer();

    public PrAnalysisResult locate(List<FileDiff> diffs, RevisionSnapshot base, RevisionSnapshot head)
            throws IOException {
        List<ChangedTarget> targets = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        for (FileDiff diff : diffs) {
            if (!base.provenance().verified()) {
                targets.add(unverified(diff, base.provenance(), head.provenance(),
                        TargetMappingStatus.BASE_REVISION_UNVERIFIED));
                continue;
            }
            if (!head.provenance().verified()) {
                targets.add(unverified(diff, base.provenance(), head.provenance(),
                        TargetMappingStatus.HEAD_REVISION_UNVERIFIED));
                continue;
            }
            String oldSource = diff.oldPath() == null ? null : base.read(diff.oldPath());
            String newSource = diff.newPath() == null ? null : head.read(diff.newPath());
            List<DiffHunk> hunks = diff.hunks().isEmpty() ? List.of(syntheticHunk()) : diff.hunks();
            for (DiffHunk hunk : hunks) {
                DiffEvidence evidence = evidence(diff, hunk, oldSource, newSource);
                if (!hunk.lines().isEmpty() && !hunk.matches(oldSource, newSource)) {
                    targets.add(fileTarget(diff, base.provenance(), head.provenance(), evidence,
                            TargetMappingStatus.SOURCE_CONTENT_MISMATCH,
                            "diff hunk does not match the verified source blobs", List.of()));
                    continue;
                }
                if (!diff.isJava()) {
                    String language = resources.language(diff.path());
                    TargetMappingStatus status = resources.supported(language)
                            ? TargetMappingStatus.FILE_LEVEL_CHANGE : TargetMappingStatus.UNSUPPORTED_LANGUAGE;
                    targets.add(fileTarget(diff, base.provenance(), head.provenance(), evidence, status,
                            status == TargetMappingStatus.UNSUPPORTED_LANGUAGE
                                    ? "no deterministic structural analyzer for " + language
                                    : "deterministic non-Java file change",
                            resources.analyze(diff.path(), hunk)));
                } else {
                    targets.addAll(mapJava(diff, hunk, evidence, base, head));
                }
            }
        }
        Map<String, ChangedTarget> unique = new LinkedHashMap<>();
        for (ChangedTarget target : targets) unique.putIfAbsent(key(target), target);
        List<ChangedTarget> ordered = new ArrayList<>(unique.values());
        ordered.sort(Comparator.comparing((ChangedTarget t) -> t.newPath() == null ? t.oldPath() : t.newPath())
                .thenComparing(t -> t.status().name()).thenComparing(t -> t.kind().name())
                .thenComparing(t -> t.headSymbol() == null ? "" : t.headSymbol().value())
                .thenComparing(t -> t.baseSymbol() == null ? "" : t.baseSymbol().value()));
        return new PrAnalysisResult(ordered, head.index(), diagnostics);
    }

    /** Represents a diff honestly when one or both matching immutable revisions are unavailable. */
    public PrAnalysisResult locateUnverified(List<FileDiff> diffs, String repository,
                                             boolean baseVerified, boolean headVerified) {
        RevisionProvenance base = baseVerified
                ? new RevisionProvenance(repository, "verified", "verified", "verified",
                RevisionSource.DIFF_FILE, true, "local Git object resolution")
                : RevisionProvenance.unverified(repository, "unknown", RevisionSource.DIFF_FILE);
        RevisionProvenance head = headVerified
                ? new RevisionProvenance(repository, "verified", "verified", "verified",
                RevisionSource.DIFF_FILE, true, "local Git object resolution")
                : RevisionProvenance.unverified(repository, "unknown", RevisionSource.DIFF_FILE);
        TargetMappingStatus status = !baseVerified ? TargetMappingStatus.BASE_REVISION_UNVERIFIED
                : TargetMappingStatus.HEAD_REVISION_UNVERIFIED;
        return locateUnavailable(diffs, base, head, status);
    }

    /** Retains any successfully verified side when the other revision is unavailable. */
    public PrAnalysisResult locateUnavailable(List<FileDiff> diffs, RevisionProvenance base,
                                              RevisionProvenance head, TargetMappingStatus status) {
        if (status != TargetMappingStatus.BASE_REVISION_UNVERIFIED
                && status != TargetMappingStatus.HEAD_REVISION_UNVERIFIED) {
            throw new IllegalArgumentException("not an unavailable-revision status: " + status);
        }
        List<ChangedTarget> targets = new ArrayList<>();
        for (FileDiff diff : diffs) {
            DiffHunk first = diff.hunks().isEmpty() ? syntheticHunk() : diff.hunks().get(0);
            targets.add(new ChangedTarget(fileKind(diff), status, diff.oldPath(), diff.newPath(), null, null,
                    diff.isJava() ? "java" : resources.language(diff.path()), base, head,
                    evidence(diff, first, null, null), List.of(),
                    diff.isJava() ? List.of() : resources.analyze(diff.path(), first),
                    "exact mapping prohibited because matching base/head revisions were not supplied"));
        }
        return new PrAnalysisResult(targets, null, List.of("diff retained as file-level evidence only"));
    }

    private List<ChangedTarget> mapJava(FileDiff diff, DiffHunk hunk, DiffEvidence evidence,
                                        RevisionSnapshot base, RevisionSnapshot head) {
        if (diff.changeType() == DiffChangeType.ADDED) return allDeclarations(diff, evidence, base, head, true);
        if (diff.changeType() == DiffChangeType.DELETED) return allDeclarations(diff, evidence, base, head, false);
        if (diff.changeType() == DiffChangeType.RENAMED && hunk.lines().isEmpty()) {
            return List.of(fileOnly(diff, evidence, base, head, ChangedTargetKind.JAVA_FILE_RENAMED,
                    "Java file renamed without content changes"));
        }

        ChangedTarget triviaOrImport = classifyTriviaOrImport(diff, hunk, evidence, base, head);
        if (triviaOrImport != null) {
            if (diff.changeType() == DiffChangeType.RENAMED) {
                return List.of(triviaOrImport, fileOnly(diff, evidence, base, head,
                        ChangedTargetKind.JAVA_FILE_RENAMED, "Java file renamed"));
            }
            return List.of(triviaOrImport);
        }

        Selection oldSelection = select(base.index().symbols(), diff.oldPath(), hunk.removedRanges());
        Selection newSelection = select(head.index().symbols(), diff.newPath(), hunk.addedRanges());
        if (oldSelection.ambiguous() || newSelection.ambiguous()) {
            List<SymbolId> candidates = java.util.stream.Stream
                    .concat(oldSelection.symbols().stream(), newSelection.symbols().stream())
                    .map(Symbol::id).distinct().sorted().toList();
            return List.of(new ChangedTarget(ChangedTargetKind.JAVA_FILE_LEVEL_CHANGE,
                    TargetMappingStatus.AMBIGUOUS, diff.oldPath(), diff.newPath(), null, null, "java",
                    base.provenance(), head.provenance(), evidence, candidates, List.of(),
                    "one changed line intersects multiple declarations; no declaration was guessed"));
        }

        Set<SymbolId> touched = new LinkedHashSet<>();
        oldSelection.symbols().forEach(s -> touched.add(s.id()));
        newSelection.symbols().forEach(s -> touched.add(s.id()));
        List<ChangedTarget> result = new ArrayList<>();
        for (SymbolId id : touched) {
            Symbol old = base.index().symbols().find(id).orElse(null);
            Symbol current = head.index().symbols().find(id).orElse(null);
            if (old != null && current != null) {
                result.add(exact(diff, base, head, evidence, modifiedKind(current.kind()), id, id,
                        "same declaration exists in base and head and intersects this validated hunk"));
            } else if (old != null) {
                result.add(exact(diff, base, head, evidence, deletedKind(old.kind()), id, null,
                        "declaration exists only in the base revision"));
            } else if (current != null) {
                result.add(exact(diff, base, head, evidence, addedKind(current.kind()), null, id,
                        "declaration exists only in the head revision"));
            }
        }
        if (isAnnotationOnly(hunk) && !result.isEmpty()) {
            result = result.stream().map(t -> new ChangedTarget(ChangedTargetKind.ANNOTATION_MODIFIED,
                    t.status(), t.oldPath(), t.newPath(), t.baseSymbol(), t.headSymbol(), t.language(),
                    t.baseRevision(), t.headRevision(), t.evidence(), t.candidates(), t.observations(),
                    "annotation attached to this declaration changed")).toList();
        }
        if (diff.changeType() == DiffChangeType.RENAMED) {
            result.add(fileOnly(diff, evidence, base, head, ChangedTargetKind.JAVA_FILE_RENAMED,
                    "Java file renamed"));
        }
        if (result.isEmpty()) result.add(classifyFileOnly(diff, hunk, evidence, base, head));
        return result;
    }

    private List<ChangedTarget> allDeclarations(FileDiff diff, DiffEvidence evidence,
                                                RevisionSnapshot base, RevisionSnapshot head, boolean added) {
        SymbolIndex index = added ? head.index().symbols() : base.index().symbols();
        String path = added ? diff.newPath() : diff.oldPath();
        List<ChangedTarget> result = new ArrayList<>();
        result.add(fileOnly(diff, evidence, base, head,
                added ? ChangedTargetKind.JAVA_FILE_ADDED : ChangedTargetKind.JAVA_FILE_DELETED,
                added ? "Java file added" : "Java file deleted"));
        for (Symbol symbol : index.symbolsInFile(path)) {
            if (symbol.kind() == SymbolKind.PARAMETER) continue;
            result.add(exact(diff, base, head, evidence, added ? addedKind(symbol.kind()) : deletedKind(symbol.kind()),
                    added ? null : symbol.id(), added ? symbol.id() : null,
                    added ? "declaration exists in added head file" : "declaration exists in deleted base file"));
        }
        return result;
    }

    private ChangedTarget classifyTriviaOrImport(FileDiff diff, DiffHunk hunk, DiffEvidence evidence,
                                                  RevisionSnapshot base, RevisionSnapshot head) {
        List<String> changed = changedText(hunk);
        if (!changed.isEmpty() && changed.stream().allMatch(s -> s.startsWith("import "))) {
            ChangedTargetKind kind = hunk.removedLines().isEmpty() ? ChangedTargetKind.IMPORT_ADDED
                    : hunk.addedLines().isEmpty() ? ChangedTargetKind.IMPORT_REMOVED
                    : ChangedTargetKind.IMPORT_MODIFIED;
            return fileOnly(diff, evidence, base, head, kind, "import-only change");
        }
        if (!changed.isEmpty() && changed.stream().allMatch(this::isCommentLine)) {
            return fileOnly(diff, evidence, base, head, ChangedTargetKind.COMMENT_ONLY_CHANGE,
                    "comment-only change");
        }
        if (!hunk.removedLines().isEmpty() && !hunk.addedLines().isEmpty()
                && stripWhitespace(hunk.removedLines()).equals(stripWhitespace(hunk.addedLines()))) {
            return fileOnly(diff, evidence, base, head, ChangedTargetKind.WHITESPACE_ONLY_CHANGE,
                    "whitespace-only change");
        }
        return null;
    }

    private ChangedTarget classifyFileOnly(FileDiff diff, DiffHunk hunk, DiffEvidence evidence,
                                           RevisionSnapshot base, RevisionSnapshot head) {
        ChangedTargetKind kind = isAnnotationOnly(hunk) ? ChangedTargetKind.ANNOTATION_MODIFIED
                : diff.changeType() == DiffChangeType.RENAMED ? ChangedTargetKind.JAVA_FILE_RENAMED
                : ChangedTargetKind.JAVA_FILE_LEVEL_CHANGE;
        return fileOnly(diff, evidence, base, head, kind,
                "validated hunk does not uniquely intersect a Java member; no enclosing-type fallback was used");
    }

    private Selection select(SymbolIndex index, String path, List<FileDiff.LineRange> ranges) {
        if (path == null) return new Selection(List.of(), false);
        Set<Symbol> selected = new LinkedHashSet<>();
        boolean ambiguous = false;
        for (FileDiff.LineRange range : ranges) {
            List<Symbol> candidates = index.symbolsInFile(path).stream()
                    .filter(s -> s.kind() != SymbolKind.PARAMETER)
                    .filter(s -> !s.kind().isType()
                            || range.intersects(s.location().startLine(), s.location().startLine()))
                    .filter(s -> s.location().intersectsLines(range.start(), range.end())).toList();
            List<Symbol> members = candidates.stream().filter(s -> !s.kind().isType()).toList();
            if (range.start() == range.end() && members.size() > 1) ambiguous = true;
            if (!members.isEmpty()) selected.addAll(members);
            else if (candidates.size() == 1) selected.add(candidates.get(0));
            else if (candidates.size() > 1) ambiguous = true;
        }
        return new Selection(selected.stream().sorted(Comparator.comparing(Symbol::id)).toList(), ambiguous);
    }

    private record Selection(List<Symbol> symbols, boolean ambiguous) { }

    private ChangedTarget exact(FileDiff diff, RevisionSnapshot base, RevisionSnapshot head, DiffEvidence evidence,
                                ChangedTargetKind kind, SymbolId baseId, SymbolId headId, String reason) {
        return new ChangedTarget(kind, TargetMappingStatus.EXACT, diff.oldPath(), diff.newPath(), baseId, headId,
                "java", base.provenance(), head.provenance(), evidence, List.of(), List.of(), reason);
    }

    private ChangedTarget fileOnly(FileDiff diff, DiffEvidence evidence, RevisionSnapshot base,
                                   RevisionSnapshot head, ChangedTargetKind kind, String reason) {
        return new ChangedTarget(kind, TargetMappingStatus.FILE_LEVEL_CHANGE, diff.oldPath(), diff.newPath(), null,
                null, "java", base.provenance(), head.provenance(), evidence, List.of(), List.of(), reason);
    }

    private ChangedTarget fileTarget(FileDiff diff, RevisionProvenance base, RevisionProvenance head,
                                     DiffEvidence evidence, TargetMappingStatus status, String reason,
                                     List<CrossLanguageObservation> observations) {
        return new ChangedTarget(fileKind(diff), status, diff.oldPath(), diff.newPath(), null, null,
                diff.isJava() ? "java" : resources.language(diff.path()), base, head, evidence, List.of(),
                observations, reason);
    }

    private ChangedTarget unverified(FileDiff diff, RevisionProvenance base, RevisionProvenance head,
                                     TargetMappingStatus status) {
        return fileTarget(diff, base, head, null, status,
                "exact mapping prohibited because the corresponding revision is unverified", List.of());
    }

    private ChangedTargetKind fileKind(FileDiff diff) {
        if (diff.binary()) return ChangedTargetKind.BINARY_FILE_CHANGE;
        if (diff.isJava()) return switch (diff.changeType()) {
            case ADDED -> ChangedTargetKind.JAVA_FILE_ADDED;
            case DELETED -> ChangedTargetKind.JAVA_FILE_DELETED;
            case RENAMED -> ChangedTargetKind.JAVA_FILE_RENAMED;
            default -> ChangedTargetKind.JAVA_FILE_LEVEL_CHANGE;
        };
        return switch (diff.changeType()) {
            case ADDED -> ChangedTargetKind.NON_JAVA_FILE_ADDED;
            case DELETED -> ChangedTargetKind.NON_JAVA_FILE_DELETED;
            case RENAMED -> ChangedTargetKind.NON_JAVA_FILE_RENAMED;
            default -> ChangedTargetKind.NON_JAVA_FILE_MODIFIED;
        };
    }

    private DiffEvidence evidence(FileDiff diff, DiffHunk hunk, String oldSource, String newSource) {
        return new DiffEvidence(diff.oldPath(), diff.newPath(), diff.changeType(), hunk.header(),
                range(hunk.oldStart(), hunk.oldCount()), range(hunk.newStart(), hunk.newCount()),
                hunk.removedLines(), hunk.addedLines(), oldSource == null ? null : Hashing.sha256(oldSource),
                newSource == null ? null : Hashing.sha256(newSource));
    }

    private FileDiff.LineRange range(int start, int count) {
        return count == 0 ? null : new FileDiff.LineRange(start, start + count - 1);
    }

    private DiffHunk syntheticHunk() { return new DiffHunk(0, 0, 0, 0, "", List.of()); }
    private String stripWhitespace(List<String> lines) { return String.join("", lines).replaceAll("\\s+", ""); }
    private List<String> changedText(DiffHunk hunk) {
        return java.util.stream.Stream.concat(hunk.removedLines().stream(), hunk.addedLines().stream())
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
    private boolean isCommentLine(String text) {
        return text.startsWith("//") || text.startsWith("/*") || text.startsWith("*") || text.startsWith("*/");
    }
    private boolean isAnnotationOnly(DiffHunk hunk) {
        List<String> changed = changedText(hunk);
        return !changed.isEmpty() && changed.stream().allMatch(s -> s.startsWith("@"));
    }
    private String key(ChangedTarget t) {
        return t.kind()+"|"+t.status()+"|"+t.baseSymbol()+"|"+t.headSymbol()+"|"
                +(t.evidence()==null?"":t.evidence().hunkHeader())+"|"+t.oldPath()+"|"+t.newPath();
    }
    private ChangedTargetKind modifiedKind(SymbolKind kind) { return switch (kind) {
        case METHOD -> ChangedTargetKind.METHOD_MODIFIED; case CONSTRUCTOR -> ChangedTargetKind.CONSTRUCTOR_MODIFIED;
        case FIELD -> ChangedTargetKind.FIELD_MODIFIED; default -> ChangedTargetKind.TYPE_DECLARATION_MODIFIED; }; }
    private ChangedTargetKind addedKind(SymbolKind kind) { return switch (kind) {
        case METHOD -> ChangedTargetKind.METHOD_ADDED; case CONSTRUCTOR -> ChangedTargetKind.CONSTRUCTOR_ADDED;
        case FIELD -> ChangedTargetKind.FIELD_ADDED; default -> ChangedTargetKind.TYPE_ADDED; }; }
    private ChangedTargetKind deletedKind(SymbolKind kind) { return switch (kind) {
        case METHOD -> ChangedTargetKind.METHOD_DELETED; case CONSTRUCTOR -> ChangedTargetKind.CONSTRUCTOR_DELETED;
        case FIELD -> ChangedTargetKind.FIELD_DELETED; default -> ChangedTargetKind.TYPE_DELETED; }; }
}
