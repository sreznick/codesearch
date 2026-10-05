package org.codesearch.go;

import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityAttributes;
import org.codesearch.core.EntityKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoEntityExtractorTest {
    @Test
    void shouldNotPrintAntlrErrorsToSystemErr(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("broken.go");
        Files.writeString(file, """
                package demo

                func broken() {
                    _ = )
                }
                """);

        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capturedErr));
        try {
            new GoLanguagePlugin().createExtractor(List.of(file)).extractEntities(file);
        } catch (RuntimeException ignored) {
            // The extractor may fail on broken Go code, but ANTLR must not write directly to stderr.
        } finally {
            System.setErr(originalErr);
        }

        assertFalse(capturedErr.toString().contains("mismatched input"));
    }

    @Test
    void shouldDetectImplicitInterfaceImplementationAcrossFiles(@TempDir Path tempDir) throws IOException {
        Path types = tempDir.resolve("types.go");
        Path usage = tempDir.resolve("usage.go");
        Files.writeString(types, """
                package demo

                type Named interface {
                    Name() string
                }

                type Greeter interface {
                    Named
                    Greet(who string) string
                }

                type English struct {
                    prefix string
                }

                func (e *English) Name() string { return "en" }

                func (e *English) Greet(who string) string { return e.prefix + who }

                type Silent struct{}

                func (s Silent) Name() string { return "" }

                func NewEnglish() *English {
                    return &English{prefix: "Hello, "}
                }
                """);
        Files.writeString(usage, """
                package demo

                var fallback Silent

                func run() {
                    greeter := NewEnglish()
                    literal := &English{}
                    count := 3
                    var label string
                    _ = greeter
                }
                """);

        List<Path> files = List.of(types, usage);
        List<CodeEntity> entities = new GoLanguagePlugin().createExtractor(files).extractEntities(usage);

        CodeEntity greeter = find(entities, EntityKind.LOCAL_VARIABLE, "greeter");
        assertEquals("*English", greeter.declaredType());
        assertEquals(":= -> *English", greeter.attribute(EntityAttributes.TYPE_INFERENCE));
        assertTrue(assignableTypes(greeter).containsAll(List.of("English", "Greeter", "Named")));

        assertEquals("*English", find(entities, EntityKind.LOCAL_VARIABLE, "literal").declaredType());
        assertEquals("int", find(entities, EntityKind.LOCAL_VARIABLE, "count").declaredType());
        assertEquals("string", find(entities, EntityKind.LOCAL_VARIABLE, "label").declaredType());

        CodeEntity fallback = find(entities, EntityKind.VARIABLE, "fallback");
        assertTrue(assignableTypes(fallback).contains("Named"));
        assertFalse(assignableTypes(fallback).contains("Greeter"), "у Silent нет метода Greet");
    }

    @Test
    void shouldExtractGoDeclarationsWithContainers() throws IOException {
        Path file = Path.of("src/test/resources/go/sample.go");
        List<CodeEntity> entities = new GoLanguagePlugin().createExtractor(List.of(file)).extractEntities(file);

        CodeEntity add = find(entities, EntityKind.METHOD, "Add");
        assertEquals("type", add.attribute(EntityAttributes.CONTAINER_KIND));
        assertEquals("BitSet", add.attribute(EntityAttributes.CONTAINER_NAME));

        CodeEntity print = find(entities, EntityKind.METHOD, "Print");
        assertEquals("interface", print.attribute(EntityAttributes.CONTAINER_KIND));
        assertEquals("error", print.declaredType());

        assertEquals("int", find(entities, EntityKind.FUNCTION, "intMin").declaredType());
        assertEquals("[]uint64", find(entities, EntityKind.FIELD, "data").declaredType());
        assertEquals("int", find(entities, EntityKind.CONSTANT, "bitsPerWord").declaredType());
        assertTrue(entities.stream().anyMatch(e -> e.kind() == EntityKind.PACKAGE && e.content().equals("sample")));
    }

    private static CodeEntity find(List<CodeEntity> entities, EntityKind kind, String name) {
        return entities.stream()
                .filter(entity -> entity.kind() == kind && entity.content().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Не найдено: " + kind + " " + name));
    }

    private static java.util.Set<String> assignableTypes(CodeEntity entity) {
        return EntityAttributes.splitList(entity.attribute(EntityAttributes.ASSIGNABLE_TYPES));
    }
}
