package org.codesearch.python;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PythonTypes {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][\\w]*");
    private static final Pattern DOTTED_IDENTIFIER = Pattern.compile("[A-Za-z_][\\w]*(?:\\.[A-Za-z_][\\w]*)*");
    private static final Pattern CALL = Pattern.compile("^((?:[A-Za-z_][\\w]*\\.)*([A-Za-z_][\\w]*))\\(.*\\)$", Pattern.DOTALL);
    private static final Pattern INTEGER = Pattern.compile("[-+]?(?:0[xX][\\da-fA-F_]+|0[oO][0-7_]+|0[bB][01_]+|\\d[\\d_]*)");
    private static final Pattern FLOAT = Pattern.compile("[-+]?(?:\\d[\\d_]*)?\\.?\\d[\\d_]*(?:[eE][-+]?\\d+)?");
    private static final Pattern OPTIONAL = Pattern.compile("^(?:typing\\.)?Optional\\[(.+)]$");
    private static final Set<String> BUILTIN_TYPES = Set.of(
            "bool", "int", "float", "complex", "str", "bytes", "bytearray",
            "list", "dict", "set", "frozenset", "tuple", "object", "None"
    );

    private PythonTypes() {}

    static boolean isIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    static boolean isConstantName(String name) {
        return name.length() > 1 && name.equals(name.toUpperCase()) && name.chars().anyMatch(Character::isLetter);
    }

    static boolean isBuiltin(String type) {
        return BUILTIN_TYPES.contains(type);
    }

    static String normalizeAnnotation(String annotation) {
        if (annotation == null || annotation.isBlank()) {
            return null;
        }
        String value = annotation.replaceAll("\\s+", "");
        if (value.length() > 1 && (value.startsWith("\"") || value.startsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        Matcher optional = OPTIONAL.matcher(value);
        if (optional.matches()) {
            value = optional.group(1);
        }
        if (value.endsWith("|None")) {
            value = value.substring(0, value.length() - "|None".length());
        } else if (value.startsWith("None|")) {
            value = value.substring("None|".length());
        }
        return value.isBlank() ? null : value;
    }

    static String searchableName(String type) {
        String normalized = normalizeAnnotation(type);
        if (normalized == null) {
            return null;
        }
        int subscript = normalized.indexOf('[');
        if (subscript > 0) {
            normalized = normalized.substring(0, subscript);
        }
        if (!DOTTED_IDENTIFIER.matcher(normalized).matches()) {
            return normalized;
        }
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    static String inferExpressionType(String expression, Set<String> knownClasses, Map<String, String> functionReturnTypes) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String value = expression.trim();
        String lower = value.toLowerCase();
        if (lower.matches("^[rbuf]{0,2}['\"].*")) {
            return lower.matches("^r?br?['\"].*") ? "bytes" : "str";
        }
        if ("True".equals(value) || "False".equals(value)) {
            return "bool";
        }
        if (INTEGER.matcher(value).matches()) {
            return "int";
        }
        if (FLOAT.matcher(value).matches()) {
            return "float";
        }
        if (value.startsWith("[")) {
            return "list";
        }
        if (value.startsWith("{")) {
            return value.equals("{}") || value.contains(":") ? "dict" : "set";
        }
        if (value.startsWith("(") && value.endsWith(")") && value.contains(",")) {
            return "tuple";
        }

        Matcher call = CALL.matcher(value);
        if (call.matches()) {
            String simpleName = call.group(2);
            if (knownClasses.contains(simpleName) || isBuiltin(simpleName)) {
                return simpleName;
            }
            String returnType = call.group(1).equals(simpleName) ? functionReturnTypes.get(simpleName) : null;
            if (returnType != null) {
                return returnType;
            }

            if (Character.isUpperCase(simpleName.charAt(0)) && !isConstantName(simpleName)) {
                return simpleName;
            }
        }
        return null;
    }
}
