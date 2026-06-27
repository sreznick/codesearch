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

class AppTest {
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
    void shouldPrintHelpWithoutArguments() {
        int exitCode = App.run(new String[]{}, out, err);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("codesearch [options] <query> [path]"));
        assertTrue(outContent.toString().contains("codesearch [options] <kind> <query> [path]"));
        assertTrue(outContent.toString().contains("codesearch index [--lang java|go] [path]"));
        assertTrue(outContent.toString().contains("codesearch stats [--lang java|go] [--path PATH]"));
        assertTrue(outContent.toString().contains("codesearch --cached"));
        assertTrue(outContent.toString().contains("codesearch class TestClass"));
        assertTrue(outContent.toString().contains("codesearch stats"));
        assertTrue(outContent.toString().contains("annotation <name>"));
        assertTrue(outContent.toString().contains("codesearch annotation DemoController"));
        assertTrue(outContent.toString().contains("field-type <type>"));
        assertTrue(outContent.toString().contains("local-variable-type <type>"));
        assertTrue(outContent.toString().contains("method-return-type <type>"));
        assertTrue(outContent.toString().contains("variable-assignable-to <type>"));
        assertTrue(outContent.toString().contains("codesearch --cached variable-assignable-to Printable --explain"));
        assertTrue(outContent.toString().contains("--json"));
        assertTrue(outContent.toString().contains("--snippet"));
        assertTrue(outContent.toString().contains("-C, --context N"));
        assertTrue(outContent.toString().contains("codesearch --cached annotation DemoController --json"));
        assertTrue(outContent.toString().contains("codesearch method getTestField --snippet"));
        assertTrue(outContent.toString().contains("--lang java|go"));
        assertTrue(outContent.toString().contains("Java: class|method|field|interface|local-variable <name>"));
        assertTrue(outContent.toString().contains("Go: package|import|function|method|struct|interface|field|var|const <name>"));
        assertTrue(outContent.toString().contains("codesearch index --lang go app/src/test/resources/go"));
        assertTrue(outContent.toString().contains("codesearch --lang go function intMin app/src/test/resources/go"));
        assertFalse(outContent.toString().contains("Go MVP"));
        assertTrue(outContent.toString().contains("implements/extends"));
        assertTrue(outContent.toString().contains("var -> Dog -> Animal"));
        assertFalse(outContent.toString().contains("-r, --recursive"));
        assertFalse(outContent.toString().contains("codesearch -r"));
        assertFalse(outContent.toString().contains("Legacy-команды"));
        assertFalse(outContent.toString().contains("codesearch search java"));
    }

    @Test
    void shouldRejectUnsupportedLanguage() {
        int exitCode = App.run(new String[]{"index", "--lang", "python", "src"}, out, err);

        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Неподдерживаемый язык: python"));
    }

    @Test
    void shouldRunGrepStyleSearchWithKindFilter(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    private String magicToken = "needle";

                    public void magicMethod() {
                    }
                }
                """);

        int exitCode = App.run(
                new String[]{"field", "magic", sourcePath.toString()},
                out,
                err,
                tempDir.resolve("index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Field"));
        assertTrue(output.contains("magicToken"));
        assertTrue(output.contains("in class SampleSearch"));
        assertFalse(output.contains("magicMethod"));
    }

    @Test
    void shouldRunGrepAliasCaseInsensitiveByDefault(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                }
                """);

        int exitCode = App.run(
                new String[]{"grep", "-r", "sample", sourcePath.toString(), "--kind", "class"},
                out,
                err,
                tempDir.resolve("index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Class"));
        assertTrue(output.contains("SampleSearch"));
    }

    @Test
    void shouldAcceptLanguagePrefixInQuickSearch(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                }
                """);

        int exitCode = App.run(
                new String[]{"java", "class", "SampleSearch", sourcePath.toString()},
                out,
                err,
                tempDir.resolve("index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Class"));
        assertTrue(output.contains("SampleSearch"));
    }

    @Test
    void shouldIgnoreBuildOutputInQuickSearch(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("project");
        Path sourceFile = sourcePath.resolve("src/test/resources/TestClass.java");
        Path buildFile = sourcePath.resolve("build/resources/test/TestClass.java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(buildFile.getParent());
        String content = """
                public class TestClass {
                }
                """;
        Files.writeString(sourceFile, content);
        Files.writeString(buildFile, content);

        int exitCode = App.run(
                new String[]{"class", "TestClass", sourcePath.toString()},
                out,
                err,
                tempDir.resolve("index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("src/test/resources/TestClass.java:1"));
        assertFalse(output.contains("build/resources/test/TestClass.java"));
    }

    @Test
    void shouldIndexClassEvenWhenOtherExtractorsSkipUnsupportedCode(@TempDir Path tempDir) {
        int exitCode = App.run(
                new String[]{"class", "ConsoleAppTest", "src/test/java/org/example"},
                out,
                err,
                tempDir.resolve("index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Class"));
        assertTrue(output.contains("ConsoleAppTest"));
        assertTrue(output.contains("src/test/java/org/example/ConsoleAppTest.java:10"));
    }

    @Test
    void shouldIndexAndSearchCachedWithShortCommands(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    private String cachedField = "value";
                }
                """);

        int indexExitCode = App.run(new String[]{"index", sourcePath.toString()}, out, err, indexPath);
        int searchExitCode = App.run(new String[]{"--cached", "class", "SampleSearch"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, indexExitCode);
        assertEquals(0, searchExitCode);
        assertTrue(output.contains("Индексация завершена"));
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Class"));
        assertTrue(output.contains("SampleSearch"));
    }

    @Test
    void shouldRunQuickGoFunctionSearch(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("go-sources");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("sample.go"), sampleGoSource());

        int exitCode = App.run(
                new String[]{"--lang", "go", "function", "intMin", sourcePath.toString()},
                out,
                err,
                tempDir.resolve("index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Function"));
        assertTrue(output.contains("intMin"));
        assertTrue(output.contains("sample.go:10"));
    }

    @Test
    void shouldIndexAndSearchCachedGoStructAndImport(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("go-sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("sample.go"), sampleGoSource());

        int indexExitCode = App.run(new String[]{"index", "--lang", "go", sourcePath.toString()}, out, err, indexPath);
        int structExitCode = App.run(new String[]{"--cached", "--lang", "go", "struct", "BitSet"}, out, err, indexPath);
        int importExitCode = App.run(new String[]{"--cached", "--lang", "go", "import", "fmt"}, out, err, indexPath);
        int fieldExitCode = App.run(new String[]{"--cached", "--lang", "go", "field", "name"}, out, err, indexPath);
        int methodExitCode = App.run(new String[]{"--cached", "--lang", "go", "method", "Add"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, indexExitCode);
        assertEquals(0, structExitCode);
        assertEquals(0, importExitCode);
        assertEquals(0, fieldExitCode);
        assertEquals(0, methodExitCode);
        assertTrue(output.contains("Язык: go"));
        assertTrue(output.contains("Struct"));
        assertTrue(output.contains("BitSet"));
        assertTrue(output.contains("Import"));
        assertTrue(output.contains("fmt"));
        assertTrue(output.contains("Field"));
        assertTrue(output.contains("name"));
        assertTrue(output.contains("in struct BitSet"));
        assertTrue(output.contains("Method"));
        assertTrue(output.contains("Add"));
        assertTrue(output.contains("in type BitSet"));
    }

    @Test
    void shouldPrintGoIndexStats(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("go-sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("sample.go"), sampleGoSource());

        App.run(new String[]{"index", "--lang", "go", sourcePath.toString()}, out, err, indexPath);
        outContent.reset();

        int exitCode = App.run(new String[]{"stats", "--lang", "go"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Статистика индекса"));
        assertTrue(output.contains("Язык: go"));
        assertTrue(output.contains("Файлов: 1"));
        assertTrue(output.contains("Function:"));
        assertTrue(output.contains("Struct:"));
        assertTrue(output.contains("Import:"));
    }

    @Test
    void shouldPrintIndexStats(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    private String cachedField = "value";

                    public void run() {
                    }
                }
                """);

        App.run(new String[]{"index", sourcePath.toString()}, out, err, indexPath);
        outContent.reset();

        int exitCode = App.run(new String[]{"stats"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Статистика индекса"));
        assertTrue(output.contains("Язык: java"));
        assertTrue(output.contains("Файлов: 1"));
        assertTrue(output.contains("Сущностей:"));
        assertTrue(output.contains("Class:"));
        assertTrue(output.contains("Field:"));
        assertTrue(output.contains("Method:"));
    }

    @Test
    void shouldPrintIndexStatsWithPathFilter(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("First.java"), """
                public class FirstSearch {
                }
                """);
        Files.writeString(sourcePath.resolve("Second.java"), """
                public class SecondSearch {
                }
                """);

        App.run(new String[]{"index", sourcePath.toString()}, out, err, indexPath);
        outContent.reset();

        int exitCode = App.run(new String[]{"stats", "--path", "First.java"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Фильтр пути: First.java"));
        assertTrue(output.contains("Файлов: 1"));
        assertTrue(output.contains("Сущностей: 1"));
    }

    @Test
    void shouldSearchCachedDeclaredType(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    private String cachedField = "value";
                }
                """);

        App.run(new String[]{"index", sourcePath.toString()}, out, err, indexPath);
        int exitCode = App.run(new String[]{"--cached", "field-type", "String"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Field"));
        assertTrue(output.contains("cachedField"));
        assertTrue(output.contains("[String]"));
        assertTrue(output.contains("in class SampleSearch"));
    }

    @Test
    void shouldSearchCachedVariablesAssignableToType(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    public void run() {
                        var builder = new StringBuilder("hello");
                    }
                }
                """);

        App.run(new String[]{"index", sourcePath.toString()}, out, err, indexPath);
        int exitCode = App.run(new String[]{"--cached", "variable-assignable-to", "Appendable"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("LocalVariable"));
        assertTrue(output.contains("builder"));
        assertTrue(output.contains("[StringBuilder]"));
    }

    @Test
    void shouldExplainAssignableTypeSearch(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    public void run() {
                        var builder = new StringBuilder("hello");
                    }
                }
                """);

        App.run(new String[]{"index", sourcePath.toString()}, out, err, indexPath);
        int exitCode = App.run(new String[]{"--cached", "variable-assignable-to", "Appendable", "--explain"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("builder"));
        assertTrue(output.contains("explain: var -> StringBuilder -> Appendable"));
        assertTrue(output.contains("совместимые типы: StringBuilder, Appendable"));
    }

    @Test
    void shouldRunQuickAssignableTypeSearchWithExplain(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    public void run() {
                        var builder = new StringBuilder("hello");
                    }
                }
                """);

        int exitCode = App.run(
                new String[]{"variable-assignable-to", "Appendable", sourcePath.toString(), "--explain"},
                out,
                err,
                tempDir.resolve("unused-index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("builder"));
        assertTrue(output.contains("[StringBuilder]"));
        assertTrue(output.contains("explain: var -> StringBuilder -> Appendable"));
        assertFalse(errContent.toString().contains("Путь не найден: Appendable"));
        assertFalse(errContent.toString().contains("Неизвестный флаг: --explain"));
    }

    @Test
    void shouldExplainProjectInterfaceImplementation(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Printable.java"), """
                interface Printable {
                    void print();
                }
                """);
        Files.writeString(sourcePath.resolve("Report.java"), """
                class Report implements Printable {
                    public void print() {
                    }
                }
                """);
        Files.writeString(sourcePath.resolve("ReportUsage.java"), """
                class ReportUsage {
                    void run() {
                        var report = new Report();
                    }
                }
                """);

        App.run(new String[]{"index", sourcePath.toString()}, out, err, indexPath);
        int exitCode = App.run(new String[]{"--cached", "variable-assignable-to", "Printable", "--explain"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("report"));
        assertTrue(output.contains("[Report]"));
        assertTrue(output.contains("explain: var -> Report -> Printable"));
        assertTrue(output.contains("совместимые типы: Report, Object, Printable"));
    }

    @Test
    void shouldExplainProjectClassInheritance(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Animal.java"), """
                class Animal {
                }
                """);
        Files.writeString(sourcePath.resolve("Dog.java"), """
                class Dog extends Animal {
                }
                """);
        Files.writeString(sourcePath.resolve("DogUsage.java"), """
                class DogUsage {
                    void run() {
                        var dog = new Dog();
                    }
                }
                """);

        App.run(new String[]{"index", sourcePath.toString()}, out, err, indexPath);
        int exitCode = App.run(new String[]{"--cached", "variable-assignable-to", "Animal", "--explain"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("dog"));
        assertTrue(output.contains("[Dog]"));
        assertTrue(output.contains("explain: var -> Dog -> Animal"));
        assertTrue(output.contains("совместимые типы: Dog, Object, Animal"));
    }

    @Test
    void shouldPrintQuickSearchResultsAsJson(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    private String jsonField = "value";
                }
                """);

        int exitCode = App.run(
                new String[]{"field", "jsonField", sourcePath.toString(), "--json"},
                out,
                err,
                tempDir.resolve("index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.trim().startsWith("{"));
        assertTrue(output.contains("\"totalHits\": 1"));
        assertTrue(output.contains("\"kind\": \"Field\""));
        assertTrue(output.contains("\"name\": \"jsonField\""));
        assertTrue(output.contains("\"declaredType\": \"String\""));
        assertFalse(output.contains("Найдено совпадений"));
    }

    @Test
    void shouldPrintCachedSearchResultsAsJson(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                @DemoController
                class SampleSearch {
                }
                """);

        App.run(new String[]{"index", sourcePath.toString()}, out, err, indexPath);
        outContent.reset();

        int exitCode = App.run(new String[]{"--cached", "annotation", "DemoController", "--json"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("\"totalHits\": 1"));
        assertTrue(output.contains("\"kind\": \"Annotation\""));
        assertTrue(output.contains("\"name\": \"DemoController\""));
        assertTrue(output.contains("\"annotationTargetKind\": \"Class\""));
        assertTrue(output.contains("\"annotationTargetName\": \"SampleSearch\""));
    }

    @Test
    void shouldPrintEmptySearchResultAsJson(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err, indexPath);
        outContent.reset();

        int exitCode = App.run(new String[]{"search", "java", "class", "MissingClass", "--json"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("\"totalHits\": 0"));
        assertTrue(output.contains("\"results\": ["));
        assertFalse(output.contains("Совпадений нет"));
    }

    @Test
    void shouldIncludeExplanationInJson(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    public void run() {
                        var builder = new StringBuilder("hello");
                    }
                }
                """);

        App.run(new String[]{"index", sourcePath.toString()}, out, err, indexPath);
        outContent.reset();

        int exitCode = App.run(
                new String[]{"--cached", "variable-assignable-to", "Appendable", "--explain", "--json"},
                out,
                err,
                indexPath
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("\"explanation\": ["));
        assertTrue(output.contains("\"var -> StringBuilder -> Appendable\""));
        assertTrue(output.contains("\"совместимые типы: StringBuilder, Appendable"));
    }

    @Test
    void shouldPrintSnippetForQuickSearch(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    private String beforeField = "before";
                    private String targetField = "value";
                    private String afterField = "after";
                }
                """);

        int exitCode = App.run(
                new String[]{"field", "targetField", sourcePath.toString(), "--snippet"},
                out,
                err,
                tempDir.resolve("index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Field targetField"));
        assertTrue(output.contains("2 |     private String beforeField"));
        assertTrue(output.contains(">    3 |     private String targetField"));
        assertTrue(output.contains("4 |     private String afterField"));
    }

    @Test
    void shouldRespectSnippetContextFlags(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    private String beforeField = "before";
                    private String targetField = "value";
                    private String afterField = "after";
                }
                """);

        int exitCode = App.run(
                new String[]{"field", "targetField", sourcePath.toString(), "-B", "1", "-A", "0"},
                out,
                err,
                tempDir.resolve("index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("2 |     private String beforeField"));
        assertTrue(output.contains(">    3 |     private String targetField"));
        assertFalse(output.contains("4 |     private String afterField"));
    }

    @Test
    void shouldIncludeSnippetInJson(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Files.createDirectories(sourcePath);
        Files.writeString(sourcePath.resolve("Sample.java"), """
                public class SampleSearch {
                    private String targetField = "value";
                }
                """);

        int exitCode = App.run(
                new String[]{"field", "targetField", sourcePath.toString(), "--context", "0", "--json"},
                out,
                err,
                tempDir.resolve("index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("\"snippet\": ["));
        assertTrue(output.contains("\"line\": 2"));
        assertTrue(output.contains("\"match\": true"));
        assertTrue(output.contains("\"text\": \"    private String targetField = \\\"value\\\";\""));
    }

    @Test
    void shouldRejectInvalidSnippetContext() {
        int exitCode = App.run(new String[]{"class", "TestClass", "--context", "abc"}, out, err);

        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Количество строк для --context должно быть числом"));
    }

    @Test
    void shouldSearchAnnotationByName(@TempDir Path tempDir) {
        int exitCode = App.run(
                new String[]{"annotation", "DemoController", "src/test/resources"},
                out,
                err,
                tempDir.resolve("index")
        );

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Annotation DemoController on Class AnnotationFixture"));
        assertTrue(output.contains("src/test/resources/TestClass.java"));
    }

    @Test
    void shouldIndexAndSearchThroughSharedCli(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        int indexExitCode = App.run(new String[]{"index", "java", "src/test/resources"}, out, err, indexPath);
        int searchExitCode = App.run(new String[]{"search", "java", "class", "TestClass"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, indexExitCode);
        assertEquals(0, searchExitCode);
        assertTrue(output.contains("Индексация завершена"));
        assertTrue(output.contains("Путь:  src/test/resources"));
        assertTrue(output.contains("Найдено совпадений: 1"));
        assertTrue(output.contains("Class"));
        assertTrue(output.contains("TestClass"));
        assertTrue(output.contains("src/test/resources/TestClass.java:6"));
    }

    @Test
    void shouldPrintDeclaredTypeForField(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err, indexPath);
        int exitCode = App.run(new String[]{"search", "java", "field", "testField"}, out, err, indexPath);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Field"));
        assertTrue(outContent.toString().contains("testField"));
        assertTrue(outContent.toString().contains("[String]"));
        assertTrue(outContent.toString().contains("src/test/resources/TestClass.java:7"));
    }

    @Test
    void shouldSearchFieldByDeclaredType(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err, indexPath);
        int exitCode = App.run(new String[]{"search", "java", "field-type", "String"}, out, err, indexPath);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("testField"));
        assertTrue(outContent.toString().contains("testFieldDuplicate"));
        assertTrue(outContent.toString().contains("[String]"));
    }

    @Test
    void shouldSearchMethodByReturnType(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err, indexPath);
        int exitCode = App.run(new String[]{"search", "java", "method-return-type", "String"}, out, err, indexPath);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Найдено совпадений: 1"));
        assertTrue(outContent.toString().contains("Method"));
        assertTrue(outContent.toString().contains("getTestField"));
        assertTrue(outContent.toString().contains("[String]"));
    }

    @Test
    void shouldSearchVariablesAssignableToTypeThroughLegacySearchCommand(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err, indexPath);
        int exitCode = App.run(new String[]{"search", "java", "variable-assignable-to", "Appendable"}, out, err, indexPath);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Найдено совпадений: 1"));
        assertTrue(outContent.toString().contains("inferredBuilder"));
        assertTrue(outContent.toString().contains("[StringBuilder]"));
    }

    @Test
    void shouldLimitSearchResults(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err, indexPath);
        int exitCode = App.run(new String[]{"search", "java", "field-type", "String", "--limit", "1"}, out, err, indexPath);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 3"));
        assertTrue(output.contains("testField"));
        assertFalse(output.contains("testFieldDuplicate"));
    }

    @Test
    void shouldFilterSearchResultsByPath(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err, indexPath);
        int exitCode = App.run(new String[]{"search", "java", "class", "TestClass", "--path", "missing/path"}, out, err, indexPath);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Найдено совпадений: 0"));
        assertTrue(outContent.toString().contains("Совпадений нет."));
    }

    @Test
    void shouldRejectInvalidLimit() {
        int exitCode = App.run(new String[]{"search", "java", "class", "TestClass", "--limit", "abc"}, out, err);

        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Лимит должен быть числом"));
    }

    @Test
    void shouldRejectNonPositiveLimit() {
        int exitCode = App.run(new String[]{"search", "java", "class", "TestClass", "--limit", "0"}, out, err);

        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Лимит должен быть положительным числом"));
    }

    @Test
    void shouldExplainMissingIndexBeforeSearch(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("missing-index");

        int exitCode = App.run(new String[]{"search", "java", "class", "TestClass"}, out, err, indexPath);

        String errorOutput = errContent.toString();
        assertEquals(1, exitCode);
        assertTrue(errorOutput.contains("Индекс не готов"));
        assertTrue(errorOutput.contains("Индекс не найден"));
        assertTrue(errorOutput.contains("index java <path>"));
        assertFalse(errorOutput.contains("Exception"));
    }

    @Test
    void shouldExplainMissingIndexBeforeStats(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("missing-index");

        int exitCode = App.run(new String[]{"stats"}, out, err, indexPath);

        String errorOutput = errContent.toString();
        assertEquals(1, exitCode);
        assertTrue(errorOutput.contains("Индекс не готов"));
        assertTrue(errorOutput.contains("Индекс не найден"));
        assertTrue(errorOutput.contains("codesearch index [path]"));
    }

    @Test
    void shouldExplainEmptyIndexBeforeSearch(@TempDir Path tempDir) throws IOException {
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(indexPath);

        int exitCode = App.run(new String[]{"search", "java", "class", "TestClass"}, out, err, indexPath);

        String errorOutput = errContent.toString();
        assertEquals(1, exitCode);
        assertTrue(errorOutput.contains("Индекс не готов"));
        assertTrue(errorOutput.contains("Индекс пуст"));
        assertTrue(errorOutput.contains("index java <path>"));
    }

    @Test
    void shouldExplainBrokenIndexBeforeSearch(@TempDir Path tempDir) throws IOException {
        Path indexPath = tempDir.resolve("index");
        Files.createDirectories(indexPath);
        Files.writeString(indexPath.resolve("segments_1"), "not a lucene index");

        int exitCode = App.run(new String[]{"search", "java", "class", "TestClass"}, out, err, indexPath);

        String errorOutput = errContent.toString();
        assertEquals(1, exitCode);
        assertTrue(errorOutput.contains("Индекс не готов"));
        assertTrue(errorOutput.contains("поврежден"));
        assertTrue(errorOutput.contains("index java <path>"));
    }

    @Test
    void shouldRejectIndexingPathWithoutJavaFiles(@TempDir Path tempDir) throws IOException {
        Path sourcePath = tempDir.resolve("sources");
        Files.createDirectories(sourcePath);

        int exitCode = App.run(new String[]{"index", "java", sourcePath.toString()}, out, err, tempDir.resolve("index"));

        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("В указанном пути нет .java файлов"));
    }

    @Test
    void shouldPrintFriendlyMessageForEmptySearchResults(@TempDir Path tempDir) {
        Path indexPath = tempDir.resolve("index");
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err, indexPath);
        int exitCode = App.run(new String[]{"search", "java", "class", "MissingClass"}, out, err, indexPath);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Найдено совпадений: 0"));
        assertTrue(outContent.toString().contains("Совпадений нет."));
    }

    private String sampleGoSource() {
        return """
                package sample

                import (
                    "fmt"
                    "strings"
                )

                const bitsPerWord = 64

                func intMin(a, b int) int {
                    if a < b {
                        return a
                    }
                    return b
                }

                type BitSet struct {
                    data []uint64
                    name string
                }

                type Printer interface {
                    Print(value string) error
                }

                var defaultName string = "main"

                func (b *BitSet) Add(value int) {
                    fmt.Println(strings.TrimSpace(defaultName), value)
                }
                """;
    }
}
