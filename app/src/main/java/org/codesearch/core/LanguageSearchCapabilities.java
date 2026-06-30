package org.codesearch.core;

import java.util.Set;


public record LanguageSearchCapabilities(
        Set<SearchTarget> searchTargets,
        Set<String> searchKindAliases,
        Set<EntityKind> declaredTypeKinds,
        AssignableTypeSearch assignableTypeSearch
) {
    public LanguageSearchCapabilities {
        searchTargets = searchTargets == null ? Set.of() : Set.copyOf(searchTargets);
        searchKindAliases = searchKindAliases == null ? Set.of() : Set.copyOf(searchKindAliases);
        declaredTypeKinds = declaredTypeKinds == null ? Set.of() : Set.copyOf(declaredTypeKinds);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Set<SearchTarget> searchTargets = Set.of(SearchTarget.CONTENT);
        private Set<String> searchKindAliases = Set.of();
        private Set<EntityKind> declaredTypeKinds = Set.of();
        private AssignableTypeSearch assignableTypeSearch;

        public Builder searchTargets(Set<SearchTarget> searchTargets) {
            this.searchTargets = searchTargets;
            return this;
        }

        public Builder searchKindAliases(Set<String> searchKindAliases) {
            this.searchKindAliases = searchKindAliases;
            return this;
        }

        public Builder declaredTypeKinds(Set<EntityKind> declaredTypeKinds) {
            this.declaredTypeKinds = declaredTypeKinds;
            return this;
        }

        public Builder assignableTypeSearch(AssignableTypeSearch assignableTypeSearch) {
            this.assignableTypeSearch = assignableTypeSearch;
            return this;
        }

        public LanguageSearchCapabilities build() {
            return new LanguageSearchCapabilities(
                    searchTargets,
                    searchKindAliases,
                    declaredTypeKinds,
                    assignableTypeSearch
            );
        }
    }

    public interface AssignableTypeSearch {
        String normalizeType(String rawType);

        Set<EntityKind> variableKinds();
    }
}
