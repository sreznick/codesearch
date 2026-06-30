package org.codesearch.adapter.java;

import org.codesearch.adapter.IndexUnavailableException;
import org.codesearch.adapter.SearchRequest;
import org.codesearch.adapter.SearchResponse;
import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaLanguageAdapterTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldWrapExistingJavaSearchImplementation() throws Exception {
        JavaLanguageAdapter adapter = new JavaLanguageAdapter();
        Path indexDir = tempDir.resolve("java-index");
        Path source = Path.of("src/test/resources");

        adapter.indexSources(source, indexDir);

        SearchResponse byName = adapter.search(
                indexDir,
                SearchRequest.builder("testField")
                        .kind(EntityKind.FIELD)
                        .build()
        );
        SearchResponse byType = adapter.search(
                indexDir,
                SearchRequest.builder("String")
                        .kind(EntityKind.FIELD)
                        .target(SearchTarget.DECLARED_TYPE)
                        .caseSensitive(true)
                        .build()
        );

        assertEquals(1, byName.totalHits());
        assertEquals("testField", byName.results().getFirst().entity().content());
        assertTrue(byType.totalHits() >= 1);
    }

    @Test
    void shouldSearchAssignableTypeWithoutExplicitKind() throws Exception {
        JavaLanguageAdapter adapter = new JavaLanguageAdapter();
        Path indexDir = tempDir.resolve("java-assignable");
        adapter.indexSources(Path.of("src/test/resources"), indexDir);

        SearchResponse response = adapter.search(
                indexDir,
                SearchRequest.builder("String")
                        .target(SearchTarget.ASSIGNABLE_TYPE)
                        .build()
        );

        assertTrue(response.totalHits() >= 1);
    }

    @Test
    void shouldExposeJavaCapabilities() {
        JavaLanguageAdapter adapter = new JavaLanguageAdapter();

        assertTrue(adapter.supportedSearchTargets().contains(SearchTarget.ASSIGNABLE_TYPE));
        assertTrue(adapter.supportedSearchKindAliases().contains("field-type"));
        assertTrue(adapter.supportedEntityKinds().contains(EntityKind.METHOD));
    }

    @Test
    void shouldMapIndexUnavailableErrors() {
        JavaLanguageAdapter adapter = new JavaLanguageAdapter();

        assertThrows(IndexUnavailableException.class, () -> adapter.search(
                tempDir.resolve("no-index"),
                SearchRequest.builder("TestClass").kind(EntityKind.CLASS).build()
        ));
    }
}
