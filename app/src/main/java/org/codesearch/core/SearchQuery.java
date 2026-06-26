package org.codesearch.core;

public record SearchQuery(
        String text,
        EntityKind kind,
        String language,
        SearchTarget target,
        boolean fuzzy,
        boolean caseSensitive,
        int limit,
        String pathFilter
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
        pathFilter = normalizePathFilter(pathFilter);
    }

    public static Builder builder(String text, EntityKind kind, String language) {
        return new Builder(text, kind, language);
    }

    private static String normalizeLanguage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase();
    }

    private static String normalizePathFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    public static final class Builder {
        private final String text;
        private final EntityKind kind;
        private final String language;
        private SearchTarget target = SearchTarget.CONTENT;
        private boolean fuzzy;
        private boolean caseSensitive;
        private int limit = DEFAULT_LIMIT;
        private String pathFilter;

        private Builder(String text, EntityKind kind, String language) {
            this.text = text;
            this.kind = kind;
            this.language = language;
        }

        public Builder target(SearchTarget target) {
            this.target = target;
            return this;
        }

        public Builder fuzzy(boolean fuzzy) {
            this.fuzzy = fuzzy;
            return this;
        }

        public Builder caseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public Builder pathFilter(String pathFilter) {
            this.pathFilter = pathFilter;
            return this;
        }

        public SearchQuery build() {
            return new SearchQuery(text, kind, language, target, fuzzy, caseSensitive, limit, pathFilter);
        }
    }
}
