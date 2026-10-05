package org.codesearch.java;

import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityAttributes;
import org.codesearch.core.EntityKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                        && "String".equals(entity.declaredType())
                        && "class".equals(entity.attributes().get(EntityAttributes.CONTAINER_KIND))
                        && "TestClass".equals(entity.attributes().get(EntityAttributes.CONTAINER_NAME))));

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
                        && "Class".equals(entity.attributes().get(EntityAttributes.ANNOTATION_TARGET_KIND))
                        && "AnnotationFixture".equals(entity.attributes().get(EntityAttributes.ANNOTATION_TARGET_NAME))));
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

    @Test
    void shouldExtractModernJavaDeclarations() throws IOException {
        Path file = Path.of("src/test/fixtures/java/Modern.java");
        List<CodeEntity> entities = new JavaLanguagePlugin().createExtractor(List.of(file)).extractEntities(file);

        assertEquals("Shape", find(entities, EntityKind.RECORD, "Circle").attribute(EntityAttributes.IMPLEMENTS_TYPES));
        assertEquals("double", find(entities, EntityKind.FIELD, "radius").declaredType());
        assertEquals("Circle", find(entities, EntityKind.FIELD, "radius").attribute(EntityAttributes.CONTAINER_NAME));
        assertEquals("Labeled", find(entities, EntityKind.ENUM, "Color").attribute(EntityAttributes.IMPLEMENTS_TYPES));
        assertEquals("double", find(entities, EntityKind.METHOD, "area").declaredType());
        assertEquals("interface", entities.stream()
                .filter(e -> e.kind() == EntityKind.METHOD && e.content().equals("label"))
                .map(e -> e.attribute(EntityAttributes.CONTAINER_KIND))
                .findFirst().orElseThrow());
        assertEquals("int", find(entities, EntityKind.FIELD, "width").declaredType());
        assertEquals("int", find(entities, EntityKind.FIELD, "height").declaredType());
        assertEquals("Cube", find(entities, EntityKind.LOCAL_VARIABLE, "cube").declaredType());
        assertEquals("var", find(entities, EntityKind.LOCAL_VARIABLE, "shape").declaredType());
    }

    @Test
    void shouldResolveAnnotationTargetsFromSyntaxTree() throws IOException {
        Path file = Path.of("src/test/fixtures/java/Modern.java");
        List<CodeEntity> annotations = new JavaEntityExtractor().extractEntities(file).stream()
                .filter(entity -> entity.kind() == EntityKind.ANNOTATION)
                .toList();

        assertTrue(annotations.stream().anyMatch(a -> a.content().equals("Override")
                && "Method".equals(a.attribute(EntityAttributes.ANNOTATION_TARGET_KIND))
                && "area".equals(a.attribute(EntityAttributes.ANNOTATION_TARGET_NAME))));
        assertTrue(annotations.stream().anyMatch(a -> a.content().equals("Deprecated")
                && "Parameter".equals(a.attribute(EntityAttributes.ANNOTATION_TARGET_KIND))
                && "title".equals(a.attribute(EntityAttributes.ANNOTATION_TARGET_NAME))));
    }

    @Test
    void shouldExtractCallsConstructorsAndMethodReferences() throws IOException {
        Path file = Path.of("src/test/fixtures/java/Modern.java");
        List<CodeEntity> calls = new JavaEntityExtractor().extractEntities(file).stream()
                .filter(entity -> entity.kind() == EntityKind.CALL)
                .toList();

        CodeEntity apply = calls.stream().filter(c -> c.content().equals("apply")).findFirst().orElseThrow();
        assertEquals("area", apply.attribute(EntityAttributes.CALL_QUALIFIER));
        assertEquals("Report.total", apply.attribute(EntityAttributes.CONTAINER_NAME));

        CodeEntity constructor = calls.stream().filter(c -> c.content().equals("Cube")).findFirst().orElseThrow();
        assertEquals("constructor", constructor.attribute(EntityAttributes.CALL_KIND));

        CodeEntity reference = calls.stream().filter(c -> c.content().equals("area")
                && "reference".equals(c.attribute(EntityAttributes.CALL_KIND))).findFirst().orElseThrow();
        assertEquals("Shape", reference.attribute(EntityAttributes.CALL_QUALIFIER));

        CodeEntity validate = calls.stream().filter(c -> c.content().equals("validate")).findFirst().orElseThrow();
        assertEquals("Circle.Circle", validate.attribute(EntityAttributes.CONTAINER_NAME));
    }

    @Test
    void shouldStoreTransitiveSupertypes() throws IOException {
        Path file = Path.of("src/test/fixtures/java/Modern.java");
        List<CodeEntity> entities = new JavaLanguagePlugin().createExtractor(List.of(file)).extractEntities(file);

        assertEquals(java.util.Set.of("Square", "Shape", "Labeled"),
                EntityAttributes.splitList(find(entities, EntityKind.CLASS, "Cube").attribute(EntityAttributes.SUPERTYPES)));
    }

    private static CodeEntity find(List<CodeEntity> entities, EntityKind kind, String name) {
        return entities.stream()
                .filter(entity -> entity.kind() == kind && entity.content().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Не найдено: " + kind + " " + name));
    }
}
