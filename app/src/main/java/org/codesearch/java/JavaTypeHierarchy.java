package org.codesearch.java;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class JavaTypeHierarchy {
    private final Map<String, Set<String>> directParents;

    JavaTypeHierarchy(Map<String, Set<String>> directParents) {
        Map<String, Set<String>> copiedParents = new LinkedHashMap<>();
        directParents.forEach((type, parents) ->
                copiedParents.put(type, Set.copyOf(parents))
        );
        this.directParents = Map.copyOf(copiedParents);
    }

    static JavaTypeHierarchy empty() {
        return new JavaTypeHierarchy(Map.of());
    }

    Set<String> assignableTypes(String type) {
        Set<String> baseTypes = JavaTypeResolver.assignableTypes(type);
        if (baseTypes.isEmpty()) {
            return baseTypes;
        }

        LinkedHashSet<String> result = new LinkedHashSet<>(baseTypes);
        collectProjectParents(JavaTypeResolver.searchableTypeName(type), result, new HashSet<>());
        return result;
    }

    Set<String> supertypes(String type) {
        String name = JavaTypeResolver.searchableTypeName(type);
        if (name == null || !directParents.containsKey(name)) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        collectProjectParents(name, result, new HashSet<>());
        result.remove(name);
        result.remove("Object");
        return result;
    }

    private void collectProjectParents(String type, Set<String> result, Set<String> visited) {
        if (type == null || !visited.add(type)) {
            return;
        }

        for (String parent : directParents.getOrDefault(type, Set.of())) {
            result.add(parent);
            result.addAll(JavaTypeResolver.assignableTypes(parent));
            collectProjectParents(parent, result, visited);
        }
    }
}
