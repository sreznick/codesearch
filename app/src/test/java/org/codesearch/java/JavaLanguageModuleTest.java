package org.codesearch.java;

import org.codesearch.core.EntityKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaLanguageModuleTest {
    @Test
    void shouldReportSupportedJavaFeatures() {
        JavaLanguageModule module = new JavaLanguageModule();

        assertTrue(module.supportsFile("src/main/java/App.java"));
        assertFalse(module.supportsFile("src/main/go/main.go"));
        assertTrue(module.supportedEntityKinds().contains(EntityKind.METHOD));
        assertTrue(module.supportedEntityKinds().contains(EntityKind.STRING_LITERAL));
    }
}
