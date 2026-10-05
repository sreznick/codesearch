package org.codesearch.java;

import org.codesearch.core.EntityKind;
import org.codesearch.plugin.EntityExtractor;
import org.codesearch.plugin.LanguagePlugin;
import org.codesearch.plugin.LanguageSearchCapabilities;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class JavaLanguagePlugin implements LanguagePlugin {
    public static final String LANGUAGE = "java";

    private static final Set<String> FILE_EXTENSIONS = Set.of(".java");
    private static final Set<EntityKind> SUPPORTED_ENTITY_KINDS = Set.of(
            EntityKind.CLASS,
            EntityKind.RECORD,
            EntityKind.ENUM,
            EntityKind.INTERFACE,
            EntityKind.METHOD,
            EntityKind.FIELD,
            EntityKind.LOCAL_VARIABLE,
            EntityKind.ANNOTATION,
            EntityKind.CALL,
            EntityKind.STRING_CONSTANT,
            EntityKind.INTEGER_LITERAL,
            EntityKind.FLOAT_LITERAL,
            EntityKind.BOOLEAN_LITERAL,
            EntityKind.CHAR_LITERAL,
            EntityKind.STRING_LITERAL
    );
    private static final LanguageSearchCapabilities SEARCH_CAPABILITIES = new LanguageSearchCapabilities(
            Set.of(EntityKind.FIELD, EntityKind.LOCAL_VARIABLE, EntityKind.METHOD),
            Set.of(EntityKind.FIELD, EntityKind.LOCAL_VARIABLE),
            Set.of(EntityKind.CLASS, EntityKind.RECORD, EntityKind.ENUM, EntityKind.INTERFACE),
            JavaTypeResolver::searchableTypeName
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
        JavaTypeHierarchy hierarchy = JavaTypeHierarchyExtractor.extract(sourceFiles);
        JavaEntityExtractor extractor = new JavaEntityExtractor(hierarchy);
        return extractor::extractEntities;
    }
}
