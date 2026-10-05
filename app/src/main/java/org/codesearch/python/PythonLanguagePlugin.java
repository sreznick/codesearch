package org.codesearch.python;

import org.codesearch.core.EntityKind;
import org.codesearch.plugin.EntityExtractor;
import org.codesearch.plugin.LanguagePlugin;
import org.codesearch.plugin.LanguageSearchCapabilities;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class PythonLanguagePlugin implements LanguagePlugin {
    public static final String LANGUAGE = "python";

    private static final Set<String> FILE_EXTENSIONS = Set.of(".py", ".pyi");
    private static final Set<EntityKind> SUPPORTED_ENTITY_KINDS = Set.of(
            EntityKind.IMPORT,
            EntityKind.CLASS,
            EntityKind.FUNCTION,
            EntityKind.METHOD,
            EntityKind.FIELD,
            EntityKind.VARIABLE,
            EntityKind.CONSTANT,
            EntityKind.LOCAL_VARIABLE,
            EntityKind.DECORATOR,
            EntityKind.CALL,
            EntityKind.STRING_LITERAL,
            EntityKind.INTEGER_LITERAL,
            EntityKind.FLOAT_LITERAL
    );
    private static final Set<EntityKind> VARIABLE_KINDS = Set.of(
            EntityKind.FIELD, EntityKind.VARIABLE, EntityKind.CONSTANT, EntityKind.LOCAL_VARIABLE
    );
    private static final LanguageSearchCapabilities SEARCH_CAPABILITIES = new LanguageSearchCapabilities(
            Set.of(EntityKind.FIELD, EntityKind.VARIABLE, EntityKind.CONSTANT, EntityKind.LOCAL_VARIABLE,
                    EntityKind.FUNCTION, EntityKind.METHOD),
            VARIABLE_KINDS,
            Set.of(EntityKind.CLASS),
            PythonTypes::searchableName
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
        return new PythonEntityExtractor(PythonProjectTypes.collect(sourceFiles));
    }
}
