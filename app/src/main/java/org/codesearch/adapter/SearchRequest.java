package org.codesearch.adapter;

import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchTarget;

public record SearchRequest(
        String text,
        EntityKind kind,
        SearchTarget target,
        boolean fuzzy,
        boolean caseSensitive,
        int limit,
        String pathFilter,
        boolean substringMatch
) {
    private static final int DEFAULT_LIMIT = 100;

    public SearchRequest {
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
        pathFilter = pathFilter == null || pathFilter.isBlank() ? null : pathFilter.trim();
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    public static final class Builder {
        private final String text;
        private EntityKind kind;
        private SearchTarget target = SearchTarget.CONTENT;
        private boolean fuzzy;
        private boolean caseSensitive;
        private int limit = DEFAULT_LIMIT;
        private String pathFilter;
        private boolean substringMatch;

        private Builder(String text) {
            this.text = text;
        }

        public Builder kind(EntityKind kind) {
            this.kind = kind;
            return this;
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

        public Builder substringMatch(boolean substringMatch) {
            this.substringMatch = substringMatch;
            return this;
        }

        public SearchRequest build() {
            return new SearchRequest(text, kind, target, fuzzy, caseSensitive, limit, pathFilter, substringMatch);
        }
    }
}
