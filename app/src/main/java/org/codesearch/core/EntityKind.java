package org.codesearch.core;

import java.util.Arrays;

public enum EntityKind {
    CLASS("class", "Class"),
    INTERFACE("interface", "Interface"),
    METHOD("method", "Method"),
    FIELD("field", "Field"),
    LOCAL_VARIABLE("local_variable", "LocalVariable"),
    ANNOTATION("annotation", "Annotation"),
    STRING_CONSTANT("string_constant", "StringConstant"),
    INTEGER_LITERAL("integer_literal", "IntegerLiteral"),
    FLOAT_LITERAL("float_literal", "FloatLiteral"),
    BOOLEAN_LITERAL("boolean_literal", "BooleanLiteral"),
    CHAR_LITERAL("char_literal", "CharLiteral"),
    STRING_LITERAL("string_literal", "StringLiteral");

    private final String key;
    private final String legacyJavaType;

    EntityKind(String key, String legacyJavaType) {
        this.key = key;
        this.legacyJavaType = legacyJavaType;
    }

    public String key() {
        return key;
    }

    public String legacyJavaType() {
        return legacyJavaType;
    }

    public static EntityKind fromValue(String rawValue) {
        String normalized = normalize(rawValue);

        return Arrays.stream(values())
                .filter(kind -> normalize(kind.key).equals(normalized)
                        || normalize(kind.legacyJavaType).equals(normalized)
                        || normalize(kind.name()).equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported entity kind: " + rawValue));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Entity kind value must not be blank");
        }

        return value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toLowerCase();
    }
}
