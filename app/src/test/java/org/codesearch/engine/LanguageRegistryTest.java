package org.codesearch.engine;

import org.codesearch.java.JavaLanguagePlugin;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageRegistryTest {
    private final LanguageRegistry registry = LanguageRegistry.withDefaults();

    @Test
    void shouldRegisterDefaultLanguagesInStableOrder() {
        assertEquals(List.of("java", "go", "python"), registry.languages());
    }

    @Test
    void shouldFindLanguageIgnoringCase() {
        assertEquals("python", registry.require(" Python ").language());
        assertTrue(registry.find("rust").isEmpty());
    }

    @Test
    void shouldExplainUnsupportedLanguage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> registry.require("rust"));
        assertEquals("Неподдерживаемый язык: rust", error.getMessage());
    }

    @Test
    void shouldRejectDuplicatePlugins() {
        assertThrows(IllegalArgumentException.class,
                () -> new LanguageRegistry(List.of(new JavaLanguagePlugin(), new JavaLanguagePlugin())));
    }
}
