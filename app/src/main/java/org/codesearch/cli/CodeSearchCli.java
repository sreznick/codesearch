package org.codesearch.cli;

import org.codesearch.cli.CommandLine.IndexCommand;
import org.codesearch.cli.CommandLine.SearchCommand;
import org.codesearch.cli.CommandLine.StatsCommand;
import org.codesearch.core.EntityKind;
import org.codesearch.core.IndexStats;
import org.codesearch.core.IndexUnavailableException;
import org.codesearch.core.MatchMode;
import org.codesearch.core.SearchResponse;
import org.codesearch.engine.CodeSearchEngine;
import org.codesearch.engine.LanguageRegistry;
import org.codesearch.index.IndexReport;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.codesearch.cli.Ansi.BLUE;
import static org.codesearch.cli.Ansi.DIM;
import static org.codesearch.cli.Ansi.GREEN;
import static org.codesearch.cli.Ansi.RED;
import static org.codesearch.cli.Ansi.color;

public final class CodeSearchCli {
    private final LanguageRegistry registry;
    private final CodeSearchEngine engine;
    private final CommandLine commandLine;
    private final PrintStream out;
    private final PrintStream err;

    public CodeSearchCli(LanguageRegistry registry, Path indexPath, PrintStream out, PrintStream err) {
        this.registry = registry;
        this.engine = new CodeSearchEngine(registry, indexPath);
        this.commandLine = new CommandLine(registry);
        this.out = out;
        this.err = err;
    }

    public int run(String[] args) {
        if (args.length == 0 || isHelp(args[0])) {
            printHelp();
            return 0;
        }
        return switch (args[0].toLowerCase()) {
            case "index" -> index(args);
            case "stats" -> stats(args);
            case "bench" -> bench(args);
            case "search" -> exactSearch(args);
            case "grep" -> quickSearch(args, 1);
            case "--cached", "cached" -> cachedSearch(args, 1);
            default -> quickSearch(args, 0);
        };
    }

