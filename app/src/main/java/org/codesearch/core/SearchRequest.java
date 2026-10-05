package org.codesearch.core;

public record SearchRequest(
        String text,
        EntityKind kind,
        SearchTarget target,
        MatchMode matchMode,
        boolean caseSensitive,
        int limit,
        String pathFilter,
        String containerFilter
) {
    public static final int DEFAULT_LIMIT = 100;

    public SearchRequest {
        if (target == null) {
            throw new IllegalArgumentException("Search target must not be null");
        }
        if (matchMode == null) {
            throw new IllegalArgumentException("Match mode must not be null");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }

        text = text == null || text.isBlank() ? null : text.trim();
        pathFilter = pathFilter == null || pathFilter.isBlank() ? null : pathFilter.trim();
        containerFilter = containerFilter == null || containerFilter.isBlank() ? null : containerFilter.trim();

        if (text == null && (target != SearchTarget.CONTENT || kind == null)) {
            throw new IllegalArgumentException("Query text must not be blank");
        }
        if (target == SearchTarget.DECLARED_TYPE && kind == null) {
            throw new IllegalArgumentException("Entity kind is required for declared type search");
        }
    }

    public boolean listsAllOfKind() {
        return text == null;
    }

    public static Builder builder(String text) {
        return new Builder(text);
    }

    public static Builder listKind(EntityKind kind) {
        return new Builder(null).kind(kind);
    }

    public static final class Builder {
        private final String text;
        private EntityKind kind;
        private SearchTarget target = SearchTarget.CONTENT;
        private MatchMode matchMode = MatchMode.EXACT;
        private boolean caseSensitive;
        private int limit = DEFAULT_LIMIT;
        private String pathFilter;
        private String containerFilter;

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

        public Builder matchMode(MatchMode matchMode) {
            this.matchMode = matchMode;
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

        public Builder containerFilter(String containerFilter) {
            this.containerFilter = containerFilter;
            return this;
        }

        public SearchRequest build() {
            return new SearchRequest(text, kind, target, matchMode, caseSensitive, limit, pathFilter, containerFilter);
        }
    }
}
