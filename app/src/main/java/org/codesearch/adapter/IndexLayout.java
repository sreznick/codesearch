package org.codesearch.adapter;

/**
 * How language indexes are laid out on disk under the configured index root.
 */
public enum IndexLayout {
    /**
     * Each language gets its own subdirectory, e.g. {@code index/java/}.
     */
    PER_LANGUAGE_SUBDIRECTORY,

    /**
     * The index root is the Lucene directory used by the CLI today ({@code index/}).
     */
    CLI_LEGACY_FLAT
}
