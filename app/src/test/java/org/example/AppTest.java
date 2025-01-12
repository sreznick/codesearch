package org.example;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.MMapDirectory;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

public class AppTest {

    private static MMapDirectory directory;
    private static IndexWriter writer;

    @BeforeAll
    static void setUpIndex() throws IOException {
        deleteDirectoryRecursively(Paths.get("index"));
        JavaSourceIndexer.indexJavaSources("src/test/resources");
    }

    private static void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            throw new RuntimeException("Ошибка при удалении файла: " + file, e);
                        }
                    });
        }
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (directory != null) {
            directory.close();
        }
    }

    @Test
    public void testFindStringConstants() {
        StringConstantQuery.findStringConstants("Test String", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c Test String: 2"));
        assertTrue(logOutput.contains("StringConstant: Test String, Файл: src/test/resources/TestClass.java, Строка: 7"));
        assertTrue(logOutput.contains("StringConstant: Test String, Файл: src/test/resources/TestClass.java, Строка: 12"));
    }

    @Test
    public void testFindClass() {
        StringConstantQuery.findClass("TestClass", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c TestClass: 1"));
        assertTrue(logOutput.contains("Class: TestClass, Файл: src/test/resources/TestClass.java, Строка: 6"));
    }

    @Test
    public void testFindMethod() {
        StringConstantQuery.findMethod("testMethod", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c testMethod: 2"));
        assertTrue(logOutput.contains("Method: testMethod, Файл: src/test/resources/TestClass.java, Строка: 14"));
        assertTrue(logOutput.contains("Method: testMethod, Файл: src/test/resources/TestClass.java, Строка: 24"));
    }

    @Test
    public void testFindMethodWithReturn() {
        StringConstantQuery.findMethod("testMethodWithReturn", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c testMethodWithReturn: 1"));
        assertTrue(logOutput.contains("Method: testMethodWithReturn, Файл: src/test/resources/TestClass.java, Строка: 19"));
    }

    @Test
    public void testFindInterface() {
        StringConstantQuery.findInterface("TestInterface", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c TestInterface: 1"));
        assertTrue(logOutput.contains("Interface: TestInterface, Файл: src/test/resources/TestClass.java, Строка: 1"));
    }

    @Test
    public void testFindField() {
        StringConstantQuery.findField("testField", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c testField: 1"));
        assertTrue(logOutput.contains("Field: testField, Файл: src/test/resources/TestClass.java, Строка: 7"));
    }

    @Test
    public void testFindLocalVariable() {
        StringConstantQuery.findLocalVariable("localVariable", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c localVariable: 2"));
        assertTrue(logOutput.contains("LocalVariable: localVariable, Файл: src/test/resources/TestClass.java, Строка: 15"));
        assertTrue(logOutput.contains("LocalVariable: localVariable, Файл: src/test/resources/TestClass.java, Строка: 20"));
    }

    @Test
    public void testFindStringLiteral() {
        StringConstantQuery.findLiteral("Test String", "StringLiteral", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c Test String: 2"));
        assertTrue(logOutput.contains("StringLiteral: Test String, Файл: src/test/resources/TestClass.java, Строка: 7"));
        assertTrue(logOutput.contains("StringLiteral: Test String, Файл: src/test/resources/TestClass.java, Строка: 12"));
    }

    @Test
    public void testFindIntLiteral() {
        StringConstantQuery.findLiteral("12345", "IntegerLiteral", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c 12345: 2"));
        assertTrue(logOutput.contains("IntegerLiteral: 12345, Файл: src/test/resources/TestClass.java, Строка: 8"));
        assertTrue(logOutput.contains("IntegerLiteral: 12345, Файл: src/test/resources/TestClass.java, Строка: 26"));
    }

    @Test
    public void testFindFloatLiteral() {
        StringConstantQuery.findLiteral("123.45", "FloatLiteral", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c 123.45: 2"));
        assertTrue(logOutput.contains("FloatLiteral: 123.45, Файл: src/test/resources/TestClass.java, Строка: 9"));
        assertTrue(logOutput.contains("FloatLiteral: 123.45, Файл: src/test/resources/TestClass.java, Строка: 27"));
    }

    @Test
    public void testFindCharLiteral() {
        StringConstantQuery.findLiteral("A", "CharLiteral", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c A: 1"));
        assertTrue(logOutput.contains("CharLiteral: A, Файл: src/test/resources/TestClass.java, Строка: 10"));
    }

    @Test
    public void testFindBooleanLiteral() {
        StringConstantQuery.findLiteral("true", "BooleanLiteral", false);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c true: 1"));
        assertTrue(logOutput.contains("BooleanLiteral: true, Файл: src/test/resources/TestClass.java, Строка: 11"));
    }

    @Test
    public void testFuzzyFindStringConstants() {
        StringConstantQuery.findStringConstants("TesString", true);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений с TesString: 2"));
        assertTrue(logOutput.contains("StringConstant: Test String, Файл: src/test/resources/TestClass.java, Строка: 7"));
        assertTrue(logOutput.contains("StringConstant: Test String, Файл: src/test/resources/TestClass.java, Строка: 12"));
    }

    @Test
    public void testFuzzyFindClass() {
        StringConstantQuery.findClass("TstClas", true);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений с TstClas: 1"));
        assertTrue(logOutput.contains("Class: TestClass, Файл: src/test/resources/TestClass.java, Строка: 6"));
    }

    @Test
    public void testFuzzyFindMethod() {
        StringConstantQuery.findMethod("testMethood", true);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений с testMethood: 2"));
        assertTrue(logOutput.contains("Method: testMethod, Файл: src/test/resources/TestClass.java, Строка: 14"));
        assertTrue(logOutput.contains("Method: testMethod, Файл: src/test/resources/TestClass.java, Строка: 24"));
    }

    @Test
    public void testFuzzyFindField() {
        StringConstantQuery.findField("tesField", true);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений с tesField: 1"));
        assertTrue(logOutput.contains("Field: testField, Файл: src/test/resources/TestClass.java, Строка: 7"));
    }

    @Test
    public void testFuzzyFindLiteral() {
        StringConstantQuery.findLiteral("1234", "IntegerLiteral", true);
        String logOutput = StringConstantQuery.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений с 1234: 2"));
        assertTrue(logOutput.contains("IntegerLiteral: 12345, Файл: src/test/resources/TestClass.java, Строка: 8"));
        assertTrue(logOutput.contains("IntegerLiteral: 12345, Файл: src/test/resources/TestClass.java, Строка: 26"));
    }

    @Test
    public void testEmptySearchQuery() {
        StringConstantQuery.findStringConstants("", false);
        String logOutput = StringConstantQuery.logBuilder.toString();
        assertTrue(logOutput.contains("Найдено совпадений c : 0"));
    }

    @Test
    public void testNoMatchesFound() {
        StringConstantQuery.findStringConstants("AbraAbraCadabra", false);
        String logOutput = StringConstantQuery.logBuilder.toString();
        assertTrue(logOutput.contains("Найдено совпадений c AbraAbraCadabra: 0"));
    }

}
