package org.codesearch.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityKindTest {
    @Test
    void shouldResolveLegacyJavaType() {
        assertEquals(EntityKind.STRING_CONSTANT, EntityKind.fromValue("StringConstant"));
        assertEquals(EntityKind.LOCAL_VARIABLE, EntityKind.fromValue("LocalVariable"));
    }

    @Test
    void shouldResolveNormalizedKey() {
        assertEquals(EntityKind.STRING_LITERAL, EntityKind.fromValue("string_literal"));
        assertEquals(EntityKind.METHOD, EntityKind.fromValue("method"));
    }

    @Test
    void shouldRejectUnsupportedValue() {
        assertThrows(IllegalArgumentException.class, () -> EntityKind.fromValue("unknown_kind"));
    }
}
