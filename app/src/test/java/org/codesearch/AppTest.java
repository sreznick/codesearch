package org.codesearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(outContent.toString().contains("index java <path>"));
        assertTrue(outContent.toString().contains("search java <kind> <query> [-f] [-cs]"));
        assertTrue(outContent.toString().contains("search java field-type String"));
    }

    @Test
    void shouldRejectUnsupportedLanguage() {
        int exitCode = App.run(new String[]{"index", "go", "src"}, out, err);

        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Пока поддерживается только язык java."));
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
    void shouldPrintFriendlyMessageForEmptySearchResults() {
        App.run(new String[]{"index", "java", "src/test/resources"}, out, err);
        int exitCode = App.run(new String[]{"search", "java", "class", "MissingClass"}, out, err);

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Найдено совпадений: 0"));
        assertTrue(outContent.toString().contains("Совпадений нет."));
    }
}
