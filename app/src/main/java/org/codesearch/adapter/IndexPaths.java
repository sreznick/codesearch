package org.codesearch.adapter;

import java.nio.file.Path;

public final class IndexPaths {
    private IndexPaths() {}

    public static Path languageIndexDir(Path indexRoot, String language) {
        return indexRoot.resolve(language.trim().toLowerCase());
    }

    public static Path resolve(Path indexRoot, String language, IndexLayout layout) {
        if (layout == IndexLayout.CLI_LEGACY_FLAT) {
            return indexRoot;
        }
        return languageIndexDir(indexRoot, language);
    }
}
