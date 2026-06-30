package org.codesearch.adapter;

import org.codesearch.core.LanguageSearchService;
import org.codesearch.core.SearchQuery;
import org.codesearch.core.SearchTarget;
import org.codesearch.core.SourceIndexer;
import org.codesearch.plugin.LanguagePlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * Generic {@link LanguageAdapter} backed by a {@link LanguagePlugin}.
 * New languages only need a plugin implementation — no adapter boilerplate.
 */
public class PluginLanguageAdapter implements LanguageAdapter {
    private final LanguagePlugin plugin;

    public PluginLanguageAdapter(LanguagePlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Language plugin must not be null");
        }
        this.plugin = plugin;
    }

    public LanguagePlugin plugin() {
        return plugin;
    }

    @Override
    public String language() {
        return plugin.language();
    }

    @Override
    public Set<String> fileExtensions() {
        return plugin.fileExtensions();
    }

    @Override
    public java.util.Set<org.codesearch.core.EntityKind> supportedEntityKinds() {
        return plugin.supportedEntityKinds();
    }

    @Override
    public Set<SearchTarget> supportedSearchTargets() {
        return plugin.searchCapabilities().searchTargets();
    }

    @Override
    public Set<String> supportedSearchKindAliases() {
        Set<String> aliases = plugin.searchCapabilities().searchKindAliases();
        if (!aliases.isEmpty()) {
            return aliases;
        }
        return DefaultSearchKindAliases.forCapabilities(supportedSearchTargets());
    }

    @Override
    public void indexSources(Path sourceRoot, Path indexDirectory) throws IOException, InterruptedException {
        SourceIndexer.indexSources(plugin, sourceRoot, indexDirectory);
    }

    @Override
    public SearchResponse search(Path indexDirectory, SearchRequest request) throws IOException {
        validateSearchRequest(request);

        LanguageSearchService searchService = new LanguageSearchService(indexDirectory, plugin);
        try {
            if (request.target() == SearchTarget.ASSIGNABLE_TYPE) {
                return toAdapterResponse(searchService.searchAssignableVariables(
                        request.text(),
                        language(),
                        request.caseSensitive(),
                        request.limit(),
                        request.pathFilter()
                ));
            }
            if (request.target() == SearchTarget.DECLARED_TYPE) {
                return toAdapterResponse(searchService.searchWithMetadata(
                        SearchQuery.builder(request.text(), request.kind(), language())
                                .target(SearchTarget.DECLARED_TYPE)
                                .fuzzy(request.fuzzy())
                                .caseSensitive(request.caseSensitive())
                                .limit(request.limit())
                                .pathFilter(request.pathFilter())
                                .build()
                ));
            }
            if (request.kind() == null || request.substringMatch()) {
                return toAdapterResponse(searchService.searchContaining(
                        request.text(),
                        request.kind(),
                        language(),
                        request.caseSensitive(),
                        request.limit(),
                        request.pathFilter()
                ));
            }
            return toAdapterResponse(searchService.searchWithMetadata(
                    SearchQuery.builder(request.text(), request.kind(), language())
                            .target(SearchTarget.CONTENT)
                            .fuzzy(request.fuzzy())
                            .caseSensitive(request.caseSensitive())
                            .limit(request.limit())
                            .pathFilter(request.pathFilter())
                            .build()
            ));
        } catch (LanguageSearchService.IndexUnavailableException e) {
            throw new IndexUnavailableException(e.getMessage(), e);
        }
    }

    private static SearchResponse toAdapterResponse(LanguageSearchService.SearchResponse response) {
        return new SearchResponse(response.totalHits(), response.results());
    }
}
