package org.codesearch.java;

import org.apache.lucene.document.Document;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.EntityLocation;

import java.util.HashMap;
import java.util.Map;

final class JavaDocumentMapper {
    private JavaDocumentMapper() {}

    static CodeEntity toCodeEntity(Document document) {
        Map<String, String> attributes = new HashMap<>();
        String declaredType = readDeclaredType(document);
        if (declaredType != null && !declaredType.isBlank()) {
            attributes.put(JavaEntityAttributes.DECLARED_TYPE, declaredType);
        }
        String assignableTypes = String.join(",", document.getValues(JavaIndexFields.ASSIGNABLE_TYPE));
        if (!assignableTypes.isBlank()) {
            attributes.put(JavaEntityAttributes.ASSIGNABLE_TYPES, assignableTypes);
        }
        String typeInference = document.get(JavaIndexFields.TYPE_INFERENCE);
        if (typeInference != null && !typeInference.isBlank()) {
            attributes.put(JavaEntityAttributes.TYPE_INFERENCE, typeInference);
        }
        addAttribute(document, attributes, JavaIndexFields.ANNOTATION_TARGET_KIND, JavaEntityAttributes.ANNOTATION_TARGET_KIND);
        addAttribute(document, attributes, JavaIndexFields.ANNOTATION_TARGET_NAME, JavaEntityAttributes.ANNOTATION_TARGET_NAME);

        return new CodeEntity(
                EntityKind.fromValue(document.get(JavaIndexFields.TYPE)),
                document.get(JavaIndexFields.CONTENT),
                new EntityLocation(
                        document.get(JavaIndexFields.FILE),
                        Integer.parseInt(document.get(JavaIndexFields.LINE)),
                        0
                ),
                JavaLanguageModule.LANGUAGE,
                declaredType,
                attributes
        );
    }

    private static String readDeclaredType(Document document) {
        String declaredType = document.get(JavaIndexFields.DECLARED_TYPE);
        if (declaredType != null && !declaredType.isBlank()) {
            return declaredType;
        }

        return document.get(JavaIndexFields.LEGACY_VAR_TYPE);
    }

    private static void addAttribute(Document document, Map<String, String> attributes, String fieldName, String attributeName) {
        String value = document.get(fieldName);
        if (value != null && !value.isBlank()) {
            attributes.put(attributeName, value);
        }
    }
}
