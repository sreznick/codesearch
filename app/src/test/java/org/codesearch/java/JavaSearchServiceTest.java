package org.codesearch.java;

import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchQuery;
import org.codesearch.core.SearchResult;
import org.codesearch.core.SearchTarget;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    void shouldSearchFieldByDeclaredType() throws IOException {
        SearchQuery query = new SearchQuery("String", EntityKind.FIELD, "java", SearchTarget.DECLARED_TYPE, false, true, 100);

        List<SearchResult> results = SEARCH_SERVICE.search(query);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(result -> result.entity().kind() == EntityKind.FIELD));
        assertTrue(results.stream().allMatch(result -> "String".equals(result.entity().declaredType())));
    }

    @Test
    void shouldSearchLocalVariableByDeclaredTypeIgnoringCase() throws IOException {
        SearchQuery query = new SearchQuery("string", EntityKind.LOCAL_VARIABLE, "java", SearchTarget.DECLARED_TYPE, false, false, 100);

        List<SearchResult> results = SEARCH_SERVICE.search(query);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(result -> result.entity().kind() == EntityKind.LOCAL_VARIABLE));
        assertTrue(results.stream().allMatch(result -> "String".equals(result.entity().declaredType())));
    }

    @Test
    void shouldSearchMethodByReturnType() throws IOException {
        SearchQuery query = new SearchQuery("String", EntityKind.METHOD, "java", SearchTarget.DECLARED_TYPE, false, true, 100);

        List<SearchResult> results = SEARCH_SERVICE.search(query);

        assertEquals(1, results.size());
        CodeEntity entity = results.getFirst().entity();
        assertEquals(EntityKind.METHOD, entity.kind());
        assertEquals("getTestField", entity.content());
        assertEquals("String", entity.declaredType());
    }

    @Test
    void shouldLimitReturnedResultsWithoutChangingTotalHits() throws IOException {
        SearchQuery query = new SearchQuery("String", EntityKind.FIELD, "java", SearchTarget.DECLARED_TYPE, false, true, 1);

        JavaSearchService.SearchResponse response = SEARCH_SERVICE.searchWithMetadata(query);

        assertEquals(2, response.totalHits());
        assertEquals(1, response.results().size());
    }

    @Test
    void shouldFilterResultsByPath() throws IOException {
        SearchQuery query = new SearchQuery(
                "TestClass",
                EntityKind.CLASS,
                "java",
                SearchTarget.CONTENT,
                false,
                true,
                100,
                "missing/path"
        );

        JavaSearchService.SearchResponse response = SEARCH_SERVICE.searchWithMetadata(query);

        assertEquals(0, response.totalHits());
        assertTrue(response.results().isEmpty());
    }

    @Test
    void shouldReportMissingIndex(@TempDir Path tempDir) {
        JavaSearchService searchService = new JavaSearchService(tempDir.resolve("missing-index"));
        SearchQuery query = new SearchQuery("TestClass", EntityKind.CLASS, "java");

        JavaSearchService.IndexUnavailableException exception = assertThrows(
                JavaSearchService.IndexUnavailableException.class,
                () -> searchService.search(query)
        );

        assertTrue(exception.getMessage().contains("Индекс не найден"));
    }
}
