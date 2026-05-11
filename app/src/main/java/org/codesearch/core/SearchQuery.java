package org.codesearch.core;

public record SearchQuery(
        String text,
        EntityKind kind,
        String language,
        SearchTarget target,
        boolean fuzzy,
        boolean caseSensitive,
        int limit
) {
    private static final int DEFAULT_LIMIT = 100;

    public SearchQuery {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Query text must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        if (target == null) {
            throw new IllegalArgumentException("Search target must not be null");
        }

        text = text.trim();
        language = normalizeLanguage(language);
    }

    public SearchQuery(String text, EntityKind kind, String language) {
        this(text, kind, language, SearchTarget.CONTENT, false, false, DEFAULT_LIMIT);
    }

    public SearchQuery(String text, EntityKind kind, String language, boolean fuzzy, boolean caseSensitive, int limit) {
        this(text, kind, language, SearchTarget.CONTENT, fuzzy, caseSensitive, limit);
    }

    private static String normalizeLanguage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase();
    }
}
