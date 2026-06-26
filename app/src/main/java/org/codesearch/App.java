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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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

        if (!isJava(command.language())) {
            err.println(colorize(RED, "Пока поддерживается только язык java."));
            return 1;
        }

        try {
            JavaSourceIndexer.indexJavaSources(command.sourcePath(), indexPath);
            out.println(colorize(GREEN, "Готово") + "  Индексация завершена");
            out.println(colorize(DIM, "Язык: ") + "java");
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
        if (args.length == 1) {
            return new IndexCommand(JavaLanguageModule.LANGUAGE, ".");
        }

        if (args.length == 2) {
            if (isJava(args[1])) {
                return new IndexCommand(args[1].trim().toLowerCase(), ".");
            }
            return new IndexCommand(JavaLanguageModule.LANGUAGE, args[1]);
        }

        if (args.length == 3) {
            return new IndexCommand(args[1].trim().toLowerCase(), args[2]);
        }

        throw new IllegalArgumentException("слишком много аргументов.");
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

        if (!isJava(command.language())) {
            err.println(colorize(RED, "Пока поддерживается только язык java."));
            return 1;
        }

        Path quickIndexPath = null;
        try {
            quickIndexPath = Files.createTempDirectory("codesearch-quick-index");
            JavaSourceIndexer.indexJavaSources(command.sourcePath(), quickIndexPath);
            JavaSearchService searchService = new JavaSearchService(quickIndexPath);
            JavaSearchService.SearchResponse response = executeSearch(searchService, command);
            printResults(out, response, command.explain(), command.target() == SearchTarget.ASSIGNABLE_TYPE ? command.query() : null);
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

        if (!isJava(command.language())) {
            err.println(colorize(RED, "Пока поддерживается только язык java."));
            return 1;
        }

        try {
            JavaSearchService searchService = new JavaSearchService(indexPath);
            JavaSearchService.SearchResponse response = executeSearch(searchService, command);
            printResults(out, response, command.explain(), command.target() == SearchTarget.ASSIGNABLE_TYPE ? command.query() : null);
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
        List<String> operands = new ArrayList<>();

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
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
                language = args[++i].trim().toLowerCase();
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

        if (!operands.isEmpty() && isJava(operands.getFirst())) {
            language = operands.removeFirst().trim().toLowerCase();
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

        return new QuickSearchCommand(query, sourcePath, language, kind, target, caseSensitive, limit, explain);
    }

    private static CachedSearchCommand parseCachedSearchCommand(String[] args, int startIndex) {
        String language = JavaLanguageModule.LANGUAGE;
        EntityKind kind = null;
        SearchTarget target = SearchTarget.CONTENT;
        boolean caseSensitive = false;
        int limit = DEFAULT_LIMIT;
        String pathFilter = null;
        boolean explain = false;
        List<String> operands = new ArrayList<>();

        for (int i = startIndex; i < args.length; i++) {
            String arg = args[i];
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
                language = args[++i].trim().toLowerCase();
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

        if (!operands.isEmpty() && isJava(operands.getFirst())) {
            language = operands.removeFirst().trim().toLowerCase();
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

        return new CachedSearchCommand(query, language, kind, target, caseSensitive, limit, pathFilter, explain);
    }

    private static SearchKind parseSearchKindOrNull(String value) {
        return switch (value.toLowerCase()) {
            case "field-type" -> new SearchKind(EntityKind.FIELD, SearchTarget.DECLARED_TYPE);
            case "local-variable-type" -> new SearchKind(EntityKind.LOCAL_VARIABLE, SearchTarget.DECLARED_TYPE);
            case "method-return-type" -> new SearchKind(EntityKind.METHOD, SearchTarget.DECLARED_TYPE);
            case "variable-assignable-to", "assignable-type" -> new SearchKind(null, SearchTarget.ASSIGNABLE_TYPE);
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
        boolean explain = false;
        for (int i = 4; i < args.length; i++) {
            if ("-f".equalsIgnoreCase(args[i])) {
                fuzzy = true;
            } else if ("-cs".equalsIgnoreCase(args[i])) {
                caseSensitive = true;
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
            JavaSearchService searchService = new JavaSearchService(indexPath);
            JavaSearchService.SearchResponse response;
            if (isAssignableTypeSearch(args[2])) {
                response = searchService.searchAssignableVariables(
                        args[3],
                        JavaLanguageModule.LANGUAGE,
                        caseSensitive,
                        limit,
                        pathFilter
                );
            } else {
                SearchCommand searchCommand = parseSearchCommand(args[2], args[3], fuzzy, caseSensitive, limit, pathFilter);
                response = searchService.searchWithMetadata(searchCommand.query());
            }
            printResults(out, response, explain, isAssignableTypeSearch(args[2]) ? args[3] : null);
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

    private static SearchCommand parseSearchCommand(String rawKind, String rawQuery, boolean fuzzy, boolean caseSensitive, int limit, String pathFilter) {
        return switch (rawKind.toLowerCase()) {
            case "field-type" -> new SearchCommand(
                    metadataQuery(rawQuery, EntityKind.FIELD, fuzzy, caseSensitive, limit, pathFilter)
            );
            case "local-variable-type" -> new SearchCommand(
                    metadataQuery(rawQuery, EntityKind.LOCAL_VARIABLE, fuzzy, caseSensitive, limit, pathFilter)
            );
            case "method-return-type" -> new SearchCommand(
                    metadataQuery(rawQuery, EntityKind.METHOD, fuzzy, caseSensitive, limit, pathFilter)
            );
            default -> new SearchCommand(
                    SearchQuery.builder(rawQuery, EntityKind.fromValue(rawKind), JavaLanguageModule.LANGUAGE)
                            .fuzzy(fuzzy)
                            .caseSensitive(caseSensitive)
                            .limit(limit)
                            .pathFilter(pathFilter)
                            .build()
            );
        };
    }

    private static SearchQuery metadataQuery(String rawQuery, EntityKind kind, boolean fuzzy, boolean caseSensitive, int limit, String pathFilter) {
        return SearchQuery.builder(rawQuery, kind, JavaLanguageModule.LANGUAGE)
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

    private static void printResults(PrintStream out, JavaSearchService.SearchResponse response, boolean explain, String assignableTargetType) {
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
            index++;
        }
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

        if (entity.declaredType() != null && !entity.declaredType().isBlank()) {
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
        out.println("  codesearch [options] <query> [path]");
        out.println("  codesearch [options] <kind> <query> [path]");
        out.println("  codesearch index [path]");
        out.println("  codesearch --cached [options] <query> [path-filter]");
        out.println("  codesearch --cached [options] <kind> <query> [path-filter]");
        out.println();
        out.println(colorize(BLUE, "Виды поиска"));
        out.println("  class|method|field|interface|local-variable <name>");
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
        out.println("  --lang java            язык; сейчас поддерживается только java");
        out.println();
        out.println(colorize(BLUE, "Примеры"));
        out.println("  codesearch TestClass");
        out.println("  codesearch class TestClass");
        out.println("  codesearch testField src --kind field");
        out.println("  codesearch index .");
        out.println("  codesearch --cached class TestClass");
        out.println("  codesearch --cached field-type String");
        out.println("  codesearch --cached variable-assignable-to Appendable");
        out.println("  codesearch --cached variable-assignable-to Printable --explain");
        out.println("  codesearch --cached variable-assignable-to Animal --explain");
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
    }

    private record QuickSearchCommand(
            String query,
            String sourcePath,
            String language,
            EntityKind kind,
            SearchTarget target,
            boolean caseSensitive,
            int limit,
            boolean explain
    ) implements SearchCommandSpec {
        @Override
        public String pathFilter() {
            return null;
        }
    }

    private record CachedSearchCommand(String query, String language, EntityKind kind, SearchTarget target, boolean caseSensitive, int limit, String pathFilter, boolean explain) implements SearchCommandSpec {}

    private record IndexCommand(String language, String sourcePath) {}

    private record SearchKind(EntityKind kind, SearchTarget target) {}

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
}
