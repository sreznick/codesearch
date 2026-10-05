package org.codesearch.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchRequestTest {
    @Test
    void shouldNormalizeTextAndPathFilter() {
        SearchRequest request = SearchRequest.builder("  TestClass ").kind(EntityKind.CLASS).pathFilter("  ").build();

        assertEquals("TestClass", request.text());
        assertNull(request.pathFilter());
        assertEquals(SearchRequest.DEFAULT_LIMIT, request.limit());
        assertEquals(MatchMode.EXACT, request.matchMode());
    }

    @Test
    void shouldAllowListingByKindWithoutText() {
        assertTrue(SearchRequest.listKind(EntityKind.CLASS).build().listsAllOfKind());
        assertThrows(IllegalArgumentException.class, () -> SearchRequest.builder(" ").build());
    }

    @Test
    void shouldRequireKindForDeclaredTypeSearch() {
        assertThrows(IllegalArgumentException.class,
                () -> SearchRequest.builder("String").target(SearchTarget.DECLARED_TYPE).build());
    }

    @Test
    void shouldRejectNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> SearchRequest.builder("x").limit(0).build());
    }
}
