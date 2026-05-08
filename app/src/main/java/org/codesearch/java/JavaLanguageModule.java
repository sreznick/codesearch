package org.codesearch.java;

import org.codesearch.core.EntityKind;
import org.codesearch.core.LanguageModule;

import java.util.Set;

public class JavaLanguageModule implements LanguageModule {
    public static final String LANGUAGE = "java";

    private static final Set<String> FILE_EXTENSIONS = Set.of(".java");
    private static final Set<EntityKind> SUPPORTED_ENTITY_KINDS = Set.of(
            EntityKind.CLASS,
            EntityKind.INTERFACE,
            EntityKind.METHOD,
            EntityKind.FIELD,
            EntityKind.LOCAL_VARIABLE,
            EntityKind.STRING_CONSTANT,
            EntityKind.INTEGER_LITERAL,
            EntityKind.FLOAT_LITERAL,
            EntityKind.BOOLEAN_LITERAL,
            EntityKind.CHAR_LITERAL,
            EntityKind.STRING_LITERAL
    );

    @Override
    public String language() {
        return LANGUAGE;
    }

    @Override
    public Set<String> fileExtensions() {
        return FILE_EXTENSIONS;
    }

    @Override
    public Set<EntityKind> supportedEntityKinds() {
        return SUPPORTED_ENTITY_KINDS;
    }
}
