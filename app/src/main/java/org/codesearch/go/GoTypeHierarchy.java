package org.codesearch.go;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class GoTypeHierarchy {
    private final Map<String, Set<String>> structMethods;
    private final Map<String, Set<String>> interfaceMethods;

    GoTypeHierarchy(Map<String, Set<String>> structMethods, Map<String, Set<String>> interfaceMethods) {
        this.structMethods = structMethods == null ? Map.of() : Map.copyOf(structMethods);
        this.interfaceMethods = interfaceMethods == null ? Map.of() : Map.copyOf(interfaceMethods);
    }

    static GoTypeHierarchy empty() {
        return new GoTypeHierarchy(Map.of(), Map.of());
    }

    Set<String> assignableTypes(String declaredType) {
        Set<String> base = GoTypeResolver.assignableTypes(declaredType);
        if (base.isEmpty()) {
            return base;
        }

        LinkedHashSet<String> result = new LinkedHashSet<>(base);
        String structName = GoTypeResolver.structName(declaredType);
        if (structName != null) {
            Set<String> methods = structMethods.getOrDefault(structName, Set.of());
            for (Map.Entry<String, Set<String>> entry : interfaceMethods.entrySet()) {
                if (methods.containsAll(entry.getValue())) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }
}