    private int index(String[] args) {
        IndexCommand command;
        try {
            command = commandLine.parseIndex(args);
        } catch (IllegalArgumentException e) {
            err.println(color(RED, "Ошибка индексации: ") + e.getMessage());
            err.println(color(DIM, "Использование: ") + "codesearch index [--lang LANG] [path]");
            return 1;
        }

        try {
            List<IndexReport> reports = engine.index(command.language(), Path.of(command.sourcePath()));
            out.println(color(GREEN, "Готово") + "  Индексация завершена");
            out.println(color(DIM, "Путь:  ") + command.sourcePath());
            for (IndexReport report : reports) {
                out.println(color(DIM, "Язык: ") + report.language()
                        + "  (файлов: " + report.indexedFiles()
                        + ", сущностей: " + report.entities()
                        + (report.skippedFiles() > 0 ? ", пропущено файлов: " + report.skippedFiles() : "")
                        + ")");
            }
            return 0;
        } catch (IllegalArgumentException | IOException e) {
            err.println(color(RED, "Ошибка индексации: ") + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println(color(RED, "Ошибка индексации: ") + "индексация была прервана.");
            return 1;
        }
    }

    private int stats(String[] args) {
        StatsCommand command;
        try {
            command = commandLine.parseStats(args);
        } catch (IllegalArgumentException e) {
            err.println(color(RED, "Ошибка статистики: ") + e.getMessage());
            err.println(color(DIM, "Использование: ") + "codesearch stats [--lang LANG] [--path PATH]");
            return 1;
        }

        try {
            Map<String, IndexStats> stats = engine.stats(command.language(), command.pathFilter());
            out.println(color(BLUE, "Статистика индекса"));
            stats.forEach((language, languageStats) -> printStats(language, languageStats, command.pathFilter()));
            return 0;
        } catch (IndexUnavailableException e) {
            printIndexUnavailable(e, "codesearch index [path]");
            return 1;
        } catch (IOException e) {
            err.println(color(RED, "Ошибка чтения индекса: ") + e.getMessage());
            return 1;
        }
    }

    private void printStats(String language, IndexStats stats, String pathFilter) {
        out.println(color(DIM, "Язык: ") + language);
        if (pathFilter != null) {
            out.println(color(DIM, "Фильтр пути: ") + pathFilter);
        }
        out.println("Файлов: " + stats.totalFiles());
        out.println("Сущностей: " + stats.totalEntities());
        out.println(color(BLUE, "По видам сущностей"));
        for (EntityKind kind : EntityKind.values()) {
            long count = stats.entitiesByKind().getOrDefault(kind, 0L);
            if (count > 0) {
                out.printf("  %-16s %d%n", kind.displayName() + ":", count);
            }
        }
        out.println();
    }

    private int bench(String[] args) {
        CommandLine.BenchCommand command;
        try {
            command = commandLine.parseBench(args);
        } catch (IllegalArgumentException e) {
            err.println(color(RED, "Ошибка бенчмарка: ") + e.getMessage());
            err.println(color(DIM, "Использование: ") + "codesearch bench [--lang LANG] [--repeat N] <path>");
            return 1;
        }
        try {
            new BenchmarkCommand(registry, out).run(command.language(), Path.of(command.sourcePath()), command.repeat());
            return 0;
        } catch (IllegalArgumentException | IOException e) {
            err.println(color(RED, "Ошибка бенчмарка: ") + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println(color(RED, "Ошибка бенчмарка: ") + "индексация была прервана.");
            return 1;
        }
    }

    private int quickSearch(String[] args, int startIndex) {
        SearchCommand command;
        try {
            command = commandLine.parseSearch(args, startIndex, false);
        } catch (IllegalArgumentException e) {
            err.println(color(RED, "Ошибка поиска: ") + e.getMessage());
            err.println(color(DIM, "Использование: ") + "codesearch [options] <query> [path]");
            return 1;
        }

        try {
            SearchResponse response = CodeSearchEngine.quickSearch(
                    registry, command.language(), Path.of(command.path()), command.toRequest(MatchMode.SUBSTRING, null));
            ResultPrinter.print(out, response, command.output(), command.assignableTargetType());
            return 0;
        } catch (IllegalArgumentException e) {
            err.println(color(RED, "Ошибка поиска: ") + e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println(color(RED, "Ошибка чтения или записи индекса: ") + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println(color(RED, "Ошибка поиска: ") + "индексация была прервана.");
            return 1;
        }
    }

    private int cachedSearch(String[] args, int startIndex) {
        SearchCommand command;
        try {
            command = commandLine.parseSearch(args, startIndex, true);
        } catch (IllegalArgumentException e) {
            err.println(color(RED, "Ошибка поиска: ") + e.getMessage());
            err.println(color(DIM, "Использование: ") + "codesearch --cached [options] <query> [path-filter]");
            return 1;
        }
        return searchIndex(command, MatchMode.SUBSTRING, "codesearch index [path]");
    }

    private int exactSearch(String[] args) {
        if (args.length < 4) {
            err.println(color(RED, "Использование: ")
                    + "search <" + String.join("|", registry.languages()) + "> <kind> <query> [-f] [-cs] [--limit N] [--path PATH] [--snippet] [--json]");
            return 1;
        }
        SearchCommand command;
        try {
            command = commandLine.parseExactSearch(args);
        } catch (IllegalArgumentException e) {
            err.println(color(RED, "Некорректный запрос: ") + e.getMessage());
            return 1;
        }
        return searchIndex(command, MatchMode.EXACT, "codesearch index " + command.language() + " <path>");
    }

    private int searchIndex(SearchCommand command, MatchMode nameMatchMode, String indexHint) {
        try {
            SearchResponse response = engine.search(command.language(), command.toRequest(nameMatchMode, command.path()));
            ResultPrinter.print(out, response, command.output(), command.assignableTargetType());
            return 0;
        } catch (IllegalArgumentException e) {
            err.println(color(RED, "Некорректный запрос: ") + e.getMessage());
            return 1;
        } catch (IndexUnavailableException e) {
            printIndexUnavailable(e, indexHint);
            return 1;
        } catch (IOException e) {
            err.println(color(RED, "Ошибка чтения индекса: ") + e.getMessage());
            return 1;
        }
    }

    private void printIndexUnavailable(IndexUnavailableException e, String indexHint) {
        err.println(color(RED, "Индекс не готов: ") + e.getMessage());
        err.println(color(DIM, "Сначала выполните: ") + indexHint);
    }

    private static boolean isHelp(String value) {
        return "help".equalsIgnoreCase(value) || "-h".equalsIgnoreCase(value) || "--help".equalsIgnoreCase(value);
    }

    private void printHelp() {
        String languages = String.join("|", registry.languages());
        out.println(color(BLUE, "Команды"));
        out.println("  codesearch [options] <query> [path]");
        out.println("  codesearch [options] <kind> [--path path]");
        out.println("  codesearch [options] <kind> <query> [path]");
        out.println("  codesearch index [--lang " + languages + "] [path]");
        out.println("  codesearch stats [--lang " + languages + "] [--path PATH]");
        out.println("  codesearch bench [--lang " + languages + "] [--repeat N] <path>");
        out.println("  codesearch --cached [options] <kind>");
        out.println("  codesearch --cached [options] <query> [path-filter]");
        out.println("  codesearch --cached [options] <kind> <query> [path-filter]");
        out.println();
        out.println("  Без --lang поиск и индексация идут по всем поддерживаемым языкам.");
        out.println();
        out.println(color(BLUE, "Виды поиска"));
        out.println("  Java: class|record|method|field|interface|local-variable <name>");
        out.println("  Go: package|import|function|method|struct|interface|field|var|const <name>");
        out.println("  Python: import|class|function|method|field|var|const|local-variable <name>");
        out.println("  annotation <name>                 Java-аннотации");
        out.println("  decorator <name>                  Python-декораторы");
        out.println("  field-type <type>                 поля с точным declared type");
        out.println("  local-variable-type <type>        локальные переменные с точным declared type");
        out.println("  variable-type <type>              переменные уровня модуля/пакета с указанным типом");
        out.println("  method-return-type <type>         методы с указанным return type");
        out.println("  function-return-type <type>       функции с указанным return type");
        out.println("  variable-assignable-to <type>     переменные совместимого или родственного типа");
        out.println("  subtypes-of <type>                наследники класса и реализации интерфейса");
        out.println("  calls <name>                      места вызова функции, метода или конструктора");
        out.println();
        out.println(color(BLUE, "Опции"));
        out.println("  -k, --kind KIND        искать только сущности указанного вида");
        out.println("  -n, --limit N          показать не больше N результатов");
        out.println("  -cs, --case-sensitive  учитывать регистр");
        out.println("  --cached               искать по постоянному индексу без переиндексации");
        out.println("  -p, --path PATH        путь для быстрого поиска или фильтр пути для --cached");
        out.println("  --explain              показать, почему результат подошел под запрос");
        out.println("  -f, --fuzzy            искать имя сущности с небольшой опечаткой");
        out.println("  --in NAME              только сущности внутри класса/функции с таким именем");
        out.println("  --json                 вывод в формате JSON");
        out.println("  --snippet              показать фрагмент кода вокруг результата");
        out.println("  -A, --after N          показать N строк после результата");
        out.println("  -B, --before N         показать N строк до результата");
        out.println("  -C, --context N        показать N строк до и после результата");
        out.println("  --lang " + languages + "  язык исходного кода (по умолчанию — все)");
        out.println();
        out.println(color(BLUE, "Примеры"));
        out.println("  codesearch class");
        out.println("  codesearch index .");
        out.println("  codesearch stats");
        out.println("  codesearch --cached variable-assignable-to Appendable --explain");
        out.println("  codesearch --lang go function intMin app/src/test/resources/go");
        out.println("  codesearch --lang python decorator route app/src/test/resources/python");
    }
}
