package org.codesearch.core;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class EntityAttributes {
    public static final String DECLARED_TYPE = "declaredType";
    public static final String ASSIGNABLE_TYPES = "assignableTypes";
    public static final String TYPE_INFERENCE = "typeInference";
    public static final String EXTENDS_TYPES = "extendsTypes";
    public static final String IMPLEMENTS_TYPES = "implementsTypes";
    public static final String CONTAINER_KIND = "containerKind";
    public static final String CONTAINER_NAME = "containerName";
    public static final String ANNOTATION_TARGET_KIND = "annotationTargetKind";
    public static final String ANNOTATION_TARGET_NAME = "annotationTargetName";
    public static final String ALIAS = "alias";
    public static final String RECEIVER = "receiver";
    public static final String SUPERTYPES = "supertypes";
    public static final String CALL_QUALIFIER = "callQualifier";
    public static final String CALL_KIND = "callKind";

    private static final String LIST_SEPARATOR = ",";

    private EntityAttributes() {}

    public static String joinList(Set<String> values) {
        return String.join(LIST_SEPARATOR, values);
    }

    public static Set<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(LIST_SEPARATOR))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
