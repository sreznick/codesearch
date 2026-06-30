package org.codesearch.adapter.go;

import org.codesearch.adapter.SearchRequest;
import org.codesearch.adapter.SearchResponse;
import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoLanguageAdapterTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldIndexAndSearchGoSources() throws Exception {
        GoLanguageAdapter adapter = new GoLanguageAdapter();
        Path indexDir = tempDir.resolve("go-index");
        Path source = Path.of("src/test/resources");

        adapter.indexSources(source, indexDir);

        SearchResponse byStruct = adapter.search(
                indexDir,
                SearchRequest.builder("Service")
                        .kind(EntityKind.CLASS)
                        .build()
        );
        SearchResponse byMethod = adapter.search(
                indexDir,
                SearchRequest.builder("Address")
                        .kind(EntityKind.METHOD)
                        .build()
        );
        SearchResponse byFieldType = adapter.search(
                indexDir,
                SearchRequest.builder("string")
                        .kind(EntityKind.FIELD)
                        .target(SearchTarget.DECLARED_TYPE)
                        .caseSensitive(true)
                        .build()
        );
        SearchResponse byLocalVarType = adapter.search(
                indexDir,
                SearchRequest.builder("string")
                        .kind(EntityKind.LOCAL_VARIABLE)
                        .target(SearchTarget.DECLARED_TYPE)
                        .caseSensitive(true)
                        .build()
        );
        SearchResponse byAssignable = adapter.search(
                indexDir,
                SearchRequest.builder("*Service")
                        .target(SearchTarget.ASSIGNABLE_TYPE)
                        .caseSensitive(true)
                        .build()
        );

        assertEquals(1, byStruct.totalHits());
        assertTrue(byMethod.totalHits() >= 1);
        assertEquals(1, byFieldType.totalHits());
        assertTrue(byLocalVarType.totalHits() >= 1);
        assertTrue(byAssignable.totalHits() >= 1);
    }

    @Test
    void shouldExposeGoCapabilities() {
        GoLanguageAdapter adapter = new GoLanguageAdapter();

        assertEquals("go", adapter.language());
        assertTrue(adapter.supportsFile("main.go"));
        assertTrue(adapter.supportedSearchTargets().contains(SearchTarget.DECLARED_TYPE));
        assertTrue(adapter.supportedSearchTargets().contains(SearchTarget.ASSIGNABLE_TYPE));
    }
}
