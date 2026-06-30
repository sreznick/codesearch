package org.codesearch.adapter;

import org.codesearch.adapter.java.JavaLanguageAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLanguageRegistryTest {

    @Test
    void shouldRegisterDefaultJavaAdapter() {
        LanguageRegistry registry = DefaultLanguageRegistry.withDefaults();

        LanguageAdapter adapter = registry.require("java");

        assertEquals("java", adapter.language());
        assertTrue(adapter.supportsFile("Main.java"));
    }

    @Test
    void shouldRejectUnknownLanguage() {
        LanguageRegistry registry = DefaultLanguageRegistry.withDefaults();

        assertThrows(IllegalArgumentException.class, () -> registry.require("python"));
    }

    @Test
    void shouldRegisterGoAdapter() {
        LanguageRegistry registry = DefaultLanguageRegistry.withDefaults();

        LanguageAdapter adapter = registry.require("go");

        assertEquals("go", adapter.language());
        assertTrue(adapter.supportsFile("main.go"));
    }

    @Test
    void shouldDetectLanguageByFileExtension() {
        LanguageRegistry registry = DefaultLanguageRegistry.withDefaults();

        LanguageAdapter adapter = registry.detectByFile("src/Main.java").orElseThrow();

        assertEquals("java", adapter.language());
    }

    @Test
    void shouldRejectDuplicateLanguageRegistration() {
        assertThrows(IllegalArgumentException.class, () ->
                new DefaultLanguageRegistry(java.util.List.of(new JavaLanguageAdapter(), new JavaLanguageAdapter()))
        );
    }
}
