package org.codesearch.adapter;

import org.codesearch.core.SearchTarget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class MultiLanguageSupport {
    private MultiLanguageSupport() {}

    public static Set<String> detectLanguages(LanguageRegistry registry, Path sourceRoot) throws IOException {
        LinkedHashSet<String> languages = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                registry.detectByFile(file.toString())
                        .map(LanguageAdapter::language)
                        .ifPresent(languages::add);
            }
        }
        return languages;
    }

    public static List<LanguageAdapter> adaptersForScope(LanguageRegistry registry, String language) {
        if (LanguageScopes.isAll(language)) {
            return new ArrayList<>(registry.all());
        }
        return List.of(registry.require(language));
    }

    public static SearchResponse mergeResponses(List<SearchResponse> responses, int limit) {
        List<org.codesearch.core.SearchResult> merged = responses.stream()
                .flatMap(response -> response.results().stream())
                .sorted(Comparator.comparingDouble(org.codesearch.core.SearchResult::score).reversed())
                .limit(limit)
                .toList();
        long totalHits = responses.stream().mapToLong(SearchResponse::totalHits).sum();
        return new SearchResponse(totalHits, merged);
    }

    public static boolean supportsSearchTarget(LanguageAdapter adapter, SearchTarget target) {
        return adapter.supportedSearchTargets().contains(target);
    }
}
