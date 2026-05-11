package org.codesearch;

import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchQuery;
import org.codesearch.core.SearchResult;
import org.codesearch.core.SearchTarget;
import org.codesearch.java.JavaLanguageModule;
import org.codesearch.java.JavaSearchService;
import org.example.JavaSourceIndexer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.List;

public class App {
    private static final int DEFAULT_LIMIT = 100;

    public static void main(String[] args) {
        run(args, System.out, System.err);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || isHelp(args[0])) {
            printHelp(out);
            return 0;
        }

        String command = args[0].toLowerCase();
        return switch (command) {
            case "index" -> handleIndex(args, out, err);
            case "search" -> handleSearch(args, out, err);
            default -> {
                err.println("Неизвестная команда: " + args[0]);
                printHelp(out);
                yield 1;
            }
        };
    }

    private static int handleIndex(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 3) {
            err.println("Использование: index java <path>");
            return 1;
        }

        if (!isJava(args[1])) {
            err.println("Пока поддерживается только язык java.");
            return 1;
        }

        try {
            JavaSourceIndexer.indexJavaSources(args[2]);
            out.println("Индексация завершена: java -> " + args[2]);
            return 0;
        } catch (Exception e) {
            err.println("Ошибка индексации: " + e.getMessage());
            return 1;
        }
    }

    private static int handleSearch(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 4) {
            err.println("Использование: search java <kind> <query> [-f] [-cs]");
            return 1;
        }

        if (!isJava(args[1])) {
            err.println("Пока поддерживается только язык java.");
            return 1;
        }

        boolean fuzzy = false;
        boolean caseSensitive = false;
        for (int i = 4; i < args.length; i++) {
            if ("-f".equalsIgnoreCase(args[i])) {
                fuzzy = true;
            } else if ("-cs".equalsIgnoreCase(args[i])) {
                caseSensitive = true;
            } else {
                err.println("Неизвестный флаг: " + args[i]);
                return 1;
            }
        }

        try {
            SearchCommand searchCommand = parseSearchCommand(args[2], args[3], fuzzy, caseSensitive);
            JavaSearchService searchService = new JavaSearchService(Paths.get("index"));
            JavaSearchService.SearchResponse response = searchService.searchWithMetadata(searchCommand.query());
            printResults(out, response);
            return 0;
        } catch (IllegalArgumentException e) {
            err.println("Ошибка поиска: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("Ошибка поиска: " + e.getMessage());
            return 1;
        }
    }

    private static SearchCommand parseSearchCommand(String rawKind, String rawQuery, boolean fuzzy, boolean caseSensitive) {
        return switch (rawKind.toLowerCase()) {
            case "field-type" -> new SearchCommand(
                    new SearchQuery(rawQuery, EntityKind.FIELD, JavaLanguageModule.LANGUAGE, SearchTarget.DECLARED_TYPE, fuzzy, caseSensitive, DEFAULT_LIMIT)
            );
            case "local-variable-type" -> new SearchCommand(
                    new SearchQuery(rawQuery, EntityKind.LOCAL_VARIABLE, JavaLanguageModule.LANGUAGE, SearchTarget.DECLARED_TYPE, fuzzy, caseSensitive, DEFAULT_LIMIT)
            );
            default -> new SearchCommand(
                    new SearchQuery(rawQuery, EntityKind.fromValue(rawKind), JavaLanguageModule.LANGUAGE, SearchTarget.CONTENT, fuzzy, caseSensitive, DEFAULT_LIMIT)
            );
        };
    }

    private static void printResults(PrintStream out, JavaSearchService.SearchResponse response) {
        out.println("Найдено совпадений: " + response.totalHits());
        for (SearchResult result : response.results()) {
            out.println(formatResult(result.entity()));
        }
    }

    private static String formatResult(CodeEntity entity) {
        if (entity.kind() == EntityKind.FIELD || entity.kind() == EntityKind.LOCAL_VARIABLE) {
            return String.format(
                    "%s: %s, Тип: %s, Файл: %s, Строка: %d",
                    entity.kind().legacyJavaType(),
                    entity.content(),
                    entity.declaredType(),
                    entity.location().filePath(),
                    entity.location().line()
            );
        }

        return String.format(
                "%s: %s, Файл: %s, Строка: %d",
                entity.kind().legacyJavaType(),
                entity.content(),
                entity.location().filePath(),
                entity.location().line()
        );
    }

    private static boolean isHelp(String value) {
        return "help".equalsIgnoreCase(value) || "-h".equalsIgnoreCase(value) || "--help".equalsIgnoreCase(value);
    }

    private static boolean isJava(String value) {
        return JavaLanguageModule.LANGUAGE.equalsIgnoreCase(value);
    }

    private static void printHelp(PrintStream out) {
        out.println("Команды:");
        out.println("  index java <path>");
        out.println("  search java <kind> <query> [-f] [-cs]");
        out.println("Примеры:");
        out.println("  index java src");
        out.println("  search java class TestClass");
        out.println("  search java method testMethod -f");
        out.println("  search java field-type String");
        out.println("  search java local-variable-type String");
    }

    private record SearchCommand(SearchQuery query) {}
}
