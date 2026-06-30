package org.codesearch.adapter;

import org.codesearch.core.SearchResult;

import java.util.List;

public record SearchResponse(long totalHits, List<SearchResult> results) {
    public SearchResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
