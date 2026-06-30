package org.codesearch.go;

import org.codesearch.core.EntityExtractor;
import org.codesearch.core.EntityKind;
import org.codesearch.core.IndexingContext;
import org.codesearch.core.LanguageSearchCapabilities;
import org.codesearch.core.SearchTarget;
import org.codesearch.core.SourceIndexer;
import org.codesearch.plugin.LanguagePlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class GoLanguagePlugin implements LanguagePlugin {
    private final GoLanguageModule module = new GoLanguageModule();

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
        GoTypeHierarchy hierarchy = context.get(GoTypeHierarchy.class);
        return new GoEntityExtractor(hierarchy == null ? GoTypeHierarchy.empty() : hierarchy);
    }

    @Override
    public IndexingContext prepareIndexing(List<Path> sourceFiles) throws IOException {
        List<Path> goFiles = sourceFiles.stream()
                .filter(file -> file.toString().endsWith(".go"))
                .toList();
        GoTypeHierarchy hierarchy = GoTypeHierarchyExtractor.extract(goFiles);
        return new IndexingContext(Map.of(GoTypeHierarchy.class, hierarchy));
    }

    @Override
    public LanguageSearchCapabilities searchCapabilities() {
        return LanguageSearchCapabilities.builder()
                .searchTargets(Set.of(SearchTarget.CONTENT, SearchTarget.DECLARED_TYPE, SearchTarget.ASSIGNABLE_TYPE))
                .declaredTypeKinds(Set.of(EntityKind.FIELD, EntityKind.LOCAL_VARIABLE, EntityKind.METHOD))
                .assignableTypeSearch(new LanguageSearchCapabilities.AssignableTypeSearch() {
                    @Override
                    public String normalizeType(String rawType) {
                        return GoTypeResolver.searchableTypeName(rawType);
                    }

                    @Override
                    public Set<EntityKind> variableKinds() {
                        return Set.of(EntityKind.FIELD, EntityKind.LOCAL_VARIABLE);
                    }
                })
                .build();
    }

    static List<Path> collectGoFiles(Path sourceRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".go"))
                    .toList();
        }
    }
}
