package org.codesearch.adapter;

import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeSearchFacadeTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldIndexAndSearchThroughFacade() throws Exception {
        Path indexRoot = tempDir.resolve("indexes");
        Path source = Path.of("src/test/resources");
        CodeSearchFacade facade = CodeSearchFacade.withDefaults(indexRoot);

        facade.index("java", source);

        SearchResponse response = facade.search(
                "java",
                SearchRequest.builder("TestClass")
                        .kind(EntityKind.CLASS)
                        .build()
        );

        assertEquals(1, response.totalHits());
        assertEquals("TestClass", response.results().getFirst().entity().content());
        assertTrue(facade.indexDirectory("java").toFile().exists());
    }

    @Test
    void shouldSearchDeclaredTypeThroughFacade() throws Exception {
        Path indexRoot = tempDir.resolve("indexes-declared");
        CodeSearchFacade facade = CodeSearchFacade.withDefaults(indexRoot);
        facade.index("java", Path.of("src/test/resources"));

        SearchResponse response = facade.search(
                "java",
                SearchRequest.builder("String")
                        .kind(EntityKind.FIELD)
                        .target(SearchTarget.DECLARED_TYPE)
                        .caseSensitive(true)
                        .build()
        );

        assertTrue(response.totalHits() >= 1);
        assertEquals(EntityKind.FIELD, response.results().getFirst().entity().kind());
    }

    @Test
    void shouldRejectUnsupportedLanguage() {
        CodeSearchFacade facade = CodeSearchFacade.withDefaults(tempDir.resolve("indexes"));

        assertThrows(
                IllegalArgumentException.class,
                () -> facade.search(
                        "python",
                        SearchRequest.builder("foo").kind(EntityKind.CLASS).build()
                )
        );
    }

    @Test
    void shouldThrowWhenIndexMissing() {
        CodeSearchFacade facade = CodeSearchFacade.withDefaults(tempDir.resolve("missing-index"));

        assertThrows(
                IndexUnavailableException.class,
                () -> facade.search(
                        "java",
                        SearchRequest.builder("TestClass").kind(EntityKind.CLASS).build()
                )
        );
    }
}
