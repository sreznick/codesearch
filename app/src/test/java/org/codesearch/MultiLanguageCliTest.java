package org.codesearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiLanguageCliTest {
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private PrintStream out;
    private PrintStream err;

    @BeforeEach
    void setUp() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        out = new PrintStream(outContent);
        err = new PrintStream(errContent);
    }

    @Test
    void shouldIndexAllLanguagesByDefault(@TempDir Path tempDir) {
        int exitCode = App.run(new String[]{"index", "src/test/resources"}, out, err, tempDir.resolve("index"));

        String output = outContent.toString();
        assertEquals(0, exitCode, errContent.toString());
        assertTrue(output.contains("Язык: java"));
        assertTrue(output.contains("Язык: go"));
        assertTrue(output.contains("Язык: python"));
    }

    @Test
    void shouldSearchCachedIndexAcrossLanguages(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        App.run(new String[]{"index", "src/test/resources"}, out, err, indexPath);
        outContent.reset();

        int exitCode = App.run(new String[]{"--cached", "method", "t"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode, errContent.toString());
        assertTrue(output.contains("Method testMethod"));
        assertTrue(output.contains("Method Print"));
        assertTrue(output.contains("Method total"));
        assertTrue(output.contains("[java]") && output.contains("[go]") && output.contains("[python]"),
                "при результатах из нескольких языков язык показывается явно");
    }

    @Test
    void shouldKeepJavaIndexAfterIndexingGo(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err, indexPath);
        App.run(new String[]{"index", "--lang", "go", "src/test/resources/go"}, out, err, indexPath);
        outContent.reset();

        int javaExit = App.run(new String[]{"search", "java", "class", "TestClass"}, out, err, indexPath);
        int goExit = App.run(new String[]{"search", "go", "struct", "BitSet"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, javaExit);
        assertEquals(0, goExit);
        assertFalse(output.contains("Найдено совпадений: 0"));
    }

    @Test
    void shouldPrintStatsForEveryIndexedLanguage(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        App.run(new String[]{"index", "src/test/resources"}, out, err, indexPath);
        outContent.reset();

        int exitCode = App.run(new String[]{"stats"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Язык: java"));
        assertTrue(output.contains("Язык: go"));
        assertTrue(output.contains("Язык: python"));
        assertTrue(output.contains("Decorator:"));
    }

    @Test
    void shouldFindPythonDecoratorWithTarget() {
        int exitCode = App.run(new String[]{"decorator", "route", "src/test/resources/python"}, out, err);

        String output = outContent.toString();
        assertEquals(0, exitCode, errContent.toString());
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Decorator app.route on Function list_products"));
    }

    @Test
    void shouldFindPythonClassWithBaseAndMethodContainer() {
        int classExit = App.run(new String[]{"--lang", "python", "class", "Product", "src/test/resources"}, out, err);
        int methodExit = App.run(new String[]{"--lang", "python", "method", "total", "src/test/resources"}, out, err);

        String output = outContent.toString();
        assertEquals(0, classExit);
        assertEquals(0, methodExit);
        assertTrue(output.contains("Class Product extends Entity"));
        assertTrue(output.contains("Method total  [float] in class Order"));
    }

    @Test
    void shouldExplainPythonAssignableSearch() {
        int exitCode = App.run(new String[]{"variable-assignable-to", "Entity", "src/test/resources/python", "--explain"}, out, err);

        String output = outContent.toString();
        assertEquals(0, exitCode, errContent.toString());
        assertTrue(output.contains("LocalVariable product  [Product] in function list_products"));
        assertTrue(output.contains("explain: = -> Product -> Entity"));
        assertTrue(output.contains("совместимые типы: Product, Entity, object"));
    }

    @Test
    void shouldSearchGoFunctionsByReturnType(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("go");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("factory.go"), """
                package factory

                type Service struct{}

                func NewService() *Service { return &Service{} }

                func Count() int { return 1 }
                """);

        int exitCode = App.run(new String[]{"function-return-type", "*Service", sourcePath.toString()}, out, err);

        String output = outContent.toString();
        assertEquals(0, exitCode, errContent.toString());
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Function NewService  [*Service]"));
    }

    @Test
    void shouldExplainGoImplicitInterfaceImplementation(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("go");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("shapes.go"), """
                package shapes

                type Shape interface {
                    Area() float64
                }

                type Square struct {
                    side float64
                }

                func (s Square) Area() float64 { return s.side * s.side }

                func main() {
                    square := Square{side: 2}
                    _ = square
                }
                """);

        int exitCode = App.run(new String[]{"variable-assignable-to", "Shape", sourcePath.toString(), "--explain"}, out, err);

        String output = outContent.toString();
        assertEquals(0, exitCode, errContent.toString());
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("LocalVariable square  [Square]"));
        assertTrue(output.contains("explain: := -> Square -> Shape"));
    }

    @Test
    void shouldRejectKindUnsupportedByAllFoundLanguages(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("go");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("main.go"), "package main\n");

        int exitCode = App.run(new String[]{"annotation", "Deprecated", sourcePath.toString()}, out, err);

        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Язык go не поддерживает вид сущности: annotation"));
    }

    @Test
    void shouldListSubtypesWithImplementedInterfaces() {
        int exitCode = App.run(new String[]{"subtypes-of", "Shape", "src/test/resources", "--explain"}, out, err);

        String output = outContent.toString();
        assertEquals(0, exitCode, errContent.toString());
        assertTrue(output.contains("Найдено совпадений: 3"));
        assertTrue(output.contains("Struct Cube implements Shape, Solid"));
        assertTrue(output.contains("Interface Solid extends Shape"));
        assertTrue(output.contains("explain: надтипы: Shape"));
    }

    @Test
    void shouldShowCallSitesWithQualifierAndContainer() {
        int exitCode = App.run(new String[]{"calls", "Area", "src/test/resources", "-cs"}, out, err);

        String output = outContent.toString();
        assertEquals(0, exitCode, errContent.toString());
        assertTrue(output.contains("Call shape.Area in function Describe"));
    }

    @Test
    void shouldShowConstructorCalls() {
        int exitCode = App.run(new String[]{"--lang", "java", "calls", "StringBuilder", "src/test/resources"}, out, err);

        String output = outContent.toString();
        assertEquals(0, exitCode, errContent.toString());
        assertTrue(output.contains("Call new StringBuilder in method TestClass.testMethod"));
    }

    @Test
    void shouldRestrictResultsToContainer() {
        int exitCode = App.run(new String[]{"calls", "--in", "list_products", "--path", "src/test/resources"}, out, err);

        String output = outContent.toString();
        assertEquals(0, exitCode, errContent.toString());
        assertTrue(output.contains("Найдено совпадений: 2"));
        assertTrue(output.contains("Call make_product in function list_products"));
        assertTrue(output.contains("Call Order in function list_products"));
    }

    @Test
    void shouldPrintBenchmarkReport() {
        int exitCode = App.run(new String[]{"bench", "src/test/resources", "--repeat", "2"}, out, err);

        String output = outContent.toString();
        assertEquals(0, exitCode, errContent.toString());
        assertTrue(output.contains("## Индексация: src/test/resources"));
        assertTrue(output.contains("| java |"));
        assertTrue(output.contains("Время индексации:"));
        assertTrue(output.contains("## Запросы (повторов: 2)"));
        assertTrue(output.contains("Наследники/реализации"));
    }
}
