package org.example;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.MMapDirectory;
import org.junit.jupiter.api.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class AppTest {

    private static MMapDirectory directory;
    private static IndexWriter writer;

    @BeforeAll
    static void setUpIndex() throws IOException, InterruptedException {
        JavaSourceIndexer.indexJavaSources("src/test/resources");
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (directory != null) {
            directory.close();
        }
    }

    @Test
    public void testFindStringConstants() {
        QueryExecutor.findStringConstants("test String", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c Test String: 2"));
        assertTrue(logOutput.contains("StringConstant: Test String, Файл: src/test/resources/TestClass.java, Строка: 7"));
        assertTrue(logOutput.contains("StringConstant: Test String, Файл: src/test/resources/TestClass.java, Строка: 12"));
    }

    @Test
    public void testFindClass() {
        QueryExecutor.findClass("TestClass", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c TestClass: 1"));
        assertTrue(logOutput.contains("Class: TestClass, Файл: src/test/resources/TestClass.java, Строка: 6"));
    }

    @Test
    public void testFindMethod() {
        QueryExecutor.findMethod("testMethod", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c testMethod: 2"));
        assertTrue(logOutput.contains("Method: testMethod, Файл: src/test/resources/TestClass.java, Строка: 14"));
        assertTrue(logOutput.contains("Method: testMethod, Файл: src/test/resources/TestClass.java, Строка: 24"));
    }

    @Test
    public void testFindMethodWithReturn() {
        QueryExecutor.findMethod("testMethodWithReturn", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c testMethodWithReturn: 1"));
        assertTrue(logOutput.contains("Method: testMethodWithReturn, Файл: src/test/resources/TestClass.java, Строка: 19"));
    }

    @Test
    public void testFindInterface() {
        QueryExecutor.findInterface("TestInterface", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c TestInterface: 1"));
        assertTrue(logOutput.contains("Interface: TestInterface, Файл: src/test/resources/TestClass.java, Строка: 1"));
    }

    @Test
    public void testFindField() {
        QueryExecutor.findField("testField", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c testField: 1"));
        assertTrue(logOutput.contains("Field: testField, Тип: String, Файл: src/test/resources/TestClass.java, Строка: 7"));
    }

    @Test
    public void testFindLocalVariable() {
        QueryExecutor.findLocalVariable("localVariable", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c localVariable: 2"));
        assertTrue(logOutput.contains("LocalVariable: localVariable, Тип: String, Файл: src/test/resources/TestClass.java, Строка: 15"));
        assertTrue(logOutput.contains("LocalVariable: localVariable, Тип: String, Файл: src/test/resources/TestClass.java, Строка: 20"));
    }

    @Test
    public void testFindStringLiteral() {
        QueryExecutor.findLiteral("Test String", "StringLiteral", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c Test String: 2"));
        assertTrue(logOutput.contains("StringLiteral: Test String, Файл: src/test/resources/TestClass.java, Строка: 7"));
        assertTrue(logOutput.contains("StringLiteral: Test String, Файл: src/test/resources/TestClass.java, Строка: 12"));
    }

    @Test
    public void testFindIntLiteral() {
        QueryExecutor.findLiteral("12345", "IntegerLiteral", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c 12345: 2"));
        assertTrue(logOutput.contains("IntegerLiteral: 12345, Файл: src/test/resources/TestClass.java, Строка: 8"));
        assertTrue(logOutput.contains("IntegerLiteral: 12345, Файл: src/test/resources/TestClass.java, Строка: 26"));
    }

    @Test
    public void testFindFloatLiteral() {
        QueryExecutor.findLiteral("123.45", "FloatLiteral", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c 123.45: 2"));
        assertTrue(logOutput.contains("FloatLiteral: 123.45, Файл: src/test/resources/TestClass.java, Строка: 9"));
        assertTrue(logOutput.contains("FloatLiteral: 123.45, Файл: src/test/resources/TestClass.java, Строка: 27"));
    }

    @Test
    public void testFindCharLiteral() {
        QueryExecutor.findLiteral("A", "CharLiteral", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c A: 1"));
        assertTrue(logOutput.contains("CharLiteral: A, Файл: src/test/resources/TestClass.java, Строка: 10"));
    }

    @Test
    public void testFindBooleanLiteral() {
        QueryExecutor.findLiteral("true", "BooleanLiteral", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c true: 1"));
        assertTrue(logOutput.contains("BooleanLiteral: true, Файл: src/test/resources/TestClass.java, Строка: 11"));
    }

    @Test
    public void testFuzzyFindStringConstants() {
        QueryExecutor.findStringConstants("TesString", true, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений с TesString: 2"));
        assertTrue(logOutput.contains("StringConstant: Test String, Файл: src/test/resources/TestClass.java, Строка: 7"));
        assertTrue(logOutput.contains("StringConstant: Test String, Файл: src/test/resources/TestClass.java, Строка: 12"));
    }

    @Test
    public void testFuzzyFindClass() {
        QueryExecutor.findClass("TstClas", true, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений с TstClas: 1"));
        assertTrue(logOutput.contains("Class: TestClass, Файл: src/test/resources/TestClass.java, Строка: 6"));
    }

    @Test
    public void testFuzzyFindMethod() {
        QueryExecutor.findMethod("testMethood", true, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений с testMethood: 2"));
        assertTrue(logOutput.contains("Method: testMethod, Файл: src/test/resources/TestClass.java, Строка: 14"));
        assertTrue(logOutput.contains("Method: testMethod, Файл: src/test/resources/TestClass.java, Строка: 24"));
    }

    @Test
    public void testFuzzyFindField() {
        QueryExecutor.findField("tesField", true, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений с tesField: 1"));
        assertTrue(logOutput.contains("Field: testField, Тип: String, Файл: src/test/resources/TestClass.java, Строка: 7"));
    }

    @Test
    public void testFuzzyFindLiteral() {
        QueryExecutor.findLiteral("1234", "IntegerLiteral", true, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений с 1234: 2"));
        assertTrue(logOutput.contains("IntegerLiteral: 12345, Файл: src/test/resources/TestClass.java, Строка: 8"));
        assertTrue(logOutput.contains("IntegerLiteral: 12345, Файл: src/test/resources/TestClass.java, Строка: 26"));
    }

    @Test
    public void testEmptySearchQuery() {
        QueryExecutor.findStringConstants("", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();
        assertTrue(logOutput.contains("Найдено совпадений c : 0"));
    }

    @Test
    public void testNoMatchesFound() {
        QueryExecutor.findStringConstants("AbraAbraCadabra", false, false, 200);
        String logOutput = QueryExecutor.logBuilder.toString();
        assertTrue(logOutput.contains("Найдено совпадений c AbraAbraCadabra: 0"));
    }

    @Test
    public void testCaseSensitiveSearchMatch() {
        QueryExecutor.findStringConstants("Test String", false, true, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c Test String: 2"));
        assertTrue(logOutput.contains("StringConstant: Test String, Файл: src/test/resources/TestClass.java, Строка: 7"));
        assertTrue(logOutput.contains("StringConstant: Test String, Файл: src/test/resources/TestClass.java, Строка: 12"));
    }

    @Test
    public void testCaseSensitiveSearchNoMatch() {
        QueryExecutor.findStringConstants("test string", false, true, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c test string: 0"));
    }

    @Test
    public void testClassCaseSensitivity() {
        QueryExecutor.findClass("testclass", false, true, 200);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c testclass: 0"));

        QueryExecutor.findClass("TestClass", false, true, 200);
        logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c TestClass: 1"));
        assertTrue(logOutput.contains("Class: TestClass, Файл: src/test/resources/TestClass.java, Строка: 6"));
    }

    @Test
    public void testMaxMatchFlag() {
        QueryExecutor.findStringConstants("max_out", false, true, 2);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c max_out: 2"));
        assertTrue(logOutput.contains("StringConstant: max_out, Файл: src/test/resources/TestClass.java, Строка: 34"));
        assertTrue(logOutput.contains("StringConstant: max_out, Файл: src/test/resources/TestClass.java, Строка: 36"));
    }

    @Test
    public void testMaxMatchFlagLessThenFoundMatches() {
        QueryExecutor.findStringConstants("max_out", false, true, 1);
        String logOutput = QueryExecutor.logBuilder.toString();

        assertTrue(logOutput.contains("Найдено совпадений c max_out: 2"));
        int outCount = 0;
        if (logOutput.contains("StringConstant: max_out, Файл: src/test/resources/TestClass.java, Строка: 34")) {
            outCount++;
        }
        if (logOutput.contains("StringConstant: max_out, Файл: src/test/resources/TestClass.java, Строка: 36")) {
            outCount++;
        }
        assertEquals(1, outCount);
    }

}
