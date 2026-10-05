package org.codesearch.go;

import org.codesearch.core.EntityKind;
import org.codesearch.plugin.EntityExtractor;
import org.codesearch.plugin.LanguagePlugin;
import org.codesearch.plugin.LanguageSearchCapabilities;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class GoLanguagePlugin implements LanguagePlugin {
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
            EntityKind.LOCAL_VARIABLE,
            EntityKind.CONSTANT,
            EntityKind.CALL,
            EntityKind.STRING_LITERAL,
            EntityKind.INTEGER_LITERAL,
            EntityKind.FLOAT_LITERAL
    );
    private static final LanguageSearchCapabilities SEARCH_CAPABILITIES = new LanguageSearchCapabilities(
            Set.of(EntityKind.FIELD, EntityKind.VARIABLE, EntityKind.LOCAL_VARIABLE, EntityKind.CONSTANT,
                    EntityKind.FUNCTION, EntityKind.METHOD),
            Set.of(EntityKind.FIELD, EntityKind.VARIABLE, EntityKind.LOCAL_VARIABLE),
            Set.of(EntityKind.STRUCT, EntityKind.INTERFACE),
            GoTypes::searchableName
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

    @Override
    public LanguageSearchCapabilities searchCapabilities() {
        return SEARCH_CAPABILITIES;
    }

    @Override
    public EntityExtractor createExtractor(List<Path> sourceFiles) {
        return new GoEntityExtractor(GoProjectTypes.collect(sourceFiles));
    }
}
