package org.codesearch;

import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchQuery;
import org.codesearch.core.SearchResult;
import org.codesearch.core.SearchTarget;
import org.codesearch.java.JavaLanguageModule;
import org.codesearch.java.JavaSearchService;
import org.codesearch.java.JavaSourceIndexer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class App {
    private static final int DEFAULT_LIMIT = 100;
    private static final Path DEFAULT_INDEX_PATH = Paths.get("index");
    private static final boolean USE_COLOR = shouldUseColor();
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String DIM = "\u001B[2m";

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, DEFAULT_INDEX_PATH);
    }

    static int run(String[] args, PrintStream out, PrintStream err, Path indexPath) {
        if (args.length == 0 || isHelp(args[0])) {
            printHelp(out);
            return 0;
        }

        String command = args[0].toLowerCase();
        return switch (command) {
            case "index" -> handleIndex(args, out, err, indexPath);
            case "search" -> handleSearch(args, out, err, indexPath);
            default -> {
                err.println(colorize(RED, "Неизвестная команда: ") + args[0]);
                printHelp(out);
                yield 1;
            }
        };
    }

    private static int handleIndex(String[] args, PrintStream out, PrintStream err, Path indexPath) {
        if (args.length < 3) {
            err.println(colorize(RED, "Использование: ") + "index java <path>");
            return 1;
        }

        if (!isJava(args[1])) {
            err.println(colorize(RED, "Пока поддерживается только язык java."));
            return 1;
        }

        try {
            JavaSourceIndexer.indexJavaSources(args[2], indexPath);
            out.println(colorize(GREEN, "Готово") + "  Индексация завершена");
            out.println(colorize(DIM, "Язык: ") + "java");
            out.println(colorize(DIM, "Путь:  ") + args[2]);
            return 0;
        } catch (IllegalArgumentException e) {
            err.println(colorize(RED, "Ошибка индексации: ") + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println(colorize(RED, "Ошибка индексации: ") + "индексация была прервана.");
            return 1;
        } catch (IOException e) {
            err.println(colorize(RED, "Ошибка индексации: ") + e.getMessage());
            return 1;
        }
    }

    private static int handleSearch(String[] args, PrintStream out, PrintStream err, Path indexPath) {
        if (args.length < 4) {
            err.println(colorize(RED, "Использование: ") + "search java <kind> <query> [-f] [-cs] [--limit N] [--path PATH]");
            return 1;
        }

        if (!isJava(args[1])) {
            err.println(colorize(RED, "Пока поддерживается только язык java."));
            return 1;
        }

        boolean fuzzy = false;
        boolean caseSensitive = false;
        int limit = DEFAULT_LIMIT;
        String pathFilter = null;
        for (int i = 4; i < args.length; i++) {
            if ("-f".equalsIgnoreCase(args[i])) {
                fuzzy = true;
            } else if ("-cs".equalsIgnoreCase(args[i])) {
                caseSensitive = true;
            } else if ("--limit".equalsIgnoreCase(args[i]) || "-n".equalsIgnoreCase(args[i])) {
                if (i + 1 >= args.length) {
                    err.println(colorize(RED, "Не указан лимит результатов."));
                    return 1;
                }
                try {
                    limit = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    err.println(colorize(RED, "Лимит должен быть числом: ") + args[i]);
                    return 1;
                }
                if (limit < 1) {
                    err.println(colorize(RED, "Лимит должен быть положительным числом."));
                    return 1;
                }
            } else if ("--path".equalsIgnoreCase(args[i]) || "-p".equalsIgnoreCase(args[i])) {
                if (i + 1 >= args.length) {
                    err.println(colorize(RED, "Не указан фильтр пути."));
                    return 1;
                }
                pathFilter = args[++i];
            } else {
                err.println(colorize(RED, "Неизвестный флаг: ") + args[i]);
                return 1;
            }
        }

        try {
            SearchCommand searchCommand = parseSearchCommand(args[2], args[3], fuzzy, caseSensitive, limit, pathFilter);
            JavaSearchService searchService = new JavaSearchService(indexPath);
            JavaSearchService.SearchResponse response = searchService.searchWithMetadata(searchCommand.query());
            printResults(out, response);
            return 0;
        } catch (IllegalArgumentException e) {
            err.println(colorize(RED, "Ошибка поиска: ") + e.getMessage());
            return 1;
        } catch (JavaSearchService.IndexUnavailableException e) {
            err.println(colorize(RED, "Индекс не готов: ") + e.getMessage());
            err.println(colorize(DIM, "Сначала выполните: ") + "index java <path>");
            return 1;
        } catch (IOException e) {
            err.println(colorize(RED, "Ошибка поиска: ") + e.getMessage());
            return 1;
        }
    }

    private static SearchCommand parseSearchCommand(String rawKind, String rawQuery, boolean fuzzy, boolean caseSensitive, int limit, String pathFilter) {
        return switch (rawKind.toLowerCase()) {
            case "field-type" -> new SearchCommand(
                    new SearchQuery(rawQuery, EntityKind.FIELD, JavaLanguageModule.LANGUAGE, SearchTarget.DECLARED_TYPE, fuzzy, caseSensitive, limit, pathFilter)
            );
            case "local-variable-type" -> new SearchCommand(
                    new SearchQuery(rawQuery, EntityKind.LOCAL_VARIABLE, JavaLanguageModule.LANGUAGE, SearchTarget.DECLARED_TYPE, fuzzy, caseSensitive, limit, pathFilter)
            );
            default -> new SearchCommand(
                    new SearchQuery(rawQuery, EntityKind.fromValue(rawKind), JavaLanguageModule.LANGUAGE, SearchTarget.CONTENT, fuzzy, caseSensitive, limit, pathFilter)
            );
        };
    }

    private static void printResults(PrintStream out, JavaSearchService.SearchResponse response) {
        out.println(colorize(BLUE, "Найдено совпадений: ") + response.totalHits());

        if (response.results().isEmpty()) {
            out.println(colorize(YELLOW, "Совпадений нет."));
            return;
        }

        int index = 1;
        for (SearchResult result : response.results()) {
            out.println(formatResult(index, result.entity()));
            index++;
        }
    }

    private static String formatResult(int index, CodeEntity entity) {
        String prefix = colorize(BLUE, index + ".");
        String kind = colorize(GREEN, entity.kind().legacyJavaType());
        String file = colorize(DIM, entity.location().filePath() + ":" + entity.location().line());

        if (entity.kind() == EntityKind.FIELD || entity.kind() == EntityKind.LOCAL_VARIABLE) {
            return String.format(
                    "%s %s %s  [%s]  %s",
                    prefix,
                    kind,
                    entity.content(),
                    entity.declaredType(),
                    file
            );
        }

        return String.format(
                "%s %s %s  %s",
                prefix,
                kind,
                entity.content(),
                file
        );
    }

    private static boolean isHelp(String value) {
        return "help".equalsIgnoreCase(value) || "-h".equalsIgnoreCase(value) || "--help".equalsIgnoreCase(value);
    }

    private static boolean isJava(String value) {
        return JavaLanguageModule.LANGUAGE.equalsIgnoreCase(value);
    }

    private static void printHelp(PrintStream out) {
        out.println(colorize(BLUE, "Команды"));
        out.println("  index java <path>");
        out.println("  search java <kind> <query> [-f] [-cs] [--limit N] [--path PATH]");
        out.println();
        out.println(colorize(BLUE, "Примеры"));
        out.println("  index java src");
        out.println("  search java class TestClass");
        out.println("  search java method testMethod -f");
        out.println("  search java field-type String");
        out.println("  search java local-variable-type String --limit 1");
        out.println("  search java field-type String --path src/test/resources");
    }

    private record SearchCommand(SearchQuery query) {}

    private static String colorize(String color, String text) {
        return USE_COLOR ? color + text + RESET : text;
    }

    private static boolean shouldUseColor() {
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null) {
            return false;
        }

        String term = System.getenv("TERM");
        return term != null && !"dumb".equalsIgnoreCase(term);
    }
}
