package org.codesearch.adapter;

/**
 * Language selection for multi-language indexing and search.
 */
public final class LanguageScopes {
    public static final String ALL = "all";

    private LanguageScopes() {}

    public static boolean isAll(String language) {
        return language == null || language.isBlank() || ALL.equalsIgnoreCase(language.trim());
    }

    public static String normalize(String language) {
        return isAll(language) ? ALL : language.trim().toLowerCase();
    }
}
