package org.codesearch.go;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GoTypeResolver {
    private static final Pattern CALL_EXPRESSION = Pattern.compile("^[A-Za-z_][\\w]*\\(");

    private GoTypeResolver() {}

    static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return type.trim().replace(" ", "");
    }

    static String searchableTypeName(String type) {
        String normalized = normalizeType(type);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("*")) {
            normalized = normalized.substring(1);
        }
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    static String structName(String type) {
        String normalized = normalizeType(type);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("*")) {
            normalized = normalized.substring(1);
        }
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    static Set<String> assignableTypes(String type) {
        String normalized = normalizeType(type);
        if (normalized == null || normalized.isBlank()) {
            return Set.of();
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(normalized);
        String searchable = searchableTypeName(normalized);
        if (searchable != null) {
            result.add(searchable);
        }
        if (normalized.startsWith("*")) {
            String elem = normalized.substring(1);
            result.add(elem);
            String elemSimple = searchableTypeName(elem);
            if (elemSimple != null) {
                result.add(elemSimple);
            }
        } else if (!isBuiltin(normalized)) {
            result.add("*" + normalized);
            if (searchable != null) {
                result.add("*" + searchable);
            }
        }
        return result;
    }

    static String serializeTypes(Set<String> types) {
        return String.join(",", types);
    }

    static String inferTypeFromExpression(String expression, Map<String, String> functionReturnTypes) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String value = expression.trim();
        if (value.startsWith("\"") || value.startsWith("`")) {
            return "string";
        }
        if ("true".equals(value) || "false".equals(value)) {
            return "bool";
        }
        if (value.matches("[-+]?\\d+")) {
            return "int";
        }
        if (value.matches("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)")) {
            return "float64";
        }

        Matcher call = CALL_EXPRESSION.matcher(value);
        if (call.find()) {
            String functionName = value.substring(0, value.indexOf('('));
            return functionReturnTypes.get(functionName);
        }
        return null;
    }

    private static boolean isBuiltin(String type) {
        return switch (type) {
            case "bool", "string", "int", "int8", "int16", "int32", "int64",
                 "uint", "uint8", "uint16", "uint32", "uint64",
                 "float32", "float64", "complex64", "complex128", "byte", "rune" -> true;
            default -> false;
        };
    }
}
