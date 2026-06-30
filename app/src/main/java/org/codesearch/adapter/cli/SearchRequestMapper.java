package org.codesearch.adapter.cli;

import org.codesearch.adapter.SearchRequest;
import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchQuery;
import org.codesearch.core.SearchTarget;

public final class SearchRequestMapper {
    private SearchRequestMapper() {}

    public static SearchRequest contentSearch(
            String query,
            EntityKind kind,
            boolean fuzzy,
            boolean caseSensitive,
            int limit,
            String pathFilter,
            boolean substringMatch
    ) {
        return SearchRequest.builder(query)
                .kind(kind)
                .target(SearchTarget.CONTENT)
                .fuzzy(fuzzy)
                .caseSensitive(caseSensitive)
                .limit(limit)
                .pathFilter(pathFilter)
                .substringMatch(substringMatch)
                .build();
    }

    public static SearchRequest declaredTypeSearch(
            String query,
            EntityKind kind,
            boolean caseSensitive,
            int limit,
            String pathFilter
    ) {
        return SearchRequest.builder(query)
                .kind(kind)
                .target(SearchTarget.DECLARED_TYPE)
                .caseSensitive(caseSensitive)
                .limit(limit)
                .pathFilter(pathFilter)
                .build();
    }

    public static SearchRequest assignableTypeSearch(
            String query,
            boolean caseSensitive,
            int limit,
            String pathFilter
    ) {
        return SearchRequest.builder(query)
                .target(SearchTarget.ASSIGNABLE_TYPE)
                .caseSensitive(caseSensitive)
                .limit(limit)
                .pathFilter(pathFilter)
                .build();
    }

    public static SearchRequest fromSearchQuery(SearchQuery query) {
        return SearchRequest.builder(query.text())
                .kind(query.kind())
                .target(query.target())
                .fuzzy(query.fuzzy())
                .caseSensitive(query.caseSensitive())
                .limit(query.limit())
                .pathFilter(query.pathFilter())
                .substringMatch(false)
                .build();
    }
}
