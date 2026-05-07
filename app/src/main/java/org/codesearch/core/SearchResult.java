package org.codesearch.core;

public record SearchResult(CodeEntity entity, float score) {
    public SearchResult {
        if (entity == null) {
            throw new IllegalArgumentException("Search result entity must not be null");
        }
        if (Float.isNaN(score)) {
            throw new IllegalArgumentException("Search result score must be a number");
        }
    }

    public SearchResult(CodeEntity entity) {
        this(entity, 0.0f);
    }
}
