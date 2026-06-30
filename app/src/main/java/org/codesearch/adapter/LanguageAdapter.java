package org.codesearch.adapter;

import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchTarget;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * Language-specific adapter: indexing and search behind a single contract.
 * New languages are added by implementing this interface and registering in {@link LanguageRegistry}.
 */
public interface LanguageAdapter {

    String language();

    Set<String> fileExtensions();

    Set<EntityKind> supportedEntityKinds();

    Set<SearchTarget> supportedSearchTargets();

    /**
     * CLI-facing search kind aliases supported by this adapter (e.g. {@code field-type}).
     */
    Set<String> supportedSearchKindAliases();

    default boolean supportsFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }
        return fileExtensions().stream().anyMatch(filePath::endsWith);
    }

    void indexSources(Path sourceRoot, Path indexDirectory) throws IOException, InterruptedException;

    SearchResponse search(Path indexDirectory, SearchRequest request) throws IOException;

    default void validateSearchRequest(SearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Search request must not be null");
        }
        if (!supportedSearchTargets().contains(request.target())) {
            throw new IllegalArgumentException(
                    "Language '" + language() + "' does not support search target: " + request.target()
            );
        }
        if (request.kind() != null && !supportedEntityKinds().contains(request.kind())) {
            throw new IllegalArgumentException(
                    "Language '" + language() + "' does not support entity kind: " + request.kind()
            );
        }
        if (request.kind() == null) {
            if (request.target() == SearchTarget.CONTENT || request.target() == SearchTarget.ASSIGNABLE_TYPE) {
                return;
            }
            throw new IllegalArgumentException("Entity kind is required for target: " + request.target());
        }
    }
}
