package org.codesearch.java;

import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchQuery;
import org.codesearch.core.SearchResult;
import org.example.JavaSourceIndexer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSearchServiceTest {
    private static final JavaSearchService SEARCH_SERVICE = new JavaSearchService(Path.of("index"));

    @BeforeAll
    static void buildIndex() throws IOException, InterruptedException {
        JavaSourceIndexer.indexJavaSources("src/test/resources");
    }

    @Test
    void shouldFindClassAsCoreSearchResult() throws IOException {
        SearchQuery query = new SearchQuery("TestClass", EntityKind.CLASS, "java");

        List<SearchResult> results = SEARCH_SERVICE.search(query);

        assertEquals(1, results.size());
        CodeEntity entity = results.getFirst().entity();
        assertEquals(EntityKind.CLASS, entity.kind());
        assertEquals("TestClass", entity.content());
        assertEquals("src/test/resources/TestClass.java", entity.location().filePath());
        assertEquals(6, entity.location().line());
        assertEquals("java", entity.language());
    }

    @Test
    void shouldCarryDeclaredTypeForField() throws IOException {
        SearchQuery query = new SearchQuery("testField", EntityKind.FIELD, "java");

        List<SearchResult> results = SEARCH_SERVICE.search(query);

        assertEquals(1, results.size());
        CodeEntity entity = results.getFirst().entity();
        assertEquals(EntityKind.FIELD, entity.kind());
        assertEquals("String", entity.declaredType());
        assertEquals("String", entity.attributes().get("declaredType"));
    }

    @Test
    void shouldReturnNothingForOtherLanguage() throws IOException {
        SearchQuery query = new SearchQuery("TestClass", EntityKind.CLASS, "go");

        List<SearchResult> results = SEARCH_SERVICE.search(query);

        assertTrue(results.isEmpty());
    }

    @Test
    void shouldRejectQueryWithoutKind() {
        SearchQuery query = new SearchQuery("TestClass", null, "java");

        assertThrows(IllegalArgumentException.class, () -> SEARCH_SERVICE.search(query));
    }
}
