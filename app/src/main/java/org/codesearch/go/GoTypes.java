package org.codesearch.go;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GoTypes {
    private static final Pattern INTEGER = Pattern.compile("[-+]?(?:0[xX][\\da-fA-F_]+|0[oObB][\\d_]+|\\d[\\d_]*)");
    private static final Pattern FLOAT = Pattern.compile("[-+]?(?:\\d+\\.\\d*|\\d*\\.\\d+)(?:[eE][-+]?\\d+)?|[-+]?\\d+[eE][-+]?\\d+");
    private static final Pattern COMPOSITE_LITERAL = Pattern.compile("^(&)?((?:\\[\\][\\w.*]+)|(?:map\\[[^\\]]+\\][\\w.*\\[\\]]+)|(?:[A-Za-z_][\\w]*(?:\\.[A-Za-z_][\\w]*)?))\\{.*", Pattern.DOTALL);
    private static final Pattern NEW_CALL = Pattern.compile("^new\\((.+)\\)$", Pattern.DOTALL);
    private static final Pattern MAKE_CALL = Pattern.compile("^make\\(((?:\\[\\]|map\\[|chan)[^,)]*(?:\\][^,)]*)?)(?:,.*)?\\)$", Pattern.DOTALL);
    private static final Pattern FUNCTION_CALL = Pattern.compile("^(?:([A-Za-z_][\\w]*)\\.)?([A-Za-z_][\\w]*)\\(.*\\)$", Pattern.DOTALL);

    private GoTypes() {}

    static String normalize(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return type.replaceAll("\\s+", "");
    }

    static String searchableName(String type) {
        String normalized = normalize(type);
        if (normalized == null) {
            return null;
        }
        while (normalized.startsWith("*")) {
            normalized = normalized.substring(1);
        }
        if (isComposite(normalized)) {
            return normalized;
        }
        int typeArgs = normalized.indexOf('[');
        if (typeArgs > 0) {
            normalized = normalized.substring(0, typeArgs);
        }
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    static boolean isComposite(String normalizedType) {
        return normalizedType.startsWith("[")
                || normalizedType.startsWith("map[")
                || normalizedType.startsWith("func")
                || normalizedType.startsWith("chan")
                || normalizedType.startsWith("<-chan")
                || normalizedType.startsWith("struct{")
                || normalizedType.startsWith("interface{");
    }

    static String inferExpressionType(String expression, Map<String, String> functionResultTypes, Set<String> importedPackages) {
        String value = normalize(expression);
        if (value == null) {
            return null;
        }
        if (value.startsWith("\"") || value.startsWith("`")) {
            return "string";
        }
        if (value.startsWith("'")) {
            return "rune";
        }
        if ("true".equals(value) || "false".equals(value)) {
            return "bool";
        }
        if (INTEGER.matcher(value).matches()) {
            return "int";
        }
        if (FLOAT.matcher(value).matches()) {
            return "float64";
        }

        Matcher composite = COMPOSITE_LITERAL.matcher(value);
        if (composite.matches()) {
            return (composite.group(1) == null ? "" : "*") + composite.group(2);
        }
        Matcher newCall = NEW_CALL.matcher(value);
        if (newCall.matches()) {
            return "*" + newCall.group(1);
        }
        Matcher makeCall = MAKE_CALL.matcher(value);
        if (makeCall.matches()) {
            return makeCall.group(1);
        }
        Matcher call = FUNCTION_CALL.matcher(value);
        if (call.matches()) {
            String qualifier = call.group(1);
            return qualifier == null || importedPackages.contains(qualifier) ? functionResultTypes.get(call.group(2)) : null;
        }
        return null;
    }
}
