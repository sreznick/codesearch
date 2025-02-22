package org.example;

import java.util.Scanner;

import static org.example.JavaSourceIndexer.indexJavaSources;

/**
 * Основной класс консольного приложения CodeSearch.
 * Приложение предназначено для обработки текстовых команд пользователя.
 * Считывает команды из консоли, выполняет их обработку и выводит результаты.
 */
public class CodeSearch {

    private static boolean running = true;
    
    private static String indexedProjectPaths;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Добро пожаловать в CodeSearch! Введите 'help' чтобы увидеть документацию:)");

        while (running) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            String[] parts = input.split("\\s+");
            String command = parts[0];

            try {
                switch (command.toLowerCase()) {
                    case "index":
                        handleIndexCommand(parts);
                        break;
                    case "find":
                        handleFindCommand(parts);
                        break;
                    case "help":
                        showHelp();
                        break;
                    case "exit":
                        running = false;
                        System.out.println("Завершение работы приложения :-(");
                        break;
                    default:
                        System.out.println("Неизвестная команда. Введите 'help' чтобы увидеть документацию:)");
                }
            } catch (Exception e) {
                System.err.println("Ошибка выполнения команды: " + e.getMessage());
                e.printStackTrace();
            }
        }

        scanner.close();
    }

    private static void handleIndexCommand(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Ошибка: Укажите путь для индексации. Пример: index <path>");
            return;
        }

        indexedProjectPaths = parts[1];

        System.out.println("Запуск индексации для: " + indexedProjectPaths);
        if (indexProject(indexedProjectPaths)) {
            System.out.println("Индексация завершена успешно для: " + indexedProjectPaths);
        } else {
            System.out.println("Ошибка в процессе индексирования: " + indexedProjectPaths);
        }
    }
    public static boolean isNumeric(String str) {
        return str.matches("-?\\d+(\\.\\d+)?");
    }

    private static void handleFindCommand(String[] parts) {
        if (parts.length < 3) {
            System.out.println("Ошибка: Укажите тип и запрос. Пример: find <type> <query> [-f, -cs]");
            return;
        }

        String type = parts[1];
        String query = parts[2];
        boolean isFuzzy = false;
        boolean isCaseSensitive = false;
        int maxMatches = 200;
        String directoryToSearch = "";

        for (int i = 3; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("-f")) {
                isFuzzy = true;
            } else if (parts[i].equalsIgnoreCase("-cs")) {
                isCaseSensitive = true;
            } else if (parts[i].equalsIgnoreCase("-m")) {
                String val = parts[i + 1];
                if (isNumeric(val)) {
                    maxMatches = Integer.parseInt(val);
                } else {
                    System.out.println("Ошибка: за флагом -m должно быть число.");
                    return;
                }
            } else if (parts[i].equalsIgnoreCase("-r")) {
                String directory = parts[i + 1];
                if (indexedProjectPaths.contains(directory)) {
                    directoryToSearch = directory;
                } else {
                    System.out.println("Ошибка: за флагом -m должна быть директория из индексируемого проекта.");
                    return;
                }
            }
        }
        try {
            findWithQuery(type, query, isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
        } catch (Exception e) {
            System.err.println("Ошибка поиска: " + e.getMessage());
        }
    }

    private static void showHelp() {
        System.out.println("Доступные команды:");
        System.out.println("  index <path>                     Запуск индексации вашего проекта по указанному пути.");
        System.out.println("  find <type> <query> [-f, -cs]    Поиск объектов указанного типа (stringconstant, class, method, interface, field, localvariable,\n" +
                "                                   [integer/float/boolean/char/string]literal) с запросом.\n" +
                "                                   Флаг [-f] позволяет искать с неточностями. Флаг [-cs] учитывает регистр.\n" +
                "                                   Флаг [-cs] учитывает регистр.\n" +
                "                                   Флаг [-m] устанавливает максимум совпадений на вывод.\n" +
                "                                   Флаг [-r] устанавтливает директорию в которой искать.");
        System.out.println("  help                             Показать документацию.");
        System.out.println("  exit                             Завершить работу приложения :-(");
    }

    private static boolean indexProject(String path) {
        try {
            indexJavaSources(path);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    private static void findWithQuery(String type, String query, boolean isFuzzy, boolean isCaseSensitive, int maxMatches, String directoryToSearch) {
        switch (type.toLowerCase()) {
            case "stringconstant":
                QueryExecutor.findStringConstants(query, isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
                break;
            case "class":
                QueryExecutor.findClass(query, isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
                break;
            case "method":
                QueryExecutor.findMethod(query, isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
                break;
            case "interface":
                QueryExecutor.findInterface(query, isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
                break;
            case "field":
                QueryExecutor.findField(query, isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
                break;
            case "localvariable":
                QueryExecutor.findLocalVariable(query, isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
                break;
            case "integerliteral":
                QueryExecutor.findLiteral(query, "IntegerLiteral", isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
                break;
            case "floatliteral":
                QueryExecutor.findLiteral(query, "FloatLiteral", isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
                break;
            case "booleanliteral":
                QueryExecutor.findLiteral(query, "BooleanLiteral", isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
                break;
            case "charliteral":
                QueryExecutor.findLiteral(query, "CharLiteral", isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
                break;
            case "stringliteral":
                QueryExecutor.findLiteral(query, "StringLiteral", isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
                break;
            default:
                System.out.println("Неизвестный тип для поиска: " + type);
                break;
        }
    }
}