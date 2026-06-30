package org.codesearch.core;

import org.apache.lucene.document.Document;

import java.util.HashMap;
import java.util.Map;

public final class DocumentMapper {
    private DocumentMapper() {}

    public static CodeEntity toCodeEntity(Document document, String language) {
        Map<String, String> attributes = new HashMap<>();
        String declaredType = readDeclaredType(document);
        if (declaredType != null && !declaredType.isBlank()) {
            attributes.put(EntityAttributes.DECLARED_TYPE, declaredType);
        }
        String assignableTypes = String.join(",", document.getValues(IndexFields.ASSIGNABLE_TYPE));
        if (!assignableTypes.isBlank()) {
            attributes.put(EntityAttributes.ASSIGNABLE_TYPES, assignableTypes);
        }
        String typeInference = document.get(IndexFields.TYPE_INFERENCE);
        if (typeInference != null && !typeInference.isBlank()) {
            attributes.put(EntityAttributes.TYPE_INFERENCE, typeInference);
        }

        return new CodeEntity(
                EntityKind.fromValue(document.get(IndexFields.TYPE)),
                document.get(IndexFields.CONTENT),
                new EntityLocation(
                        document.get(IndexFields.FILE),
                        Integer.parseInt(document.get(IndexFields.LINE)),
                        0
                ),
                language,
                declaredType,
                attributes
        );
    }

    private static String readDeclaredType(Document document) {
        String declaredType = document.get(IndexFields.DECLARED_TYPE);
        if (declaredType != null && !declaredType.isBlank()) {
            return declaredType;
        }
        return document.get(IndexFields.LEGACY_VAR_TYPE);
    }
}
