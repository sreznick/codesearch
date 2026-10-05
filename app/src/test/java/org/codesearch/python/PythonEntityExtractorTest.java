package org.codesearch.python;

import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityAttributes;
import org.codesearch.core.EntityKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonEntityExtractorTest {
    private static final Path FIXTURE = Path.of("src/test/resources/python/shop.py");
    private static List<CodeEntity> entities;

    @BeforeAll
    static void extract() throws IOException {
        entities = new PythonLanguagePlugin().createExtractor(List.of(FIXTURE)).extractEntities(FIXTURE);
    }

    @Test
    void shouldExtractClassesWithBases() {
        CodeEntity product = find(EntityKind.CLASS, "Product").orElseThrow();

        assertEquals(14, product.location().line());
        assertEquals("python", product.language());
        assertEquals("Entity", product.attribute(EntityAttributes.EXTENDS_TYPES));
        assertTrue(find(EntityKind.CLASS, "Entity").orElseThrow().attribute(EntityAttributes.EXTENDS_TYPES) == null);
    }

    @Test
    void shouldDistinguishMethodsFromFunctions() {
        CodeEntity method = find(EntityKind.METHOD, "display_name").orElseThrow();
        CodeEntity function = find(EntityKind.FUNCTION, "make_product").orElseThrow();

        assertEquals("str", method.declaredType());
        assertEquals("class", method.attribute(EntityAttributes.CONTAINER_KIND));
        assertEquals("Product", method.attribute(EntityAttributes.CONTAINER_NAME));
        assertEquals("Product", function.declaredType());
        assertTrue(find(EntityKind.FUNCTION, "display_name").isEmpty());
    }

    @Test
    void shouldUnwrapOptionalReturnAnnotation() {
        assertEquals("list", find(EntityKind.FUNCTION, "list_products").orElseThrow().declaredType());
    }

    @Test
    void shouldExtractFieldsFromClassBodyAndSelfAssignments() {
        CodeEntity price = find(EntityKind.FIELD, "price").orElseThrow();
        CodeEntity tags = find(EntityKind.FIELD, "tags").orElseThrow();

        assertEquals("float", price.declaredType());
        assertEquals(16, price.location().line());
        assertEquals("list", tags.declaredType());
        assertEquals("Product", tags.attribute(EntityAttributes.CONTAINER_NAME));
        assertEquals(1, entities.stream().filter(e -> e.kind() == EntityKind.FIELD && e.content().equals("title")).count(),
                "self.title повторно объявляет уже описанное поле класса");
    }

    @Test
    void shouldClassifyModuleLevelNames() {
        assertEquals("str", find(EntityKind.CONSTANT, "DEFAULT_CURRENCY").orElseThrow().declaredType());
        assertEquals("int", find(EntityKind.VARIABLE, "counter").orElseThrow().declaredType());
    }

    @Test
    void shouldInferLocalVariableTypes() {
        CodeEntity product = find(EntityKind.LOCAL_VARIABLE, "product").orElseThrow();
        CodeEntity order = find(EntityKind.LOCAL_VARIABLE, "order").orElseThrow();

        assertEquals("Product", product.declaredType());
        assertEquals("= -> Product", product.attribute(EntityAttributes.TYPE_INFERENCE));
        assertTrue(EntityAttributes.splitList(product.attribute(EntityAttributes.ASSIGNABLE_TYPES)).contains("Entity"));
        assertEquals("Order", order.declaredType());
        assertEquals("int", find(EntityKind.LOCAL_VARIABLE, "count").orElseThrow().declaredType());
        assertEquals("str", find(EntityKind.LOCAL_VARIABLE, "label").orElseThrow().declaredType());
    }

    @Test
    void shouldExtractImportsWithAliases() {
        assertTrue(find(EntityKind.IMPORT, "dataclasses.dataclass").isPresent());
        assertTrue(find(EntityKind.IMPORT, "typing.Optional").isPresent());
        assertEquals("log", find(EntityKind.IMPORT, "logging").orElseThrow().attribute(EntityAttributes.ALIAS));
    }

    @Test
    void shouldExtractDecoratorsWithTargets() {
        CodeEntity property = find(EntityKind.DECORATOR, "property").orElseThrow();
        CodeEntity route = find(EntityKind.DECORATOR, "app.route").orElseThrow();

        assertEquals("Method", property.attribute(EntityAttributes.ANNOTATION_TARGET_KIND));
        assertEquals("display_name", property.attribute(EntityAttributes.ANNOTATION_TARGET_NAME));
        assertEquals("Function", route.attribute(EntityAttributes.ANNOTATION_TARGET_KIND));
        assertEquals("list_products", route.attribute(EntityAttributes.ANNOTATION_TARGET_NAME));
    }

    @Test
    void shouldExtractLiterals() {
        assertTrue(find(EntityKind.STRING_LITERAL, "RUB").isPresent());
        assertTrue(find(EntityKind.INTEGER_LITERAL, "42").isPresent());
        assertTrue(find(EntityKind.FLOAT_LITERAL, "1.5").isPresent());
    }

    @Test
    void shouldSurviveBrokenSourceWithoutWritingToStderr(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("broken.py");
        Files.writeString(file, """
                class Broken(:
                    def ok(self):
                        return 1
                """);

        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr));
        try {
            new PythonEntityExtractor().extractEntities(file);
        } catch (RuntimeException ignored) {
        } finally {
            System.setErr(originalErr);
        }

        assertFalse(capturedErr.toString().contains("mismatched input"));
        assertFalse(capturedErr.toString().contains("extraneous input"));
    }

    private static Optional<CodeEntity> find(EntityKind kind, String name) {
        return entities.stream()
                .filter(entity -> entity.kind() == kind && entity.content().equals(name))
                .findFirst();
    }
}
