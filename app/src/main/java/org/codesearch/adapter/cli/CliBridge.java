package org.codesearch.adapter.cli;

import org.codesearch.adapter.CodeSearchFacade;
import org.codesearch.adapter.IndexUnavailableException;
import org.codesearch.adapter.LanguageAdapter;
import org.codesearch.adapter.LanguageScopes;
import org.codesearch.adapter.MultiLanguageSupport;
import org.codesearch.adapter.SearchRequest;
import org.codesearch.adapter.SearchResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;


public final class CliBridge {
    private final CodeSearchFacade facade;

    public CliBridge(CodeSearchFacade facade) {
        if (facade == null) {
            throw new IllegalArgumentException("Facade must not be null");
        }
        this.facade = facade;
    }

    public static CliBridge forCliIndex(Path indexDirectory) {
        return new CliBridge(CodeSearchFacade.forCli(indexDirectory));
    }

    public CodeSearchFacade facade() {
        return facade;
    }

    public boolean supportsLanguage(String language) {
        return LanguageScopes.isAll(language) || facade.registry().find(language).isPresent();
    }

    public List<String> index(String language, Path sourceRoot) throws IOException, InterruptedException {
        if (LanguageScopes.isAll(language)) {
            return facade.indexAll(sourceRoot);
        }
        facade.index(language, sourceRoot);
        return List.of(language);
    }

    public SearchResponse search(String language, SearchRequest request) throws IOException {
        try {
            return facade.searchScoped(language, request);
        } catch (IndexUnavailableException e) {
            throw e;
        }
    }

    public SearchResponse quickSearch(String language, Path sourceRoot, SearchRequest request)
            throws IOException, InterruptedException {
        if (LanguageScopes.isAll(language)) {
            return quickSearchAll(sourceRoot, request);
        }

        Path quickIndexPath = Files.createTempDirectory("codesearch-quick-index");
        try {
            LanguageAdapter adapter = facade.registry().require(language);
            adapter.indexSources(sourceRoot, quickIndexPath);
            return adapter.search(quickIndexPath, request);
        } finally {
            deleteDirectoryQuietly(quickIndexPath);
        }
    }

    private SearchResponse quickSearchAll(Path sourceRoot, SearchRequest request)
            throws IOException, InterruptedException {
        Path quickIndexRoot = Files.createTempDirectory("codesearch-quick-index");
        try {
            Set<String> languages = facade.detectLanguages(sourceRoot);
            if (languages.isEmpty()) {
                throw new IllegalArgumentException("В указанном пути нет поддерживаемых исходников: " + sourceRoot);
            }

            List<SearchResponse> responses = new ArrayList<>();
            for (String language : languages) {
                LanguageAdapter adapter = facade.registry().require(language);
                if (!MultiLanguageSupport.supportsSearchTarget(adapter, request.target())) {
                    continue;
                }
                try {
                    adapter.validateSearchRequest(request);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                Path languageIndex = quickIndexRoot.resolve(language);
                adapter.indexSources(sourceRoot, languageIndex);
                responses.add(adapter.search(languageIndex, request));
            }
            if (responses.isEmpty()) {
                throw new IllegalArgumentException("Ни один зарегистрированный язык не поддерживает этот запрос.");
            }
            return MultiLanguageSupport.mergeResponses(responses, request.limit());
        } finally {
            deleteDirectoryQuietly(quickIndexRoot);
        }
    }

    private static void deleteDirectoryQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException ignored) {
            // Best-effort cleanup for temporary quick-search indexes.
        }
    }
}
