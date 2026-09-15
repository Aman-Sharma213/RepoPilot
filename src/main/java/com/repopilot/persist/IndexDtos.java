package com.repopilot.persist;

import java.util.List;

/** Wire format of the persisted index. Kept separate from the domain model so it can evolve safely. */
public final class IndexDtos {

    private IndexDtos() {
    }

    public record LocationDto(String file, int startLine, int endLine, int startColumn, int endColumn,
                              int startByte, int endByte) {
    }

    public record SymbolDto(String id, String kind, String simpleName, String packageName, String ownerFqn,
                            List<String> parameterTypes, String type, List<String> superTypes,
                            List<String> modifiers, List<String> annotations, List<String> typeParameters,
                            LocationDto location) {
    }

    public record EdgeDto(String source, String target, String type, LocationDto location, double confidence,
                          String status, String level, String evidence) {
    }

    public record UnresolvedDto(String source, String type, String status, String rawText, String reason,
                                LocationDto location) {
    }

    public record GraphDto(List<EdgeDto> edges, List<UnresolvedDto> unresolved) {
    }

    public record FileDto(String path, long size, long lastModified, String sha256, int symbolCount) {
    }

    public record StatsDto(int files, int parsedFiles, int filesWithSyntaxErrors, int symbols, int nodes, int edges,
                           int unresolvedReferences, long millis, String digest, String mode) {
    }

    public record MetadataDto(int formatVersion, String root, String createdAt, String toolVersion, StatsDto stats) {
    }
}
