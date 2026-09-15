package com.repopilot.parse;

/**
 * Where a syntax element lives. Lines and columns are 1-based, byte offsets are 0-based and refer
 * to the UTF-8 encoding of the file (the same encoding Tree-sitter parsed).
 */
public record SourceLocation(String file, int startLine, int endLine, int startColumn, int endColumn,
                             int startByte, int endByte) {

    public static SourceLocation unknown(String file) {
        return new SourceLocation(file, 0, 0, 0, 0, 0, 0);
    }

    public boolean containsLine(int line) {
        return line >= startLine && line <= endLine;
    }

    public boolean intersectsLines(int from, int to) {
        return from <= endLine && to >= startLine;
    }

    @Override
    public String toString() {
        return file + ":" + startLine + (endLine != startLine ? "-" + endLine : "");
    }
}
