package org.codesearch.plugin;

import org.codesearch.core.CodeEntity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
public interface EntityExtractor {
    List<CodeEntity> extractEntities(Path file) throws IOException;
}
