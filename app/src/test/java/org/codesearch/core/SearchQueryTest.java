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
        SearchQuery query = new SearchQuery(" userId ", EntityKind.FIELD, " JAVA ", false, true, 25);

        assertEquals("java", entity.language());
        assertEquals("userId", query.text());
        assertEquals("java", query.language());
        assertEquals("private", entity.attributes().get("visibility"));
    }

    @Test
    void shouldTreatBlankLanguageAsAnyLanguage() {
        SearchQuery query = new SearchQuery("test", EntityKind.METHOD, "   ");

        assertNull(query.language());
        assertEquals(100, query.limit());
    }

    @Test
    void shouldRejectInvalidQueryValues() {
        assertThrows(IllegalArgumentException.class, () -> new SearchQuery("  ", EntityKind.CLASS, "java"));
        assertThrows(IllegalArgumentException.class, () -> new SearchQuery("name", EntityKind.CLASS, "java", false, false, 0));
    }
}
