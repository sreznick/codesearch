package org.codesearch.plugin;

import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchTarget;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.UnaryOperator;

public record LanguageSearchCapabilities(
        Set<EntityKind> declaredTypeKinds,
        Set<EntityKind> assignableKinds,
        Set<EntityKind> subtypeKinds,
        UnaryOperator<String> typeNormalizer
) {
    public LanguageSearchCapabilities {
        declaredTypeKinds = declaredTypeKinds == null ? Set.of() : Set.copyOf(declaredTypeKinds);
        assignableKinds = assignableKinds == null ? Set.of() : Set.copyOf(assignableKinds);
        subtypeKinds = subtypeKinds == null ? Set.of() : Set.copyOf(subtypeKinds);
        typeNormalizer = typeNormalizer == null ? UnaryOperator.identity() : typeNormalizer;
    }

    public static LanguageSearchCapabilities nameSearchOnly() {
        return new LanguageSearchCapabilities(Set.of(), Set.of(), Set.of(), null);
    }

    public Set<SearchTarget> searchTargets() {
        Set<SearchTarget> targets = EnumSet.of(SearchTarget.CONTENT);
        if (!declaredTypeKinds.isEmpty()) {
            targets.add(SearchTarget.DECLARED_TYPE);
        }
        if (!assignableKinds.isEmpty()) {
            targets.add(SearchTarget.ASSIGNABLE_TYPE);
        }
        if (!subtypeKinds.isEmpty()) {
            targets.add(SearchTarget.SUPERTYPE);
        }
        return targets;
    }

    public String normalizeType(String rawType) {
        String normalized = typeNormalizer.apply(rawType);
        return normalized == null || normalized.isBlank() ? rawType.trim() : normalized;
    }
}
