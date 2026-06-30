package org.codesearch.adapter;

import org.codesearch.core.SearchTarget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * High-level entry point for language-agnostic indexing and search.
 * CLI and other frontends can depend on this facade instead of concrete language modules.
 */
public final class CodeSearchFacade {
    private final LanguageRegistry registry;
    private final Path indexRoot;
    private final IndexLayout indexLayout;

    public CodeSearchFacade(LanguageRegistry registry, Path indexRoot) {
        this(registry, indexRoot, IndexLayout.PER_LANGUAGE_SUBDIRECTORY);
    }

    public CodeSearchFacade(LanguageRegistry registry, Path indexRoot, IndexLayout indexLayout) {
        if (registry == null) {
            throw new IllegalArgumentException("Language registry must not be null");
        }
        if (indexRoot == null) {
            throw new IllegalArgumentException("Index root must not be null");
        }
        if (indexLayout == null) {
            throw new IllegalArgumentException("Index layout must not be null");
        }
        this.registry = registry;
        this.indexRoot = indexRoot;
        this.indexLayout = indexLayout;
    }

    public static CodeSearchFacade withDefaults(Path indexRoot) {
        return new CodeSearchFacade(DefaultLanguageRegistry.withDefaults(), indexRoot);
    }

    /**
     * Facade configured for CLI: each language index lives in {@code index/<language>/}.
     */
    public static CodeSearchFacade forCli(Path indexDirectory) {
        return new CodeSearchFacade(
                DefaultLanguageRegistry.withDefaults(),
                indexDirectory,
                IndexLayout.PER_LANGUAGE_SUBDIRECTORY
        );
    }

    public LanguageRegistry registry() {
        return registry;
    }

    public Path indexRoot() {
        return indexRoot;
    }

    public IndexLayout indexLayout() {
        return indexLayout;
    }

    public Path indexDirectory(String language) {
        return IndexPaths.resolve(indexRoot, language, indexLayout);
    }

    public void index(String language, Path sourceRoot) throws IOException, InterruptedException {
        LanguageAdapter adapter = registry.require(language);
        adapter.indexSources(sourceRoot, indexDirectory(language));
    }

    public SearchResponse search(String language, SearchRequest request) throws IOException {
        LanguageAdapter adapter = registry.require(language);
        return adapter.search(indexDirectory(language), request);
    }

    public Set<String> detectLanguages(Path sourceRoot) throws IOException {
        return MultiLanguageSupport.detectLanguages(registry, sourceRoot);
    }

    public List<String> indexAll(Path sourceRoot) throws IOException, InterruptedException {
        Set<String> languages = detectLanguages(sourceRoot);
        if (languages.isEmpty()) {
            throw new IllegalArgumentException("В указанном пути нет поддерживаемых исходников: " + sourceRoot);
        }
        List<String> indexed = new ArrayList<>();
        for (String language : languages) {
            index(language, sourceRoot);
            indexed.add(language);
        }
        return indexed;
    }

    public SearchResponse searchScoped(String language, SearchRequest request) throws IOException {
        if (LanguageScopes.isAll(language)) {
            return searchAll(request);
        }
        return search(language, request);
    }

    public SearchResponse searchAll(SearchRequest request) throws IOException {
        List<SearchResponse> responses = new ArrayList<>();
        for (LanguageAdapter adapter : registry.all()) {
            if (!MultiLanguageSupport.supportsSearchTarget(adapter, request.target())) {
                continue;
            }
            try {
                adapter.validateSearchRequest(request);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Path indexDir = indexDirectory(adapter.language());
            if (!Files.isDirectory(indexDir)) {
                continue;
            }
            try {
                responses.add(adapter.search(indexDir, request));
            } catch (IndexUnavailableException ignored) {
                // Skip languages without a ready index.
            }
        }
        if (responses.isEmpty()) {
            throw new IndexUnavailableException("Нет готового индекса ни для одного языка в " + indexRoot);
        }
        return MultiLanguageSupport.mergeResponses(responses, request.limit());
    }
}
