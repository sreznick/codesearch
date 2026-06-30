/**
 * Language adapter layer for codesearch.
 *
 * <p>To add a new language:
 * <ol>
 *   <li>Implement {@link org.codesearch.plugin.LanguagePlugin} in {@code org.codesearch.<lang>}:
 *       metadata, {@link org.codesearch.core.EntityExtractor}, and {@link org.codesearch.core.LanguageSearchCapabilities}.</li>
 *   <li>Register {@link org.codesearch.adapter.PluginLanguageAdapter} in {@link org.codesearch.adapter.DefaultLanguageRegistry}.</li>
 *   <li>CLI ({@link org.codesearch.adapter.cli.CliBridge}) indexes and searches across all detected languages by default;
 *       use {@code --lang java} to narrow to one language.</li>
 * </ol>
 *
 * <p>Shared indexing and search live in {@code org.codesearch.core}; language plugins only supply parsing and capabilities.
 */
package org.codesearch.adapter;
