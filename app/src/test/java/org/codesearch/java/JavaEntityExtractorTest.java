package org.codesearch.java;

import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaEntityExtractorTest {
    @Test
    void shouldExtractCoreEntitiesFromJavaFile() throws IOException {
        JavaEntityExtractor extractor = new JavaEntityExtractor();

        List<CodeEntity> entities = extractor.extractEntities(Path.of("src/test/resources/TestClass.java"));

        assertTrue(entities.stream().anyMatch(entity ->
                entity.kind() == EntityKind.CLASS
                        && entity.content().equals("TestClass")
                        && entity.location().line() == 6));

        assertTrue(entities.stream().anyMatch(entity ->
                entity.kind() == EntityKind.FIELD
                        && entity.content().equals("testField")
                        && "String".equals(entity.declaredType())));

        assertTrue(entities.stream().anyMatch(entity ->
                entity.kind() == EntityKind.STRING_LITERAL
                        && entity.content().equals("Test String")));

        assertTrue(entities.stream().anyMatch(entity ->
                entity.kind() == EntityKind.METHOD
                        && entity.content().equals("getTestField")
                        && "String".equals(entity.declaredType())));

        assertTrue(entities.stream().anyMatch(entity ->
                entity.kind() == EntityKind.LOCAL_VARIABLE
                        && entity.content().equals("localVariable")
                        && "String".equals(entity.declaredType())));

        assertTrue(entities.stream().anyMatch(entity ->
                entity.kind() == EntityKind.LOCAL_VARIABLE
                        && entity.content().equals("inferredText")
                        && "String".equals(entity.declaredType())
                        && entity.attributes().get("assignableTypes").contains("CharSequence")));

        assertTrue(entities.stream().anyMatch(entity ->
                entity.kind() == EntityKind.LOCAL_VARIABLE
                        && entity.content().equals("inferredBuilder")
                        && "StringBuilder".equals(entity.declaredType())
                        && "var -> StringBuilder".equals(entity.attributes().get("typeInference"))
                        && entity.attributes().get("assignableTypes").contains("Appendable")));

        assertTrue(entities.stream().anyMatch(entity ->
                entity.kind() == EntityKind.ANNOTATION
                        && entity.content().equals("DemoController")
                        && "Class".equals(entity.attributes().get(JavaEntityAttributes.ANNOTATION_TARGET_KIND))
                        && "AnnotationFixture".equals(entity.attributes().get(JavaEntityAttributes.ANNOTATION_TARGET_NAME))));
    }

    @Test
    void shouldExtractExpectedKindsFromTestFixture() throws IOException {
        JavaEntityExtractor extractor = new JavaEntityExtractor();

        List<CodeEntity> entities = extractor.extractEntities(Path.of("src/test/resources/TestClass.java"));
        Map<EntityKind, Long> counts = entities.stream()
                .collect(Collectors.groupingBy(CodeEntity::kind, Collectors.counting()));

        assertTrue(counts.getOrDefault(EntityKind.CLASS, 0L) >= 1);
        assertTrue(counts.getOrDefault(EntityKind.INTERFACE, 0L) >= 1);
        assertTrue(counts.getOrDefault(EntityKind.FIELD, 0L) >= 6);
        assertTrue(counts.getOrDefault(EntityKind.LOCAL_VARIABLE, 0L) >= 4);
        assertTrue(counts.getOrDefault(EntityKind.ANNOTATION, 0L) >= 4);
        assertTrue(counts.getOrDefault(EntityKind.STRING_CONSTANT, 0L) >= 4);
        assertTrue(counts.getOrDefault(EntityKind.STRING_LITERAL, 0L) >= 5);
    }
}
