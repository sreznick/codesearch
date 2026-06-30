package org.codesearch.java;

import org.codesearch.core.EntityExtractor;
import org.codesearch.core.EntityKind;
import org.codesearch.core.IndexingContext;
import org.codesearch.core.LanguageSearchCapabilities;
import org.codesearch.core.SearchTarget;
import org.codesearch.plugin.LanguagePlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JavaLanguagePlugin implements LanguagePlugin {
    private final JavaLanguageModule module = new JavaLanguageModule();

    @Override
    public String language() {
        return module.language();
    }

    @Override
    public Set<String> fileExtensions() {
        return module.fileExtensions();
    }

    @Override
    public Set<EntityKind> supportedEntityKinds() {
        return module.supportedEntityKinds();
    }

    @Override
    public EntityExtractor createExtractor(IndexingContext context) {
        JavaTypeHierarchy typeHierarchy = context.get(JavaTypeHierarchy.class);
        if (typeHierarchy == null) {
            typeHierarchy = JavaTypeHierarchy.empty();
        }
        final JavaTypeHierarchy hierarchy = typeHierarchy;
        return file -> new JavaEntityExtractor(hierarchy).extractEntities(file);
    }

    @Override
    public IndexingContext prepareIndexing(List<Path> sourceFiles) throws IOException {
        JavaTypeHierarchy hierarchy = JavaTypeHierarchyExtractor.extract(sourceFiles);
        return new IndexingContext(Map.of(JavaTypeHierarchy.class, hierarchy));
    }

    @Override
    public LanguageSearchCapabilities searchCapabilities() {
        return LanguageSearchCapabilities.builder()
                .searchTargets(Set.of(SearchTarget.CONTENT, SearchTarget.DECLARED_TYPE, SearchTarget.ASSIGNABLE_TYPE))
                .declaredTypeKinds(Set.of(EntityKind.FIELD, EntityKind.LOCAL_VARIABLE, EntityKind.METHOD))
                .assignableTypeSearch(new LanguageSearchCapabilities.AssignableTypeSearch() {
                    @Override
                    public String normalizeType(String rawType) {
                        return JavaTypeResolver.searchableTypeName(rawType);
                    }

                    @Override
                    public Set<EntityKind> variableKinds() {
                        return Set.of(EntityKind.FIELD, EntityKind.LOCAL_VARIABLE);
                    }
                })
                .build();
    }
}
