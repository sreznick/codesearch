package org.codesearch.adapter.cli;

import org.codesearch.adapter.CodeSearchFacade;
import org.codesearch.adapter.DefaultLanguageRegistry;
import org.codesearch.adapter.IndexUnavailableException;
import org.codesearch.adapter.LanguageScopes;
import org.codesearch.adapter.SearchRequest;
import org.codesearch.adapter.SearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliBridgeTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldUsePerLanguageIndexLayoutForCli() throws Exception {
        Path indexPath = tempDir.resolve("index");
        CliBridge bridge = CliBridge.forCliIndex(indexPath);

        bridge.index("java", Path.of("src/test/resources"));

        assertTrue(indexPath.resolve("java").toFile().exists());
        SearchResponse response = bridge.search(
                "java",
                SearchRequestMapper.contentSearch("TestClass", org.codesearch.core.EntityKind.CLASS, false, false, 10, null, false)
        );

        assertEquals(1, response.totalHits());
    }

    @Test
    void shouldIndexAndSearchAllLanguages() throws Exception {
        Path indexPath = tempDir.resolve("multi-index");
        CliBridge bridge = CliBridge.forCliIndex(indexPath);

        var indexed = bridge.index(LanguageScopes.ALL, Path.of("src/test/resources"));

        assertTrue(indexed.contains("java"));
        assertTrue(indexed.contains("go"));

        SearchResponse response = bridge.search(
                LanguageScopes.ALL,
                SearchRequestMapper.contentSearch("Service", org.codesearch.core.EntityKind.CLASS, false, false, 10, null, false)
        );

        assertTrue(response.totalHits() >= 1);
    }

    @Test
    void shouldDelegateQuickSearchWithoutPersistingIndex() throws Exception {
        CliBridge bridge = CliBridge.forCliIndex(tempDir.resolve("unused-index"));

        SearchResponse response = bridge.quickSearch(
                "java",
                Path.of("src/test/resources"),
                SearchRequestMapper.contentSearch("TestClass", org.codesearch.core.EntityKind.CLASS, false, false, 10, null, false)
        );

        assertEquals(1, response.totalHits());
    }

    @Test
    void shouldExposeRegisteredLanguages() {
        CliBridge bridge = CliBridge.forCliIndex(tempDir.resolve("index"));

        assertTrue(bridge.supportsLanguage("java"));
        assertTrue(bridge.supportsLanguage(LanguageScopes.ALL));
        assertEquals("java", bridge.facade().registry().require("java").language());
    }
}
