package org.codesearch.plugin;

import org.codesearch.core.EntityExtractor;
import org.codesearch.core.IndexingContext;
import org.codesearch.core.LanguageModule;
import org.codesearch.core.LanguageSearchCapabilities;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;


public interface LanguagePlugin extends LanguageModule {

    EntityExtractor createExtractor(IndexingContext context);

    LanguageSearchCapabilities searchCapabilities();

    default IndexingContext prepareIndexing(List<Path> sourceFiles) throws IOException {
        return IndexingContext.EMPTY;
    }
}
