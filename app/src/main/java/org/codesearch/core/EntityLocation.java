package org.codesearch.core;

public record EntityLocation(String filePath, int line, int column) {
    public EntityLocation {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("File path must not be blank");
        }
        if (line < 1) {
            throw new IllegalArgumentException("Line must be positive");
        }
        if (column < 0) {
            throw new IllegalArgumentException("Column must not be negative");
        }
    }
}
