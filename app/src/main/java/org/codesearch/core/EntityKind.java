package org.codesearch.core;

import java.util.Arrays;

public enum EntityKind {
    CLASS("class", "Class"),
    RECORD("record", "Record"),
    ENUM("enum", "Enum"),
    INTERFACE("interface", "Interface"),
    STRUCT("struct", "Struct"),
    FUNCTION("function", "Function"),
    METHOD("method", "Method"),
    FIELD("field", "Field"),
    PACKAGE("package", "Package"),
    IMPORT("import", "Import"),
    VARIABLE("variable", "Variable"),
    CONSTANT("const", "Const"),
    LOCAL_VARIABLE("local_variable", "LocalVariable"),
    ANNOTATION("annotation", "Annotation"),
    DECORATOR("decorator", "Decorator"),
    CALL("call", "Call"),
    STRING_CONSTANT("string_constant", "StringConstant"),
    INTEGER_LITERAL("integer_literal", "IntegerLiteral"),
    FLOAT_LITERAL("float_literal", "FloatLiteral"),
    BOOLEAN_LITERAL("boolean_literal", "BooleanLiteral"),
    CHAR_LITERAL("char_literal", "CharLiteral"),
    STRING_LITERAL("string_literal", "StringLiteral");

    private final String key;
    private final String displayName;

    EntityKind(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isTypeDeclaration() {
        return this == CLASS || this == RECORD || this == ENUM || this == INTERFACE || this == STRUCT;
    }

    public static EntityKind fromValue(String rawValue) {
        String normalized = normalize(rawValue);

        return Arrays.stream(values())
                .filter(kind -> normalize(kind.key).equals(normalized)
                        || normalize(kind.displayName).equals(normalized)
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
