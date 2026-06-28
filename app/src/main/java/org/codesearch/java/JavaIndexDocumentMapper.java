package org.codesearch.java;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.codesearch.core.CodeEntity;

public final class JavaIndexDocumentMapper {
    private JavaIndexDocumentMapper() {}

    public static Document toDocument(CodeEntity entity) {
        Document document = new Document();
        document.add(new StringField(JavaIndexFields.CONTENT, entity.content(), Field.Store.YES));
        document.add(new StringField(JavaIndexFields.CONTENT_LOWERCASE, entity.content().toLowerCase(), Field.Store.NO));
        document.add(new StringField(JavaIndexFields.LANGUAGE, entity.language(), Field.Store.YES));
        document.add(new StringField(JavaIndexFields.FILE, entity.location().filePath(), Field.Store.YES));
        document.add(new StringField(JavaIndexFields.LINE, String.valueOf(entity.location().line()), Field.Store.YES));
        document.add(new StringField(JavaIndexFields.TYPE, entity.kind().legacyJavaType(), Field.Store.YES));

        if (entity.declaredType() != null && !entity.declaredType().isBlank()) {
            document.add(new StringField(JavaIndexFields.DECLARED_TYPE, entity.declaredType(), Field.Store.YES));
            document.add(new StringField(JavaIndexFields.DECLARED_TYPE_LOWERCASE, entity.declaredType().toLowerCase(), Field.Store.NO));
            document.add(new StringField(JavaIndexFields.LEGACY_VAR_TYPE, entity.declaredType(), Field.Store.YES));
            document.add(new StringField(JavaIndexFields.LEGACY_VAR_TYPE_LOWERCASE, entity.declaredType().toLowerCase(), Field.Store.NO));
        }

        String assignableTypes = entity.attributes().get(JavaEntityAttributes.ASSIGNABLE_TYPES);
        if (assignableTypes != null && !assignableTypes.isBlank()) {
            for (String assignableType : assignableTypes.split(",")) {
                if (!assignableType.isBlank()) {
                    document.add(new StringField(JavaIndexFields.ASSIGNABLE_TYPE, assignableType, Field.Store.YES));
                    document.add(new StringField(JavaIndexFields.ASSIGNABLE_TYPE_LOWERCASE, assignableType.toLowerCase(), Field.Store.NO));
                }
            }
        }

        String typeInference = entity.attributes().get(JavaEntityAttributes.TYPE_INFERENCE);
        if (typeInference != null && !typeInference.isBlank()) {
            document.add(new StringField(JavaIndexFields.TYPE_INFERENCE, typeInference, Field.Store.YES));
        }

        addStoredAttribute(document, entity, JavaEntityAttributes.CONTAINER_KIND, JavaIndexFields.CONTAINER_KIND);
        addStoredAttribute(document, entity, JavaEntityAttributes.CONTAINER_NAME, JavaIndexFields.CONTAINER_NAME);
        addStoredAttribute(document, entity, JavaEntityAttributes.EXTENDS_TYPES, JavaIndexFields.EXTENDS_TYPES);
        addStoredAttribute(document, entity, JavaEntityAttributes.IMPLEMENTS_TYPES, JavaIndexFields.IMPLEMENTS_TYPES);
        addStoredAttribute(document, entity, JavaEntityAttributes.ANNOTATION_TARGET_KIND, JavaIndexFields.ANNOTATION_TARGET_KIND);
        addStoredAttribute(document, entity, JavaEntityAttributes.ANNOTATION_TARGET_NAME, JavaIndexFields.ANNOTATION_TARGET_NAME);

        return document;
    }

    private static void addStoredAttribute(Document document, CodeEntity entity, String attributeName, String fieldName) {
        String value = entity.attributes().get(attributeName);
        if (value != null && !value.isBlank()) {
            document.add(new StringField(fieldName, value, Field.Store.YES));
        }
    }
}
