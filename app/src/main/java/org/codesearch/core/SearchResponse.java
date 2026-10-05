package org.codesearch.core;

import java.util.Comparator;
import java.util.List;

public record SearchResponse(long totalHits, List<SearchResult> results) {
    public static final Comparator<SearchResult> RESULT_ORDER = Comparator
            .comparingDouble(SearchResult::score).reversed()
            .thenComparing(result -> result.entity().location().filePath())
            .thenComparingInt(result -> result.entity().location().line())
            .thenComparingInt(result -> result.entity().location().column())
            .thenComparing(result -> result.entity().content());

    public SearchResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public static SearchResponse empty() {
        return new SearchResponse(0, List.of());
    }

    public static SearchResponse merge(List<SearchResponse> responses, int limit) {
        List<SearchResult> merged = responses.stream()
                .flatMap(response -> response.results().stream())
                .sorted(RESULT_ORDER)
                .limit(limit)
                .toList();
        long totalHits = responses.stream().mapToLong(SearchResponse::totalHits).sum();
        return new SearchResponse(totalHits, merged);
    }
}
