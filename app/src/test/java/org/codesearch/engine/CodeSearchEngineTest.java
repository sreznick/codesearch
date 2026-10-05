package org.codesearch.engine;

import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.IndexStats;
import org.codesearch.core.IndexUnavailableException;
import org.codesearch.core.MatchMode;
import org.codesearch.core.SearchRequest;
import org.codesearch.core.SearchResponse;
import org.codesearch.core.SearchTarget;
import org.codesearch.index.IndexReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeSearchEngineTest {
    private static final Path RESOURCES = Path.of("src/test/resources");

    @TempDir
    Path indexRoot;

    private CodeSearchEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CodeSearchEngine(LanguageRegistry.withDefaults(), indexRoot);
    }

    @Test
    void shouldIndexEveryDetectedLanguageIntoItsOwnDirectory() throws Exception {
        List<IndexReport> reports = engine.index(null, RESOURCES);

        assertEquals(List.of("go", "java", "python"), reports.stream().map(IndexReport::language).sorted().toList());
        assertTrue(reports.stream().allMatch(report -> report.entities() > 0));
        assertEquals(List.of("java", "go", "python"),
                engine.indexedPlugins().stream().map(plugin -> plugin.language()).toList());
    }

    @Test
    void shouldKeepOtherLanguageIndexesWhenReindexingOneLanguage() throws Exception {
        engine.index("java", RESOURCES);
        engine.index("go", RESOURCES.resolve("go"));

        SearchResponse javaClasses = engine.search("java", SearchRequest.builder("TestClass").kind(EntityKind.CLASS).build());
        SearchResponse goStructs = engine.search("go", SearchRequest.builder("BitSet").kind(EntityKind.STRUCT).build());

        assertEquals(1, javaClasses.totalHits());
        assertEquals(1, goStructs.totalHits());
    }

    @Test
    void shouldReturnFullEntityFromIndex() throws Exception {
        engine.index("java", RESOURCES);

        CodeEntity field = engine.search("java", SearchRequest.builder("testField").kind(EntityKind.FIELD).build())
                .results().getFirst().entity();

        assertEquals("String", field.declaredType());
        assertEquals("src/test/resources/TestClass.java", field.location().filePath());
        assertEquals(7, field.location().line());
        assertEquals("java", field.language());
        assertEquals("TestClass", field.attribute("containerName"));
    }

    @Test
    void shouldSearchAllLanguagesAndSkipThoseWithoutRequestedKind() throws Exception {
        engine.index(null, RESOURCES);

        SearchResponse interfaces = engine.search(null, SearchRequest.builder("e")
                .kind(EntityKind.INTERFACE).matchMode(MatchMode.SUBSTRING).build());
        SearchResponse structs = engine.search(null, SearchRequest.listKind(EntityKind.STRUCT).build());

        assertTrue(interfaces.results().stream().anyMatch(r -> r.entity().language().equals("java")));
        assertTrue(interfaces.results().stream().anyMatch(r -> r.entity().language().equals("go")));
        assertTrue(structs.results().stream().allMatch(r -> r.entity().language().equals("go")));
    }

    @Test
    void shouldFindAssignableVariablesInEveryLanguage() throws Exception {
        engine.index(null, RESOURCES);

        SearchResponse java = engine.search(null, SearchRequest.builder("Animal").target(SearchTarget.ASSIGNABLE_TYPE).build());
        SearchResponse python = engine.search(null, SearchRequest.builder("Entity").target(SearchTarget.ASSIGNABLE_TYPE).build());

        assertEquals(List.of("dog"), java.results().stream().map(r -> r.entity().content()).toList());
        assertTrue(python.results().stream().map(r -> r.entity().content()).toList().containsAll(List.of("product", "order")));
    }

    @Test
    void shouldDistinguishExactSubstringAndFuzzyMatching() throws Exception {
        engine.index("java", RESOURCES);

        long exact = engine.search("java", SearchRequest.builder("testField").kind(EntityKind.FIELD).build()).totalHits();
        long substring = engine.search("java", SearchRequest.builder("testField").kind(EntityKind.FIELD)
                .matchMode(MatchMode.SUBSTRING).build()).totalHits();
        long fuzzy = engine.search("java", SearchRequest.builder("tesField").kind(EntityKind.FIELD)
                .matchMode(MatchMode.FUZZY).build()).totalHits();

        assertEquals(1, exact);
        assertEquals(2, substring);
        assertTrue(fuzzy >= 1);
    }

    @Test
    void shouldLimitResultsWithoutChangingTotalHits() throws Exception {
        engine.index("java", RESOURCES);

        SearchResponse response = engine.search("java", SearchRequest.builder("String")
                .kind(EntityKind.FIELD).target(SearchTarget.DECLARED_TYPE).limit(1).build());

        assertEquals(3, response.totalHits());
        assertEquals(1, response.results().size());
        assertEquals("testField", response.results().getFirst().entity().content());
    }

    @Test
    void shouldFilterResultsByPath() throws Exception {
        engine.index(null, RESOURCES);

        SearchResponse response = engine.search(null, SearchRequest.builder("main")
                .matchMode(MatchMode.SUBSTRING).pathFilter("go/").build());

        assertTrue(response.totalHits() > 0);
        assertTrue(response.results().stream().allMatch(r -> r.entity().location().filePath().contains("go/")));
    }

    @Test
    void shouldReportStatsPerLanguage() throws Exception {
        engine.index(null, RESOURCES);

        Map<String, IndexStats> stats = engine.stats(null, null);

        assertEquals(1, stats.get("java").totalFiles());
        assertEquals(1, stats.get("python").totalFiles());
        assertTrue(stats.get("go").entitiesByKind().get(EntityKind.STRUCT) >= 2);
    }

    @Test
    void shouldRejectKindUnsupportedByExplicitLanguage() throws Exception {
        engine.index("go", RESOURCES.resolve("go"));

        assertThrows(IllegalArgumentException.class,
                () -> engine.search("go", SearchRequest.builder("Foo").kind(EntityKind.ANNOTATION).build()));
    }

    @Test
    void shouldReportMissingIndex() {
        assertThrows(IndexUnavailableException.class,
                () -> engine.search("java", SearchRequest.builder("TestClass").kind(EntityKind.CLASS).build()));
        assertThrows(IndexUnavailableException.class,
                () -> engine.search(null, SearchRequest.builder("TestClass").kind(EntityKind.CLASS).build()));
    }

    @Test
    void shouldFindSubtypesInEveryLanguage() throws Exception {
        engine.index(null, RESOURCES);

        assertEquals(List.of("Dog"), names(engine.search(null, subtypesOf("Animal"))));
        assertEquals(List.of("Order", "Product"), names(engine.search(null, subtypesOf("Entity"))).stream().sorted().toList());
        assertEquals(List.of("Cube", "Solid", "Square"), names(engine.search(null, subtypesOf("Shape"))).stream().sorted().toList());
        assertEquals(List.of("Cube"), names(engine.search(null, subtypesOf("Solid"))));
    }

    @Test
    void shouldFindCallSitesWithContainers() throws Exception {
        engine.index(null, RESOURCES);

        SearchResponse response = engine.search(null, SearchRequest.builder("Area").kind(EntityKind.CALL).build());
        CodeEntity call = response.results().getFirst().entity();

        assertEquals(1, response.totalHits());
        assertEquals("shape", call.attribute("callQualifier"));
        assertEquals("Describe", call.attribute("containerName"));
    }

    @Test
    void shouldFilterByContainer() throws Exception {
        engine.index(null, RESOURCES);

        SearchResponse methods = engine.search(null, SearchRequest.listKind(EntityKind.METHOD).containerFilter("order").build());
        SearchResponse calls = engine.search(null, SearchRequest.listKind(EntityKind.CALL).containerFilter("list_products").build());

        assertEquals(List.of("total"), names(methods));
        assertEquals(List.of("make_product", "Order"), names(calls));
    }

    @Test
    void shouldQuickSearchOnlyLanguagesSupportingRequest() throws Exception {
        SearchResponse response = CodeSearchEngine.quickSearch(LanguageRegistry.withDefaults(), null, RESOURCES,
                SearchRequest.builder("Demo").kind(EntityKind.ANNOTATION).matchMode(MatchMode.SUBSTRING).build());

        assertEquals(4, response.totalHits());
        assertTrue(response.results().stream().allMatch(r -> r.entity().language().equals("java")));
    }

    private static SearchRequest subtypesOf(String type) {
        return SearchRequest.builder(type).target(SearchTarget.SUPERTYPE).build();
    }

    private static List<String> names(SearchResponse response) {
        return response.results().stream().map(result -> result.entity().content()).toList();
    }
}
