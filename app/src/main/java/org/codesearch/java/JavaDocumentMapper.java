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
        String declaredType = document.get("varType");
        if (declaredType != null && !declaredType.isBlank()) {
            attributes.put("declaredType", declaredType);
        }

        return new CodeEntity(
                EntityKind.fromValue(document.get("type")),
                document.get("content"),
                new EntityLocation(
                        document.get("file"),
                        Integer.parseInt(document.get("line")),
                        0
                ),
                JavaLanguageModule.LANGUAGE,
                declaredType,
                attributes
        );
    }
}
