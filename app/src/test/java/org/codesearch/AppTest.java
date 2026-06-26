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
        assertTrue(outContent.toString().contains("codesearch index [path]"));
        assertTrue(outContent.toString().contains("codesearch --cached"));
        assertTrue(outContent.toString().contains("codesearch class TestClass"));
        assertFalse(outContent.toString().contains("-r, --recursive"));
        assertFalse(outContent.toString().contains("codesearch -r"));
        assertFalse(outContent.toString().contains("Legacy-команды"));
        assertFalse(outContent.toString().contains("codesearch search java"));
    }

    @Test
    void shouldRejectUnsupportedLanguage() {
        int exitCode = App.run(new String[]{"index", "go", "src"}, out, err);

        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Пока поддерживается только язык java."));
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
    void shouldIndexAndSearchThroughSharedCli() {
        int indexExitCode = App.run(new String[]{"index", "java", "src/test/resources"}, out, err);
        int searchExitCode = App.run(new String[]{"search", "java", "class", "TestClass"}, out, err);

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
    void shouldPrintDeclaredTypeForField() {
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err);
        int exitCode = App.run(new String[]{"search", "java", "field", "testField"}, out, err);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Field"));
        assertTrue(outContent.toString().contains("testField"));
        assertTrue(outContent.toString().contains("[String]"));
        assertTrue(outContent.toString().contains("src/test/resources/TestClass.java:7"));
    }

    @Test
    void shouldSearchFieldByDeclaredType() {
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err);
        int exitCode = App.run(new String[]{"search", "java", "field-type", "String"}, out, err);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("testField"));
        assertTrue(outContent.toString().contains("testFieldDuplicate"));
        assertTrue(outContent.toString().contains("[String]"));
    }

    @Test
    void shouldSearchMethodByReturnType() {
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err);
        int exitCode = App.run(new String[]{"search", "java", "method-return-type", "String"}, out, err);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Найдено совпадений: 1"));
        assertTrue(outContent.toString().contains("Method"));
        assertTrue(outContent.toString().contains("getTestField"));
        assertTrue(outContent.toString().contains("[String]"));
    }

    @Test
    void shouldSearchVariablesAssignableToTypeThroughLegacySearchCommand() {
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err);
        int exitCode = App.run(new String[]{"search", "java", "variable-assignable-to", "Appendable"}, out, err);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Найдено совпадений: 1"));
        assertTrue(outContent.toString().contains("inferredBuilder"));
        assertTrue(outContent.toString().contains("[StringBuilder]"));
    }

    @Test
    void shouldLimitSearchResults() {
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err);
        int exitCode = App.run(new String[]{"search", "java", "field-type", "String", "--limit", "1"}, out, err);

        String output = outContent.toString();
        assertEquals(0, exitCode);
        assertTrue(output.contains("Найдено совпадений: 2"));
        assertTrue(output.contains("testField"));
        assertFalse(output.contains("testFieldDuplicate"));
    }

    @Test
    void shouldFilterSearchResultsByPath() {
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err);
        int exitCode = App.run(new String[]{"search", "java", "class", "TestClass", "--path", "missing/path"}, out, err);

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
    void shouldPrintFriendlyMessageForEmptySearchResults() {
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err);
        int exitCode = App.run(new String[]{"search", "java", "class", "MissingClass"}, out, err);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Найдено совпадений: 0"));
        assertTrue(outContent.toString().contains("Совпадений нет."));
    }
}
