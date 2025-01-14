package org.example;

import java.util.Scanner;

import static org.example.JavaSourceIndexer.indexJavaSources;

public class CodeSearch {

    private static boolean running = true;

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

        String path = parts[1];

        System.out.println("Запуск индексации для: " + path);
        if (indexProject(path)) {
            System.out.println("Индексация завершена успешно для: " + path);
        } else {
            System.out.println("Ошибка в процессе индексирования: " + path);
        }
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

        for (int i = 3; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("-f")) {
                isFuzzy = true;
            } else if (parts[i].equalsIgnoreCase("-cs")) {
                isCaseSensitive = true;
            }
        }
        try {
            findWithQuery(type, query, isFuzzy, isCaseSensitive);
        } catch (Exception e) {
            System.err.println("Ошибка поиска: " + e.getMessage());
        }
    }


    private static void showHelp() {
        System.out.println("Доступные команды:");
        System.out.println("  index <path>                     Запуск индексации вашего проекта по указанному пути.");
        System.out.println("  find <type> <query> [-f, -cs]    Поиск объектов указанного типа (stringconstant, class, method, interface, field, localvariable, \n" +
                "                                   [integer/float/boolean/char/string]literal) с запросом. \n" +
                "                                   Флаг [-f] позволяет искать с неточностями. Флаг [-cs] учитывает регистр.");
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


    private static void findWithQuery(String type, String query, boolean isFuzzy, boolean isCaseSensitive) {
        switch (type.toLowerCase()) {
            case "stringconstant":
                QueryExecutor.findStringConstants(query, isFuzzy, isCaseSensitive);
                break;
            case "class":
                QueryExecutor.findClass(query, isFuzzy, isCaseSensitive);
                break;
            case "method":
                QueryExecutor.findMethod(query, isFuzzy, isCaseSensitive);
                break;
            case "interface":
                QueryExecutor.findInterface(query, isFuzzy, isCaseSensitive);
                break;
            case "field":
                QueryExecutor.findField(query, isFuzzy, isCaseSensitive);
                break;
            case "localvariable":
                QueryExecutor.findLocalVariable(query, isFuzzy, isCaseSensitive);
                break;
            case "integerliteral":
                QueryExecutor.findLiteral(query, "IntegerLiteral", isFuzzy, isCaseSensitive);
                break;
            case "floatliteral":
                QueryExecutor.findLiteral(query, "FloatLiteral", isFuzzy, isCaseSensitive);
                break;
            case "booleanliteral":
                QueryExecutor.findLiteral(query, "BooleanLiteral", isFuzzy, isCaseSensitive);
                break;
            case "charliteral":
                QueryExecutor.findLiteral(query, "CharLiteral", isFuzzy, isCaseSensitive);
                break;
            case "stringliteral":
                QueryExecutor.findLiteral(query, "StringLiteral", isFuzzy, isCaseSensitive);
                break;
            default:
                System.out.println("Неизвестный тип для поиска: " + type);
                break;
        }
    }
}
