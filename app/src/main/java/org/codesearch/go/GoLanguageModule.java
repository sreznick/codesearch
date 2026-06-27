package org.codesearch.go;

import org.codesearch.core.EntityKind;
import org.codesearch.core.LanguageModule;

import java.util.Set;

public class GoLanguageModule implements LanguageModule {
    public static final String LANGUAGE = "go";

    private static final Set<String> FILE_EXTENSIONS = Set.of(".go");
    private static final Set<EntityKind> SUPPORTED_ENTITY_KINDS = Set.of(
            EntityKind.PACKAGE,
            EntityKind.IMPORT,
            EntityKind.FUNCTION,
            EntityKind.METHOD,
            EntityKind.STRUCT,
            EntityKind.INTERFACE,
            EntityKind.FIELD,
            EntityKind.VARIABLE,
            EntityKind.CONSTANT,
            EntityKind.STRING_LITERAL,
            EntityKind.INTEGER_LITERAL,
            EntityKind.FLOAT_LITERAL
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
