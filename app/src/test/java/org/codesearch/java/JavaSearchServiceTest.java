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
import java.nio.file.Files;
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

        assertEquals(3, results.size());
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
    void shouldSearchVariablesAssignableToInterface() throws IOException {
        JavaSearchService.SearchResponse response = SEARCH_SERVICE.searchAssignableVariables(
                "Appendable",
                "java",
                true,
                100,
                null
        );

        assertEquals(1, response.totalHits());
        CodeEntity entity = response.results().getFirst().entity();
        assertEquals(EntityKind.LOCAL_VARIABLE, entity.kind());
        assertEquals("inferredBuilder", entity.content());
        assertEquals("StringBuilder", entity.declaredType());
        assertTrue(entity.attributes().get("assignableTypes").contains("Appendable"));
    }

    @Test
    void shouldSearchInferredStringVariableAsCharSequence() throws IOException {
        JavaSearchService.SearchResponse response = SEARCH_SERVICE.searchAssignableVariables(
                "CharSequence",
                "java",
                true,
                100,
                null
        );

        assertTrue(response.results().stream().anyMatch(result ->
                result.entity().kind() == EntityKind.LOCAL_VARIABLE
                        && result.entity().content().equals("inferredText")
                        && result.entity().declaredType().equals("String")));
    }

    @Test
    void shouldSearchVariableAssignableToProjectInterface(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Printable.java"), """
                interface Printable {
                    void print();
                }
                """);
        Files.writeString(sourcePath.resolve("Report.java"), """
                class Report implements Printable {
                    public void print() {
                    }
                }
                """);
        Files.writeString(sourcePath.resolve("ReportUsage.java"), """
                class ReportUsage {
                    void run() {
                        var report = new Report();
                    }
                }
                """);

        JavaSourceIndexer.indexJavaSources(sourcePath.toString(), indexPath);
        JavaSearchService.SearchResponse response = new JavaSearchService(indexPath).searchAssignableVariables(
                "Printable",
                "java",
                true,
                100,
                null
        );

        assertEquals(1, response.totalHits());
        CodeEntity entity = response.results().getFirst().entity();
        assertEquals(EntityKind.LOCAL_VARIABLE, entity.kind());
        assertEquals("report", entity.content());
        assertEquals("Report", entity.declaredType());
        assertTrue(entity.attributes().get("assignableTypes").contains("Printable"));
        assertEquals("var -> Report", entity.attributes().get("typeInference"));
    }

    @Test
    void shouldSearchVariableAssignableToProjectSuperclass(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Animal.java"), """
                class Animal {
                }
                """);
        Files.writeString(sourcePath.resolve("Dog.java"), """
                class Dog extends Animal {
                }
                """);
        Files.writeString(sourcePath.resolve("DogUsage.java"), """
                class DogUsage {
                    void run() {
                        var dog = new Dog();
                    }
                }
                """);

        JavaSourceIndexer.indexJavaSources(sourcePath.toString(), indexPath);
        JavaSearchService.SearchResponse response = new JavaSearchService(indexPath).searchAssignableVariables(
                "Animal",
                "java",
                true,
                100,
                null
        );

        assertEquals(1, response.totalHits());
        CodeEntity entity = response.results().getFirst().entity();
        assertEquals(EntityKind.LOCAL_VARIABLE, entity.kind());
        assertEquals("dog", entity.content());
        assertEquals("Dog", entity.declaredType());
        assertTrue(entity.attributes().get("assignableTypes").contains("Animal"));
        assertEquals("var -> Dog", entity.attributes().get("typeInference"));
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
