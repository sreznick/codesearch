package org.codesearch.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchQueryTest {
    @Test
    void shouldNormalizeLanguageAndCopyAttributes() {
        CodeEntity entity = new CodeEntity(
                EntityKind.FIELD,
                "userId",
                new EntityLocation("src/User.java", 12, 4),
                " Java ",
                "String",
                Map.of("visibility", "private")
        );
        SearchQuery query = SearchQuery.builder(" userId ", EntityKind.FIELD, " JAVA ")
                .caseSensitive(true)
                .limit(25)
                .pathFilter(" src/main ")
                .build();

        assertEquals("java", entity.language());
        assertEquals("userId", query.text());
        assertEquals("java", query.language());
        assertEquals(SearchTarget.CONTENT, query.target());
        assertEquals("src/main", query.pathFilter());
        assertEquals("private", entity.attributes().get("visibility"));
    }

    @Test
    void shouldTreatBlankLanguageAsAnyLanguage() {
        SearchQuery query = SearchQuery.builder("test", EntityKind.METHOD, "   ").build();

        assertNull(query.language());
        assertEquals(100, query.limit());
    }

    @Test
    void shouldRejectInvalidQueryValues() {
        assertThrows(IllegalArgumentException.class, () -> SearchQuery.builder("  ", EntityKind.CLASS, "java").build());
        assertThrows(IllegalArgumentException.class, () -> SearchQuery.builder("name", EntityKind.CLASS, "java").limit(0).build());
    }
}
