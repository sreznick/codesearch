package org.codesearch.cli;

import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchTarget;

final class SearchKinds {
    record SearchKind(EntityKind kind, SearchTarget target) {
        boolean listable() {
            return kind != null && target == SearchTarget.CONTENT;
        }
    }

    private SearchKinds() {}

    static SearchKind parseOrNull(String value) {
        return switch (value.toLowerCase()) {
            case "field-type" -> new SearchKind(EntityKind.FIELD, SearchTarget.DECLARED_TYPE);
            case "local-variable-type" -> new SearchKind(EntityKind.LOCAL_VARIABLE, SearchTarget.DECLARED_TYPE);
            case "variable-type" -> new SearchKind(EntityKind.VARIABLE, SearchTarget.DECLARED_TYPE);
            case "method-return-type" -> new SearchKind(EntityKind.METHOD, SearchTarget.DECLARED_TYPE);
            case "function-return-type" -> new SearchKind(EntityKind.FUNCTION, SearchTarget.DECLARED_TYPE);
            case "variable-assignable-to", "assignable-type" -> new SearchKind(null, SearchTarget.ASSIGNABLE_TYPE);
            case "subtypes-of", "subtypes", "implementations-of" -> new SearchKind(null, SearchTarget.SUPERTYPE);
            case "calls" -> new SearchKind(EntityKind.CALL, SearchTarget.CONTENT);
            case "var" -> new SearchKind(EntityKind.VARIABLE, SearchTarget.CONTENT);
            case "constant" -> new SearchKind(EntityKind.CONSTANT, SearchTarget.CONTENT);
            default -> {
                EntityKind kind = parseEntityKindOrNull(value);
                yield kind == null ? null : new SearchKind(kind, SearchTarget.CONTENT);
            }
        };
    }

    static boolean isAssignable(String value) {
        SearchKind kind = parseOrNull(value);
        return kind != null && kind.target() == SearchTarget.ASSIGNABLE_TYPE;
    }

    private static EntityKind parseEntityKindOrNull(String value) {
        try {
            return EntityKind.fromValue(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
