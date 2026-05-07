package org.codesearch.core;

import java.util.Set;

public interface LanguageModule {
    String language();

    Set<String> fileExtensions();

    Set<EntityKind> supportedEntityKinds();

    default boolean supportsFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return false;
        }

        return fileExtensions().stream().anyMatch(filePath::endsWith);
    }
}
