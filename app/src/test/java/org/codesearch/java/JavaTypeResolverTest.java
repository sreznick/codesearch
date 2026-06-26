package org.codesearch.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaTypeResolverTest {
    @Test
    void shouldInferVarTypeFromNormalNewExpression() {
        String resolvedType = JavaTypeResolver.resolveDeclaredType("var", "new StringBuilder(\"hello\")");

        assertEquals("StringBuilder", resolvedType);
        assertTrue(JavaTypeResolver.assignableTypes(resolvedType).contains("Appendable"));
    }

    @Test
    void shouldInferVarTypeFromAntlrCompactedNewExpression() {
        String resolvedType = JavaTypeResolver.resolveDeclaredType("var", "newStringBuilder(\"hello\")");

        assertEquals("StringBuilder", resolvedType);
        assertTrue(JavaTypeResolver.assignableTypes(resolvedType).contains("Appendable"));
    }
}
