package org.codesearch;

import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.LanguageModule;
import org.codesearch.core.SearchQuery;
import org.codesearch.core.SearchResult;
import org.codesearch.core.SearchTarget;
import org.codesearch.go.GoLanguageModule;
import org.codesearch.go.GoSourceIndexer;
import org.codesearch.java.JavaLanguageModule;
import org.codesearch.java.JavaEntityAttributes;
import org.codesearch.java.JavaSearchService;
import org.codesearch.java.JavaSourceIndexer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class App {
    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_SNIPPET_CONTEXT = 2;
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
            case "stats" -> handleStats(args, out, err, indexPath);
            case "search" -> handleSearch(args, out, err, indexPath);
            case "grep" -> handleQuickSearch(args, out, err, indexPath, 1);
            case "--cached", "cached" -> handleCachedSearch(args, out, err, indexPath, 1);
            default -> {
                yield handleQuickSearch(args, out, err, indexPath, 0);
            }
        };
    }

    private static int handleIndex(String[] args, PrintStream out, PrintStream err, Path indexPath) {
        IndexCommand command;
        try {
            command = parseIndexCommand(args);
        } catch (IllegalArgumentException e) {
            err.println(colorize(RED, "Ошибка индексации: ") + e.getMessage());
            err.println(colorize(DIM, "Использование: ") + "codesearch index [path]");
            return 1;
        }

        try {
            LanguageModule module = languageModule(command.language());
            if (isJava(module.language())) {
                JavaSourceIndexer.indexJavaSources(command.sourcePath(), indexPath);
            } else if (isGo(module.language())) {
                GoSourceIndexer.indexGoSources(command.sourcePath(), indexPath);
            }
            out.println(colorize(GREEN, "Готово") + "  Индексация завершена");
            out.println(colorize(DIM, "Язык: ") + module.language());
            out.println(colorize(DIM, "Путь:  ") + command.sourcePath());
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

    private static IndexCommand parseIndexCommand(String[] args) {
        String language = JavaLanguageModule.LANGUAGE;
        String sourcePath = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--lang".equalsIgnoreCase(arg) || "-l".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Не указан язык.");
                }
                language = normalizeLanguage(args[++i]);
                continue;
            }
            if (isSupportedLanguage(arg)) {
                language = normalizeLanguage(arg);
                continue;
            }
            if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Неизвестный флаг: " + arg);
            }
            if (sourcePath != null) {
                throw new IllegalArgumentException("слишком много аргументов.");
            }
            sourcePath = arg;
        }

        return new IndexCommand(language, sourcePath == null ? "." : sourcePath);
    }

    private static int handleStats(String[] args, PrintStream out, PrintStream err, Path indexPath) {
        StatsCommand command;
        try {
            command = parseStatsCommand(args);
        } catch (IllegalArgumentException e) {
            err.println(colorize(RED, "Ошибка статистики: ") + e.getMessage());
            err.println(colorize(DIM, "Использование: ") + "codesearch stats [--path PATH]");
            return 1;
        }

        try {
            LanguageModule module = languageModule(command.language());
            JavaSearchService searchService = new JavaSearchService(indexPath, module);
            JavaSearchService.IndexStats stats = searchService.stats(command.pathFilter());
            printStats(out, stats, command.pathFilter(), module.language());
            return 0;
        } catch (JavaSearchService.IndexUnavailableException e) {
            err.println(colorize(RED, "Индекс не готов: ") + e.getMessage());
            err.println(colorize(DIM, "Сначала выполните: ") + "codesearch index [path]");
            return 1;
        } catch (IOException e) {
            err.println(colorize(RED, "Ошибка чтения индекса: ") + e.getMessage());
            return 1;
        }
    }

    private static StatsCommand parseStatsCommand(String[] args) {
        String language = JavaLanguageModule.LANGUAGE;
        String pathFilter = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--lang".equalsIgnoreCase(arg) || "-l".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Не указан язык.");
                }
                language = normalizeLanguage(args[++i]);
                continue;
            }
            if ("--path".equalsIgnoreCase(arg) || "-p".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Не указан фильтр пути.");
                }
                pathFilter = args[++i];
                continue;
            }
            if (isSupportedLanguage(arg)) {
                language = normalizeLanguage(arg);
                continue;
            }
            if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Неизвестный флаг: " + arg);
            }
            throw new IllegalArgumentException("Неизвестный аргумент: " + arg);
        }

        return new StatsCommand(language, pathFilter);
    }

    private static int handleQuickSearch(String[] args, PrintStream out, PrintStream err, Path indexPath, int startIndex) {
        QuickSearchCommand command;
        try {
            command = parseQuickSearchCommand(args, startIndex);
        } catch (IllegalArgumentException e) {
            err.println(colorize(RED, "Ошибка поиска: ") + e.getMessage());
            printQuickSearchUsage(err);
            return 1;
        }

        Path quickIndexPath = null;
        try {
            LanguageModule module = languageModule(command.language());
            quickIndexPath = Files.createTempDirectory("codesearch-quick-index");
            if (isJava(module.language())) {
                JavaSourceIndexer.indexJavaSources(command.sourcePath(), quickIndexPath);
            } else if (isGo(module.language())) {
                GoSourceIndexer.indexGoSources(command.sourcePath(), quickIndexPath);
            }
            JavaSearchService searchService = new JavaSearchService(quickIndexPath, module);
            JavaSearchService.SearchResponse response = executeSearch(searchService, command);
            printResults(out, response, command.explain(), command.json(), command.snippet(), command.target() == SearchTarget.ASSIGNABLE_TYPE ? command.query() : null);
            return 0;
        } catch (IllegalArgumentException e) {
            err.println(colorize(RED, "Ошибка поиска: ") + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println(colorize(RED, "Ошибка поиска: ") + "индексация была прервана.");
            return 1;
        } catch (IOException e) {
            err.println(colorize(RED, "Ошибка чтения или записи индекса: ") + e.getMessage());
            return 1;
        } finally {
            deleteDirectoryQuietly(quickIndexPath);
        }
    }

    private static int handleCachedSearch(String[] args, PrintStream out, PrintStream err, Path indexPath, int startIndex) {
        CachedSearchCommand command;
        try {
            command = parseCachedSearchCommand(args, startIndex);
        } catch (IllegalArgumentException e) {
            err.println(colorize(RED, "Ошибка поиска: ") + e.getMessage());
            printCachedSearchUsage(err);
            return 1;
        }

        try {
            LanguageModule module = languageModule(command.language());
            JavaSearchService searchService = new JavaSearchService(indexPath, module);
            JavaSearchService.SearchResponse response = executeSearch(searchService, command);
            printResults(out, response, command.explain(), command.json(), command.snippet(), command.target() == SearchTarget.ASSIGNABLE_TYPE ? command.query() : null);
            return 0;
        } catch (IllegalArgumentException e) {
            err.println(colorize(RED, "Некорректный запрос: ") + e.getMessage());
            return 1;
        } catch (JavaSearchService.IndexUnavailableException e) {
            err.println(colorize(RED, "Индекс не готов: ") + e.getMessage());
            err.println(colorize(DIM, "Сначала выполните: ") + "codesearch index [path]");
            return 1;
        } catch (IOException e) {
            err.println(colorize(RED, "Ошибка чтения индекса: ") + e.getMessage());
            return 1;
        }
    }

    private static JavaSearchService.SearchResponse executeSearch(JavaSearchService searchService, SearchCommandSpec command) throws IOException {
        if (command.target() == SearchTarget.ASSIGNABLE_TYPE) {
            return searchService.searchAssignableVariables(
                    command.query(),
                    command.language(),
                    command.caseSensitive(),
                    command.limit(),
                    command.pathFilter()
            );
        }
        if (command.target() == SearchTarget.DECLARED_TYPE) {
            return searchService.searchWithMetadata(
                    SearchQuery.builder(command.query(), command.kind(), command.language())
                            .target(SearchTarget.DECLARED_TYPE)
                            .caseSensitive(command.caseSensitive())
                            .limit(command.limit())
                            .pathFilter(command.pathFilter())
                            .build()
            );
        }

        return searchService.searchContaining(
                command.query(),
                command.kind(),
                command.language(),
                command.caseSensitive(),
                command.limit(),
                command.pathFilter()
        );
    }

    private static QuickSearchCommand parseQuickSearchCommand(String[] args, int startIndex) {
        String language = JavaLanguageModule.LANGUAGE;
        EntityKind kind = null;
        SearchTarget target = SearchTarget.CONTENT;
        boolean caseSensitive = false;
        int limit = DEFAULT_LIMIT;
        boolean explain = false;
        boolean json = false;
        SnippetBuilder snippet = new SnippetBuilder();
        List<String> operands = new ArrayList<>();

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
            if ("--snippet".equalsIgnoreCase(arg)) {
                snippet.enableDefaultContext();
                continue;
            }
            if ("-A".equals(arg) || "--after".equalsIgnoreCase(arg)) {
                snippet.after(parseRequiredSnippetLineCount(args, ++i, arg));
                continue;
            }
            if ("-B".equals(arg) || "--before".equalsIgnoreCase(arg)) {
                snippet.before(parseRequiredSnippetLineCount(args, ++i, arg));
                continue;
            }
            if ("-C".equals(arg) || "--context".equalsIgnoreCase(arg)) {
                snippet.context(parseRequiredSnippetLineCount(args, ++i, arg));
                continue;
            }
            if ("--json".equalsIgnoreCase(arg)) {
                json = true;
                continue;
            }
            if ("--explain".equalsIgnoreCase(arg)) {
                explain = true;
                continue;
            }
            if ("-r".equalsIgnoreCase(arg) || "--recursive".equalsIgnoreCase(arg)) {
                continue;
            }
            if ("-cs".equalsIgnoreCase(arg) || "--case-sensitive".equalsIgnoreCase(arg)) {
                caseSensitive = true;
                continue;
            }
            if ("--lang".equalsIgnoreCase(arg) || "-l".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Не указан язык.");
                }
                language = normalizeLanguage(args[++i]);
                continue;
            }
            if ("--kind".equalsIgnoreCase(arg) || "-k".equalsIgnoreCase(arg) || "--type".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Не указан тип сущности.");
                }
                kind = EntityKind.fromValue(args[++i]);
                continue;
            }
            if ("--limit".equalsIgnoreCase(arg) || "-n".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Не указан лимит результатов.");
                }
                try {
                    limit = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Лимит должен быть числом: " + args[i], e);
                }
                if (limit < 1) {
                    throw new IllegalArgumentException("Лимит должен быть положительным числом.");
                }
                continue;
            }
            if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Неизвестный флаг: " + arg);
            }
            operands.add(arg);
        }

        if (!operands.isEmpty() && isSupportedLanguage(operands.getFirst())) {
            language = normalizeLanguage(operands.removeFirst());
        }

        String query;
        String sourcePath = ".";
        if (operands.size() == 1) {
            query = operands.getFirst();
        } else if (operands.size() == 2) {
            SearchKind searchKind = parseSearchKindOrNull(operands.getFirst());
            if (kind == null && searchKind != null) {
                kind = searchKind.kind();
                target = searchKind.target();
                query = operands.get(1);
            } else {
                query = operands.getFirst();
                sourcePath = operands.get(1);
            }
        } else if (operands.size() == 3) {
            SearchKind searchKind = parseSearchKindOrNull(operands.getFirst());
            if (kind == null && searchKind != null) {
                kind = searchKind.kind();
                target = searchKind.target();
                query = operands.get(1);
                sourcePath = operands.get(2);
            } else {
                throw new IllegalArgumentException("Нужно указать запрос и путь.");
            }
        } else {
            throw new IllegalArgumentException("Нужно указать запрос.");
        }

        return new QuickSearchCommand(query, sourcePath, language, kind, target, caseSensitive, limit, explain, json, snippet.build());
    }

    private static CachedSearchCommand parseCachedSearchCommand(String[] args, int startIndex) {
        String language = JavaLanguageModule.LANGUAGE;
        EntityKind kind = null;
        SearchTarget target = SearchTarget.CONTENT;
        boolean caseSensitive = false;
        int limit = DEFAULT_LIMIT;
        String pathFilter = null;
        boolean explain = false;
        boolean json = false;
        SnippetBuilder snippet = new SnippetBuilder();
        List<String> operands = new ArrayList<>();

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
            if ("--snippet".equalsIgnoreCase(arg)) {
                snippet.enableDefaultContext();
                continue;
            }
            if ("-A".equals(arg) || "--after".equalsIgnoreCase(arg)) {
                snippet.after(parseRequiredSnippetLineCount(args, ++i, arg));
                continue;
            }
            if ("-B".equals(arg) || "--before".equalsIgnoreCase(arg)) {
                snippet.before(parseRequiredSnippetLineCount(args, ++i, arg));
                continue;
            }
            if ("-C".equals(arg) || "--context".equalsIgnoreCase(arg)) {
                snippet.context(parseRequiredSnippetLineCount(args, ++i, arg));
                continue;
            }
            if ("--json".equalsIgnoreCase(arg)) {
                json = true;
                continue;
            }
            if ("--explain".equalsIgnoreCase(arg)) {
                explain = true;
                continue;
            }
            if ("-cs".equalsIgnoreCase(arg) || "--case-sensitive".equalsIgnoreCase(arg)) {
                caseSensitive = true;
                continue;
            }
            if ("--lang".equalsIgnoreCase(arg) || "-l".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Не указан язык.");
                }
                language = normalizeLanguage(args[++i]);
                continue;
            }
            if ("--kind".equalsIgnoreCase(arg) || "-k".equalsIgnoreCase(arg) || "--type".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Не указан тип сущности.");
                }
                kind = EntityKind.fromValue(args[++i]);
                continue;
            }
            if ("--limit".equalsIgnoreCase(arg) || "-n".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Не указан лимит результатов.");
                }
                try {
                    limit = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Лимит должен быть числом: " + args[i], e);
                }
                if (limit < 1) {
                    throw new IllegalArgumentException("Лимит должен быть положительным числом.");
                }
                continue;
            }
            if ("--path".equalsIgnoreCase(arg) || "-p".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Не указан фильтр пути.");
                }
                pathFilter = args[++i];
                continue;
            }
            if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Неизвестный флаг: " + arg);
            }
            operands.add(arg);
        }

        if (!operands.isEmpty() && isSupportedLanguage(operands.getFirst())) {
            language = normalizeLanguage(operands.removeFirst());
        }

        String query;
        if (operands.size() == 1) {
            query = operands.getFirst();
        } else if (operands.size() == 2) {
            SearchKind searchKind = parseSearchKindOrNull(operands.getFirst());
            if (kind == null && searchKind != null) {
                kind = searchKind.kind();
                target = searchKind.target();
                query = operands.get(1);
            } else {
                query = operands.getFirst();
                pathFilter = operands.get(1);
            }
        } else if (operands.size() == 3) {
            SearchKind searchKind = parseSearchKindOrNull(operands.getFirst());
            if (kind == null && searchKind != null) {
                kind = searchKind.kind();
                target = searchKind.target();
                query = operands.get(1);
                pathFilter = operands.get(2);
            } else {
                throw new IllegalArgumentException("Нужно указать запрос и необязательный фильтр пути.");
            }
        } else {
            throw new IllegalArgumentException("Нужно указать запрос.");
        }

        return new CachedSearchCommand(query, language, kind, target, caseSensitive, limit, pathFilter, explain, json, snippet.build());
    }

    private static int parseRequiredSnippetLineCount(String[] args, int valueIndex, String flag) {
        if (valueIndex >= args.length) {
            throw new IllegalArgumentException("Не указано количество строк для " + flag + ".");
        }
        try {
            int value = Integer.parseInt(args[valueIndex]);
            if (value < 0) {
                throw new IllegalArgumentException("Количество строк для " + flag + " не может быть отрицательным.");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Количество строк для " + flag + " должно быть числом: " + args[valueIndex], e);
        }
    }

    private static SearchKind parseSearchKindOrNull(String value) {
        return switch (value.toLowerCase()) {
            case "field-type" -> new SearchKind(EntityKind.FIELD, SearchTarget.DECLARED_TYPE);
            case "local-variable-type" -> new SearchKind(EntityKind.LOCAL_VARIABLE, SearchTarget.DECLARED_TYPE);
            case "method-return-type" -> new SearchKind(EntityKind.METHOD, SearchTarget.DECLARED_TYPE);
            case "variable-assignable-to", "assignable-type" -> new SearchKind(null, SearchTarget.ASSIGNABLE_TYPE);
            case "var" -> new SearchKind(EntityKind.VARIABLE, SearchTarget.CONTENT);
            case "constant" -> new SearchKind(EntityKind.CONSTANT, SearchTarget.CONTENT);
            default -> {
                EntityKind kind = parseEntityKindOrNull(value);
                yield kind == null ? null : new SearchKind(kind, SearchTarget.CONTENT);
            }
        };
    }

    private static EntityKind parseEntityKindOrNull(String value) {
        try {
            return EntityKind.fromValue(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int handleSearch(String[] args, PrintStream out, PrintStream err, Path indexPath) {
        if (args.length < 4) {
            err.println(colorize(RED, "Использование: ") + "search <java|go> <kind> <query> [-f] [-cs] [--limit N] [--path PATH] [--snippet] [--json]");
            return 1;
        }

        if (!isSupportedLanguage(args[1])) {
            err.println(colorize(RED, "Неподдерживаемый язык: ") + args[1]);
            return 1;
        }
        String language = normalizeLanguage(args[1]);

        boolean fuzzy = false;
        boolean caseSensitive = false;
        int limit = DEFAULT_LIMIT;
        String pathFilter = null;
        boolean explain = false;
        boolean json = false;
        SnippetBuilder snippet = new SnippetBuilder();
        for (int i = 4; i < args.length; i++) {
            if ("-f".equalsIgnoreCase(args[i])) {
                fuzzy = true;
            } else if ("-cs".equalsIgnoreCase(args[i])) {
                caseSensitive = true;
            } else if ("--snippet".equalsIgnoreCase(args[i])) {
                snippet.enableDefaultContext();
            } else if ("-A".equals(args[i]) || "--after".equalsIgnoreCase(args[i])) {
                try {
                    snippet.after(parseRequiredSnippetLineCount(args, ++i, args[i - 1]));
                } catch (IllegalArgumentException e) {
                    err.println(colorize(RED, "Ошибка контекста: ") + e.getMessage());
                    return 1;
                }
            } else if ("-B".equals(args[i]) || "--before".equalsIgnoreCase(args[i])) {
                try {
                    snippet.before(parseRequiredSnippetLineCount(args, ++i, args[i - 1]));
                } catch (IllegalArgumentException e) {
                    err.println(colorize(RED, "Ошибка контекста: ") + e.getMessage());
                    return 1;
                }
            } else if ("-C".equals(args[i]) || "--context".equalsIgnoreCase(args[i])) {
                try {
                    snippet.context(parseRequiredSnippetLineCount(args, ++i, args[i - 1]));
                } catch (IllegalArgumentException e) {
                    err.println(colorize(RED, "Ошибка контекста: ") + e.getMessage());
                    return 1;
                }
            } else if ("--json".equalsIgnoreCase(args[i])) {
                json = true;
            } else if ("--explain".equalsIgnoreCase(args[i])) {
                explain = true;
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
            LanguageModule module = languageModule(language);
            JavaSearchService searchService = new JavaSearchService(indexPath, module);
            JavaSearchService.SearchResponse response;
            if (isAssignableTypeSearch(args[2])) {
                response = searchService.searchAssignableVariables(
                        args[3],
                        language,
                        caseSensitive,
                        limit,
                        pathFilter
                );
            } else {
                SearchCommand searchCommand = parseSearchCommand(args[2], args[3], language, fuzzy, caseSensitive, limit, pathFilter);
                response = searchService.searchWithMetadata(searchCommand.query());
            }
            printResults(out, response, explain, json, snippet.build(), isAssignableTypeSearch(args[2]) ? args[3] : null);
            return 0;
        } catch (IllegalArgumentException e) {
            err.println(colorize(RED, "Некорректный запрос: ") + e.getMessage());
            return 1;
        } catch (JavaSearchService.IndexUnavailableException e) {
            err.println(colorize(RED, "Индекс не готов: ") + e.getMessage());
            err.println(colorize(DIM, "Сначала выполните: ") + "index java <path>");
            return 1;
        } catch (IOException e) {
            err.println(colorize(RED, "Ошибка чтения индекса: ") + e.getMessage());
            return 1;
        }
    }

    private static SearchCommand parseSearchCommand(String rawKind, String rawQuery, String language, boolean fuzzy, boolean caseSensitive, int limit, String pathFilter) {
        return switch (rawKind.toLowerCase()) {
            case "field-type" -> new SearchCommand(
                    metadataQuery(rawQuery, EntityKind.FIELD, language, fuzzy, caseSensitive, limit, pathFilter)
            );
            case "local-variable-type" -> new SearchCommand(
                    metadataQuery(rawQuery, EntityKind.LOCAL_VARIABLE, language, fuzzy, caseSensitive, limit, pathFilter)
            );
            case "method-return-type" -> new SearchCommand(
                    metadataQuery(rawQuery, EntityKind.METHOD, language, fuzzy, caseSensitive, limit, pathFilter)
            );
            default -> new SearchCommand(
                    SearchQuery.builder(rawQuery, EntityKind.fromValue(rawKind), language)
                            .fuzzy(fuzzy)
                            .caseSensitive(caseSensitive)
                            .limit(limit)
                            .pathFilter(pathFilter)
                            .build()
            );
        };
    }

    private static SearchQuery metadataQuery(String rawQuery, EntityKind kind, String language, boolean fuzzy, boolean caseSensitive, int limit, String pathFilter) {
        return SearchQuery.builder(rawQuery, kind, language)
                .target(SearchTarget.DECLARED_TYPE)
                .fuzzy(fuzzy)
                .caseSensitive(caseSensitive)
                .limit(limit)
                .pathFilter(pathFilter)
                .build();
    }

    private static boolean isAssignableTypeSearch(String rawKind) {
        return "variable-assignable-to".equalsIgnoreCase(rawKind)
                || "assignable-type".equalsIgnoreCase(rawKind);
    }

    private static void printStats(PrintStream out, JavaSearchService.IndexStats stats, String pathFilter, String language) {
        out.println(colorize(BLUE, "Статистика индекса"));
        out.println(colorize(DIM, "Язык: ") + language);
        if (pathFilter != null && !pathFilter.isBlank()) {
            out.println(colorize(DIM, "Фильтр пути: ") + pathFilter);
        }
        out.println("Файлов: " + stats.totalFiles());
        out.println("Сущностей: " + stats.totalEntities());
        out.println();
        out.println(colorize(BLUE, "По видам сущностей"));
        for (EntityKind kind : EntityKind.values()) {
            long count = stats.entitiesByKind().getOrDefault(kind, 0L);
            if (count > 0) {
                out.printf("  %-16s %d%n", kind.legacyJavaType() + ":", count);
            }
        }
    }

    private static void printResults(PrintStream out, JavaSearchService.SearchResponse response, boolean explain, boolean json, SnippetOptions snippet, String assignableTargetType) {
        if (json) {
            printJsonResults(out, response, explain, snippet, assignableTargetType);
            return;
        }

        out.println(colorize(BLUE, "Найдено совпадений: ") + response.totalHits());

        if (response.results().isEmpty()) {
            out.println(colorize(YELLOW, "Совпадений нет."));
            return;
        }

        int index = 1;
        for (SearchResult result : response.results()) {
            out.println(formatResult(index, result.entity()));
            if (explain) {
                explainResult(result.entity(), assignableTargetType).forEach(line ->
                        out.println(colorize(DIM, "   explain: ") + line)
                );
            }
            printSnippet(out, result.entity(), snippet);
            index++;
        }
    }

    private static void printJsonResults(PrintStream out, JavaSearchService.SearchResponse response, boolean explain, SnippetOptions snippet, String assignableTargetType) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"totalHits\": ").append(response.totalHits()).append(",\n");
        json.append("  \"results\": [\n");

        for (int i = 0; i < response.results().size(); i++) {
            SearchResult result = response.results().get(i);
            appendJsonResult(json, result, explain, snippet, assignableTargetType);
            if (i + 1 < response.results().size()) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}");
        out.println(json);
    }

    private static void appendJsonResult(StringBuilder json, SearchResult result, boolean explain, SnippetOptions snippet, String assignableTargetType) {
        CodeEntity entity = result.entity();
        json.append("    {\n");
        json.append("      \"kind\": ").append(jsonString(entity.kind().legacyJavaType())).append(",\n");
        json.append("      \"name\": ").append(jsonString(entity.content())).append(",\n");
        json.append("      \"language\": ").append(jsonString(entity.language())).append(",\n");
        json.append("      \"file\": ").append(jsonString(entity.location().filePath())).append(",\n");
        json.append("      \"line\": ").append(entity.location().line()).append(",\n");
        json.append("      \"declaredType\": ").append(jsonStringOrNull(entity.declaredType())).append(",\n");
        json.append("      \"score\": ").append(result.score()).append(",\n");
        appendJsonAttributes(json, entity.attributes());
        json.append(",\n");
        appendJsonExplanation(json, explain ? explainResult(entity, assignableTargetType) : List.of());
        if (snippet.enabled()) {
            json.append(",\n");
            appendJsonSnippet(json, entity, snippet);
        }
        json.append("\n");
        json.append("    }");
    }

    private static void appendJsonAttributes(StringBuilder json, Map<String, String> attributes) {
        json.append("      \"attributes\": {");
        if (!attributes.isEmpty()) {
            json.append("\n");
            List<Map.Entry<String, String>> entries = attributes.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList();
            for (int i = 0; i < entries.size(); i++) {
                Map.Entry<String, String> entry = entries.get(i);
                json.append("        ")
                        .append(jsonString(entry.getKey()))
                        .append(": ")
                        .append(jsonString(entry.getValue()));
                if (i + 1 < entries.size()) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("      }");
            return;
        }
        json.append("}");
    }

    private static void appendJsonExplanation(StringBuilder json, List<String> explanation) {
        json.append("      \"explanation\": [");
        if (!explanation.isEmpty()) {
            json.append("\n");
            for (int i = 0; i < explanation.size(); i++) {
                json.append("        ").append(jsonString(explanation.get(i)));
                if (i + 1 < explanation.size()) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("      ]");
            return;
        }
        json.append("]");
    }

    private static void appendJsonSnippet(StringBuilder json, CodeEntity entity, SnippetOptions snippet) {
        json.append("      \"snippet\": [");
        List<SnippetLine> lines;
        try {
            lines = readSnippetLines(entity, snippet);
        } catch (IOException e) {
            lines = List.of();
        }

        if (!lines.isEmpty()) {
            json.append("\n");
            for (int i = 0; i < lines.size(); i++) {
                SnippetLine line = lines.get(i);
                json.append("        {")
                        .append("\"line\": ").append(line.number()).append(", ")
                        .append("\"match\": ").append(line.match()).append(", ")
                        .append("\"text\": ").append(jsonString(line.text()))
                        .append("}");
                if (i + 1 < lines.size()) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("      ]");
            return;
        }
        json.append("]");
    }

    private static void printSnippet(PrintStream out, CodeEntity entity, SnippetOptions snippet) {
        if (!snippet.enabled()) {
            return;
        }

        try {
            for (SnippetLine line : readSnippetLines(entity, snippet)) {
                String marker = line.match() ? ">" : " ";
                out.printf("   %s %4d | %s%n", marker, line.number(), line.text());
            }
        } catch (IOException e) {
            out.println(colorize(DIM, "   snippet: ") + "не удалось прочитать " + entity.location().filePath());
        }
    }

    private static List<SnippetLine> readSnippetLines(CodeEntity entity, SnippetOptions snippet) throws IOException {
        if (!snippet.enabled()) {
            return List.of();
        }

        Path filePath = Paths.get(entity.location().filePath());
        List<String> fileLines = Files.readAllLines(filePath);
        int matchLine = entity.location().line();
        if (matchLine < 1 || matchLine > fileLines.size()) {
            return List.of();
        }

        int start = Math.max(1, matchLine - snippet.before());
        int end = Math.min(fileLines.size(), matchLine + snippet.after());
        List<SnippetLine> snippetLines = new ArrayList<>();
        for (int lineNumber = start; lineNumber <= end; lineNumber++) {
            snippetLines.add(new SnippetLine(
                    lineNumber,
                    fileLines.get(lineNumber - 1),
                    lineNumber == matchLine
            ));
        }
        return snippetLines;
    }

    private static List<String> explainResult(CodeEntity entity, String assignableTargetType) {
        List<String> lines = new ArrayList<>();
        if (assignableTargetType != null) {
            String chain = assignableChain(entity, assignableTargetType);
            if (chain != null) {
                lines.add(chain);
            }
        }

        String assignableTypes = entity.attributes().get("assignableTypes");
        if (assignableTypes != null && !assignableTypes.isBlank()) {
            lines.add("совместимые типы: " + assignableTypes.replace(",", ", "));
        }

        if (lines.isEmpty() && entity.declaredType() != null && !entity.declaredType().isBlank()) {
            lines.add("объявленный тип: " + entity.declaredType());
        }
        return lines;
    }

    private static String assignableChain(CodeEntity entity, String targetType) {
        String declaredType = entity.declaredType();
        if (declaredType == null || declaredType.isBlank()) {
            return null;
        }

        String typeInference = entity.attributes().get("typeInference");
        if (typeInference != null && !typeInference.isBlank()) {
            return typeInference + " -> " + targetType;
        }
        if (declaredType.equals(targetType)) {
            return declaredType;
        }
        return declaredType + " -> " + targetType;
    }

    private static String formatResult(int index, CodeEntity entity) {
        String prefix = colorize(BLUE, index + ".");
        String kind = colorize(GREEN, entity.kind().legacyJavaType());
        String file = colorize(DIM, entity.location().filePath() + ":" + entity.location().line());
        String container = formatContainer(entity);

        if (entity.kind() == EntityKind.ANNOTATION) {
            return formatAnnotationResult(prefix, kind, entity, file);
        }

        if (entity.declaredType() != null && !entity.declaredType().isBlank()) {
            return String.format(
                    "%s %s %s  [%s]%s  %s",
                    prefix,
                    kind,
                    entity.content(),
                    entity.declaredType(),
                    container,
                    file
            );
        }

        return String.format(
                "%s %s %s%s  %s",
                prefix,
                kind,
                entity.content(),
                container,
                file
        );
    }

    private static String formatContainer(CodeEntity entity) {
        String containerKind = entity.attributes().get(JavaEntityAttributes.CONTAINER_KIND);
        String containerName = entity.attributes().get(JavaEntityAttributes.CONTAINER_NAME);
        if (containerKind == null || containerKind.isBlank() || containerName == null || containerName.isBlank()) {
            return "";
        }
        return " in " + containerKind + " " + containerName;
    }

    private static String formatAnnotationResult(String prefix, String kind, CodeEntity entity, String file) {
        String targetKind = entity.attributes().get(JavaEntityAttributes.ANNOTATION_TARGET_KIND);
        String targetName = entity.attributes().get(JavaEntityAttributes.ANNOTATION_TARGET_NAME);
        if (targetKind != null && targetName != null) {
            return String.format(
                    "%s %s %s on %s %s  %s",
                    prefix,
                    kind,
                    entity.content(),
                    targetKind,
                    targetName,
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

    private static boolean isGo(String value) {
        return GoLanguageModule.LANGUAGE.equalsIgnoreCase(value);
    }

    private static boolean isSupportedLanguage(String value) {
        return isJava(value) || isGo(value);
    }

    private static String normalizeLanguage(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Не указан язык.");
        }
        String language = value.trim().toLowerCase();
        if (!isSupportedLanguage(language)) {
            throw new IllegalArgumentException("Неподдерживаемый язык: " + value);
        }
        return language;
    }

    private static LanguageModule languageModule(String language) {
        String normalized = normalizeLanguage(language);
        if (isJava(normalized)) {
            return new JavaLanguageModule();
        }
        return new GoLanguageModule();
    }

    private static void printHelp(PrintStream out) {
        out.println(colorize(BLUE, "Команды"));
        out.println("  codesearch [options] <query> [path]");
        out.println("  codesearch [options] <kind> <query> [path]");
        out.println("  codesearch index [--lang java|go] [path]");
        out.println("  codesearch stats [--lang java|go] [--path PATH]");
        out.println("  codesearch --cached [options] <query> [path-filter]");
        out.println("  codesearch --cached [options] <kind> <query> [path-filter]");
        out.println();
        out.println(colorize(BLUE, "Виды поиска"));
        out.println("  Java: class|method|field|interface|local-variable <name>");
        out.println("  Go: package|import|function|method|struct|interface|field|var|const <name>");
        out.println("  annotation <name>                 Java-аннотации и место применения");
        out.println("  field-type <type>                 поля с точным declared type");
        out.println("  local-variable-type <type>        локальные переменные с точным declared type");
        out.println("  method-return-type <type>         методы с указанным return type");
        out.println("  variable-assignable-to <type>     переменные, совместимые с типом");
        out.println();
        out.println(colorize(BLUE, "Опции"));
        out.println("  -k, --kind KIND        искать только сущности указанного вида");
        out.println("  -n, --limit N          показать не больше N результатов");
        out.println("  -cs, --case-sensitive  учитывать регистр");
        out.println("  --cached               искать по постоянному индексу без переиндексации");
        out.println("  -p, --path PATH        фильтр пути для --cached");
        out.println("  --explain              показать, почему результат подошел под запрос");
        out.println("  --json                 вывести результат в JSON для скриптов и интеграций");
        out.println("  --snippet              показать фрагмент кода вокруг результата");
        out.println("  -A, --after N          показать N строк после результата");
        out.println("  -B, --before N         показать N строк до результата");
        out.println("  -C, --context N        показать N строк до и после результата");
        out.println("  --lang java|go         язык исходного кода");
        out.println();
        out.println(colorize(BLUE, "Примеры"));
        out.println("  codesearch TestClass");
        out.println("  codesearch class TestClass");
        out.println("  codesearch annotation DemoController");
        out.println("  codesearch testField src --kind field");
        out.println("  codesearch index .");
        out.println("  codesearch index --lang go app/src/test/resources/go");
        out.println("  codesearch stats");
        out.println("  codesearch stats --lang go");
        out.println("  codesearch --cached class TestClass");
        out.println("  codesearch --cached field-type String");
        out.println("  codesearch --cached variable-assignable-to Appendable");
        out.println("  codesearch --cached variable-assignable-to Printable --explain");
        out.println("  codesearch --cached variable-assignable-to Animal --explain");
        out.println("  codesearch --cached annotation DemoController --json");
        out.println("  codesearch method getTestField --snippet");
        out.println("  codesearch --lang go function intMin app/src/test/resources/go");
        out.println("  codesearch --cached --lang go struct BitSet");
        out.println();
        out.println(colorize(BLUE, "Семантический поиск по типам"));
        out.println("  variable-assignable-to учитывает простое выведение var, JDK-типы,");
        out.println("  а также implements/extends внутри индексируемого Java-проекта.");
        out.println("  --explain показывает цепочку, например: var -> Dog -> Animal");
    }

    private static void printQuickSearchUsage(PrintStream err) {
        err.println(colorize(DIM, "Использование: ") + "codesearch [options] <query> [path]");
    }

    private static void printCachedSearchUsage(PrintStream err) {
        err.println(colorize(DIM, "Использование: ") + "codesearch --cached [options] <query> [path-filter]");
    }

    private static void deleteDirectoryQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(path)) {
            for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
            // Temporary quick-search indexes are best-effort cleanup only.
        }
    }

    private record SearchCommand(SearchQuery query) {}

    private interface SearchCommandSpec {
        String query();
        String language();
        EntityKind kind();
        SearchTarget target();
        boolean caseSensitive();
        int limit();
        String pathFilter();
        boolean explain();
        boolean json();
        SnippetOptions snippet();
    }

    private record QuickSearchCommand(
            String query,
            String sourcePath,
            String language,
            EntityKind kind,
            SearchTarget target,
            boolean caseSensitive,
            int limit,
            boolean explain,
            boolean json,
            SnippetOptions snippet
    ) implements SearchCommandSpec {
        @Override
        public String pathFilter() {
            return null;
        }
    }

    private record CachedSearchCommand(String query, String language, EntityKind kind, SearchTarget target, boolean caseSensitive, int limit, String pathFilter, boolean explain, boolean json, SnippetOptions snippet) implements SearchCommandSpec {}

    private record IndexCommand(String language, String sourcePath) {}

    private record StatsCommand(String language, String pathFilter) {}

    private record SearchKind(EntityKind kind, SearchTarget target) {}

    private record SnippetOptions(boolean enabled, int before, int after) {}

    private record SnippetLine(int number, String text, boolean match) {}

    private static class SnippetBuilder {
        private boolean enabled;
        private int before;
        private int after;

        void enableDefaultContext() {
            enabled = true;
            if (before == 0 && after == 0) {
                before = DEFAULT_SNIPPET_CONTEXT;
                after = DEFAULT_SNIPPET_CONTEXT;
            }
        }

        void before(int lines) {
            enabled = true;
            before = lines;
        }

        void after(int lines) {
            enabled = true;
            after = lines;
        }

        void context(int lines) {
            enabled = true;
            before = lines;
            after = lines;
        }

        SnippetOptions build() {
            return new SnippetOptions(enabled, before, after);
        }
    }

    private static String colorize(String color, String text) {
        return USE_COLOR ? color + text + RESET : text;
    }

    private static boolean shouldUseColor() {
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null) {
            return false;
        }
        if (System.console() == null) {
            return false;
        }

        String term = System.getenv("TERM");
        return term != null && !"dumb".equalsIgnoreCase(term);
    }

    private static String jsonStringOrNull(String value) {
        if (value == null) {
            return "null";
        }
        return jsonString(value);
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        escaped.append("\"");
        return escaped.toString();
    }
}
