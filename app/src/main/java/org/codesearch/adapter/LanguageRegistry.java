package org.codesearch.adapter;

import java.util.Collection;
import java.util.Optional;

public interface LanguageRegistry {

    Optional<LanguageAdapter> find(String language);

    LanguageAdapter require(String language);

    Collection<LanguageAdapter> all();

    Optional<LanguageAdapter> detectByFile(String filePath);
}
