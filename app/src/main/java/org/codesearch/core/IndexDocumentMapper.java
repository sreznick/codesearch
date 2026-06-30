package org.codesearch.core;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;

public final class IndexDocumentMapper {
    private IndexDocumentMapper() {}

    public static Document toDocument(CodeEntity entity) {
        Document document = new Document();
        document.add(new StringField(IndexFields.CONTENT, entity.content(), Field.Store.YES));
        document.add(new StringField(IndexFields.CONTENT_LOWERCASE, entity.content().toLowerCase(), Field.Store.NO));
        document.add(new StringField(IndexFields.FILE, entity.location().filePath(), Field.Store.YES));
        document.add(new StringField(IndexFields.LINE, String.valueOf(entity.location().line()), Field.Store.YES));
        document.add(new StringField(IndexFields.TYPE, entity.kind().legacyJavaType(), Field.Store.YES));

        if (entity.declaredType() != null && !entity.declaredType().isBlank()) {
            document.add(new StringField(IndexFields.DECLARED_TYPE, entity.declaredType(), Field.Store.YES));
            document.add(new StringField(IndexFields.DECLARED_TYPE_LOWERCASE, entity.declaredType().toLowerCase(), Field.Store.NO));
            document.add(new StringField(IndexFields.LEGACY_VAR_TYPE, entity.declaredType(), Field.Store.YES));
            document.add(new StringField(IndexFields.LEGACY_VAR_TYPE_LOWERCASE, entity.declaredType().toLowerCase(), Field.Store.NO));
        }

        String assignableTypes = entity.attributes().get(EntityAttributes.ASSIGNABLE_TYPES);
        if (assignableTypes != null && !assignableTypes.isBlank()) {
            for (String assignableType : assignableTypes.split(",")) {
                if (!assignableType.isBlank()) {
                    document.add(new StringField(IndexFields.ASSIGNABLE_TYPE, assignableType, Field.Store.YES));
                    document.add(new StringField(IndexFields.ASSIGNABLE_TYPE_LOWERCASE, assignableType.toLowerCase(), Field.Store.NO));
                }
            }
        }

        String typeInference = entity.attributes().get(EntityAttributes.TYPE_INFERENCE);
        if (typeInference != null && !typeInference.isBlank()) {
            document.add(new StringField(IndexFields.TYPE_INFERENCE, typeInference, Field.Store.YES));
        }

        return document;
    }
}
