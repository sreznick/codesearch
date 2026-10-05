package org.example;

import org.junit.jupiter.api.*;

import java.io.*;
import java.lang.reflect.*;

import static org.junit.jupiter.api.Assertions.*;

public class ConsoleAppTest {

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private ByteArrayInputStream inContent;

    @BeforeEach
    public void setUp() throws IllegalAccessException, NoSuchFieldException {
        System.setOut(new PrintStream(outContent));
        Field runningField = CodeSearch.class.getDeclaredField("running");
        runningField.setAccessible(true);
        runningField.set(null, true);
    }

    @AfterEach
    public void tearDown() {
        System.setOut(originalOut);
        outContent.reset();
    }

    @Test
    public void testHelpCommand() {
        inContent = new ByteArrayInputStream("help\nexit\n".getBytes());
        System.setIn(inContent);
        CodeSearch.main(new String[]{});

        String output = outContent.toString();
        String expectedOutput = String.join(System.lineSeparator(),
                "Доступные команды:",
                "  index <path>                     Запуск индексации вашего проекта по указанному пути.",
                "  find <type> <query> [-f, -cs]    Поиск объектов указанного типа (stringconstant, class, method, interface, field, localvariable,",
                "                                   [integer/float/boolean/char/string]literal) с запросом.",
                "                                   Флаг [-f] позволяет искать с неточностями. Флаг [-cs] учитывает регистр.",
                "  help                             Показать документацию.",
                "  exit                             Завершить работу приложения :-(",
                ""
        );
        assertTrue(output.contains(expectedOutput));
    }

    @Test
    public void testHandleIndexCommand() throws Exception {
        Method method = CodeSearch.class.getDeclaredMethod("handleIndexCommand", String[].class);
        method.setAccessible(true);

        method.invoke(null, (Object) new String[]{"index", "src"});
        String output = outContent.toString().trim();
        assertTrue(output.contains("Запуск индексации для: src"));
        outContent.reset();

        method.invoke(null, (Object) new String[]{"index"});
        output = outContent.toString().trim();
        assertTrue(output.contains("Ошибка: Укажите путь для индексации."));
    }

    @Test
    public void testIndexProjectSuccess() throws Exception {
        Method method = CodeSearch.class.getDeclaredMethod("indexProject", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(null, "src");
        assertTrue(result);
    }

    @Test
    public void testIndexProjectFailure() throws Exception {
        Method method = CodeSearch.class.getDeclaredMethod("indexProject", String.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(null, "/invalid/path");
        assertFalse(result);
    }

    @Test
    public void testHandleFindCommand() throws Exception {
        Method method = CodeSearch.class.getDeclaredMethod("handleFindCommand", String[].class);
        method.setAccessible(true);

        method.invoke(null, (Object) new String[]{"find"});
        String output = outContent.toString().trim();
        assertTrue(output.contains("Ошибка: Укажите тип и запрос."));
    }

    // Работает, но только отдельно от остальных
//    @Test
//    public void testHandleFindCommandSuccess() throws Exception {
//        Method method = CodeSearch.class.getDeclaredMethod("handleFindCommand", String[].class);
//        method.setAccessible(true);
//
//        method.invoke(null, (Object) new String[]{"find", "class", "TestClass"});
//        String output = outContent.toString().trim();
//        assertTrue(output.contains("Запрос на Class: TestClass (учет регистра: false)"));
//    }

    @Test
    public void testHandleFindCommandInvalidType() throws Exception {
        Method method = CodeSearch.class.getDeclaredMethod("handleFindCommand", String[].class);
        method.setAccessible(true);

        method.invoke(null, (Object) new String[]{"find", "unknowntype", "query"});
        String output = outContent.toString().trim();
        assertTrue(output.contains("Неизвестный тип для поиска: unknowntype"));
    }

    @Test
    public void testExitCommand() throws Exception {
        Field runningField = CodeSearch.class.getDeclaredField("running");
        runningField.setAccessible(true);

        assertTrue(runningField.getBoolean(null));

        Method method = CodeSearch.class.getDeclaredMethod("main", String[].class);
        method.setAccessible(true);

        InputStream originalIn = System.in;
        ByteArrayInputStream testInput = new ByteArrayInputStream("exit\n".getBytes());
        System.setIn(testInput);

        try {
            method.invoke(null, (Object) new String[]{});
        } catch (InvocationTargetException e) {
            if (!(e.getCause() instanceof NullPointerException)) {
                throw e;
            }
        } finally {
            System.setIn(originalIn);
        }

        assertFalse(runningField.getBoolean(null));

        String output = outContent.toString().trim();
        assertTrue(output.contains("Завершение работы приложения :-("));
    }
}
