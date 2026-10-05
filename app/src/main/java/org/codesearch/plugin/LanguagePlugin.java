package org.codesearch.plugin;

import org.codesearch.core.EntityKind;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface LanguagePlugin {
    String language();

    Set<String> fileExtensions();

    Set<EntityKind> supportedEntityKinds();

    LanguageSearchCapabilities searchCapabilities();

    EntityExtractor createExtractor(List<Path> sourceFiles) throws IOException;

    default boolean supportsFile(Path file) {
        String name = file.getFileName() == null ? file.toString() : file.getFileName().toString();
        return fileExtensions().stream().anyMatch(name::endsWith);
    }
}
