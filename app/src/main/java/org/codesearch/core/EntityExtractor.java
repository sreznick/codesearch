package org.codesearch.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
public interface EntityExtractor {
    List<CodeEntity> extractEntities(Path file) throws IOException;
}
