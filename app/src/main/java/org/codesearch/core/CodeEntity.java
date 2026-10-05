package org.codesearch.core;

import java.util.HashMap;
import java.util.Map;

public record CodeEntity(
        EntityKind kind,
        String content,
        EntityLocation location,
        String language,
        String declaredType,
        Map<String, String> attributes
) {
    public CodeEntity {
        if (kind == null) {
            throw new IllegalArgumentException("Entity kind must not be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Entity content must not be blank");
        }
        if (location == null) {
            throw new IllegalArgumentException("Entity location must not be null");
        }
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("Entity language must not be blank");
        }

        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        language = language.trim().toLowerCase();
    }

    public CodeEntity(EntityKind kind, String content, EntityLocation location, String language) {
        this(kind, content, location, language, null, Map.of());
    }

    public String attribute(String name) {
        String value = attributes.get(name);
        return value == null || value.isBlank() ? null : value;
    }

    public CodeEntity withAttributes(Map<String, String> extraAttributes) {
        if (extraAttributes.isEmpty()) {
            return this;
        }
        Map<String, String> merged = new HashMap<>(attributes);
        merged.putAll(extraAttributes);
        return new CodeEntity(kind, content, location, language, declaredType, merged);
    }
}
