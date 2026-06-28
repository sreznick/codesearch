package org.codesearch.java;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JavaTypeResolver {
    private static final Pattern NEW_EXPRESSION = Pattern.compile("^new\\s*([A-Za-z_$][\\w$\\.]*)(?:<[^>]*>)?\\(.*");
    private static final Pattern INTEGER_LITERAL = Pattern.compile("[-+]?\\d+[lL]?");
    private static final Pattern FLOAT_LITERAL = Pattern.compile("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)(?:[fFdD])?");

    private static final Map<String, Set<String>> KNOWN_ASSIGNABLE_TYPES = Map.ofEntries(
            Map.entry("String", orderedSet("String", "CharSequence", "Comparable", "Serializable", "Object")),
            Map.entry("StringBuilder", orderedSet("StringBuilder", "Appendable", "CharSequence", "Serializable", "Object")),
            Map.entry("StringBuffer", orderedSet("StringBuffer", "Appendable", "CharSequence", "Serializable", "Object")),
            Map.entry("ArrayList", orderedSet("ArrayList", "List", "Collection", "Iterable", "Object")),
            Map.entry("LinkedList", orderedSet("LinkedList", "List", "Deque", "Queue", "Collection", "Iterable", "Object")),
            Map.entry("HashSet", orderedSet("HashSet", "Set", "Collection", "Iterable", "Object")),
            Map.entry("LinkedHashSet", orderedSet("LinkedHashSet", "Set", "Collection", "Iterable", "Object")),
            Map.entry("HashMap", orderedSet("HashMap", "Map", "Object")),
            Map.entry("LinkedHashMap", orderedSet("LinkedHashMap", "Map", "Object")),
            Map.entry("List", orderedSet("List", "Collection", "Iterable", "Object")),
            Map.entry("Set", orderedSet("Set", "Collection", "Iterable", "Object")),
            Map.entry("Collection", orderedSet("Collection", "Iterable", "Object")),
            Map.entry("Iterable", orderedSet("Iterable", "Object")),
            Map.entry("Map", orderedSet("Map", "Object")),
            Map.entry("Appendable", orderedSet("Appendable", "Object")),
            Map.entry("CharSequence", orderedSet("CharSequence", "Object"))
    );

    private JavaTypeResolver() {}

    static String resolveDeclaredType(String declaredType, String initializer) {
        String normalizedDeclaredType = normalizeType(declaredType);
        if (!"var".equals(normalizedDeclaredType)) {
            return normalizedDeclaredType;
        }

        String inferredType = inferType(initializer);
        return inferredType == null ? normalizedDeclaredType : inferredType;
    }

    static Set<String> assignableTypes(String type) {
        String normalizedType = normalizeType(type);
        if (normalizedType == null || normalizedType.isBlank()) {
            return Set.of();
        }

        Set<String> knownTypes = KNOWN_ASSIGNABLE_TYPES.get(simpleName(normalizedType));
        if (knownTypes != null) {
            return knownTypes;
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(normalizedType);
        String simpleName = simpleName(normalizedType);
        result.add(simpleName);
        if (!isPrimitive(simpleName)) {
            result.add("Object");
        }
        return result;
    }

    static String serializeTypes(Set<String> types) {
        return String.join(",", types);
    }

    static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }

        String normalized = type.trim()
                .replace(" ", "")
                .replace("...", "[]");
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart);
        }
        return normalized;
    }

    static String searchableTypeName(String type) {
        String normalizedType = normalizeType(type);
        return normalizedType == null ? null : simpleName(normalizedType);
    }

    private static String inferType(String initializer) {
        if (initializer == null || initializer.isBlank()) {
            return null;
        }

        String value = initializer.trim();
        Matcher newExpression = NEW_EXPRESSION.matcher(value);
        if (newExpression.matches()) {
            return simpleName(newExpression.group(1));
        }
        if (value.startsWith("\"")) {
            return "String";
        }
        if (value.startsWith("'")) {
            return "char";
        }
        if ("true".equals(value) || "false".equals(value)) {
            return "boolean";
        }
        if (FLOAT_LITERAL.matcher(value).matches()) {
            return value.endsWith("f") || value.endsWith("F") ? "float" : "double";
        }
        if (INTEGER_LITERAL.matcher(value).matches()) {
            return value.endsWith("l") || value.endsWith("L") ? "long" : "int";
        }

        return null;
    }

    private static String simpleName(String type) {
        int lastDot = type.lastIndexOf('.');
        return lastDot >= 0 ? type.substring(lastDot + 1) : type;
    }

    private static boolean isPrimitive(String type) {
        return switch (type) {
            case "boolean", "byte", "short", "int", "long", "float", "double", "char" -> true;
            default -> false;
        };
    }

    private static Set<String> orderedSet(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
