package org.codesearch.java;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.codesearch.core.CodeEntity;

public final class JavaIndexDocumentMapper {
    private JavaIndexDocumentMapper() {}

    public static Document toDocument(CodeEntity entity) {
        Document document = new Document();
        document.add(new StringField("content", entity.content(), Field.Store.YES));
        document.add(new StringField("content_lowercase", entity.content().toLowerCase(), Field.Store.NO));
        document.add(new StringField("file", entity.location().filePath(), Field.Store.YES));
        document.add(new StringField("line", String.valueOf(entity.location().line()), Field.Store.YES));
        document.add(new StringField("type", entity.kind().legacyJavaType(), Field.Store.YES));

        if (entity.declaredType() != null && !entity.declaredType().isBlank()) {
            document.add(new StringField("varType", entity.declaredType(), Field.Store.YES));
        }

        return document;
    }
}
