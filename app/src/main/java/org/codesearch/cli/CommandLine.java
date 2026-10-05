package org.codesearch.cli;

import org.codesearch.core.EntityKind;
import org.codesearch.core.MatchMode;
import org.codesearch.core.SearchRequest;
import org.codesearch.core.SearchTarget;
import org.codesearch.engine.LanguageRegistry;

import java.util.ArrayList;
import java.util.List;

final class CommandLine {
    static final int DEFAULT_SNIPPET_CONTEXT = 2;
    static final int DEFAULT_BENCH_REPEAT = 20;

    record SnippetOptions(boolean enabled, int before, int after) {
        static SnippetOptions disabled() {
            return new SnippetOptions(false, 0, 0);
        }
    }

    record OutputOptions(boolean explain, boolean json, SnippetOptions snippet) {}

    record SearchCommand(
            String language,
            String query,
            EntityKind kind,
            SearchTarget target,
            boolean fuzzy,
            boolean caseSensitive,
            int limit,
            String path,
            String containerFilter,
            OutputOptions output
    ) {
        SearchRequest toRequest(MatchMode nameMatchMode, String pathFilter) {
            MatchMode matchMode = fuzzy ? MatchMode.FUZZY
                    : target == SearchTarget.CONTENT ? nameMatchMode : MatchMode.EXACT;
            return new SearchRequest(query, kind, target, matchMode, caseSensitive, limit, pathFilter, containerFilter);
        }

        String assignableTargetType() {
            return target == SearchTarget.ASSIGNABLE_TYPE ? query : null;
        }
    }

    record IndexCommand(String language, String sourcePath) {}

    record StatsCommand(String language, String pathFilter) {}

    record BenchCommand(String language, String sourcePath, int repeat) {}

    private final LanguageRegistry registry;

    CommandLine(LanguageRegistry registry) {
        this.registry = registry;
    }

    IndexCommand parseIndex(String[] args) {
        String language = null;
        String sourcePath = null;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (isFlag(arg, "--lang", "-l")) {
                language = language(requireValue(args, ++i, "Не указан язык."));
            } else if (registry.supports(arg)) {
                language = language(arg);
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Неизвестный флаг: " + arg);
            } else if (sourcePath != null) {
                throw new IllegalArgumentException("слишком много аргументов.");
            } else {
                sourcePath = arg;
            }
        }
        return new IndexCommand(language, sourcePath == null ? "." : sourcePath);
    }

    StatsCommand parseStats(String[] args) {
        String language = null;
        String pathFilter = null;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (isFlag(arg, "--lang", "-l")) {
                language = language(requireValue(args, ++i, "Не указан язык."));
            } else if (isFlag(arg, "--path", "-p")) {
                pathFilter = requireValue(args, ++i, "Не указан фильтр пути.");
            } else if (registry.supports(arg)) {
                language = language(arg);
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Неизвестный флаг: " + arg);
            } else {
                throw new IllegalArgumentException("Неизвестный аргумент: " + arg);
            }
        }
        return new StatsCommand(language, pathFilter);
    }

    BenchCommand parseBench(String[] args) {
        String language = null;
        String sourcePath = null;
        int repeat = DEFAULT_BENCH_REPEAT;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (isFlag(arg, "--lang", "-l")) {
                language = language(requireValue(args, ++i, "Не указан язык."));
            } else if (isFlag(arg, "--repeat")) {
                repeat = parseLimit(requireValue(args, ++i, "Не указано число повторов."));
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Неизвестный флаг: " + arg);
            } else if (sourcePath != null) {
                throw new IllegalArgumentException("слишком много аргументов.");
            } else {
                sourcePath = arg;
            }
        }
        if (sourcePath == null) {
            throw new IllegalArgumentException("Не указан путь к исходникам.");
        }
        return new BenchCommand(language, sourcePath, repeat);
    }

    SearchCommand parseSearch(String[] args, int startIndex, boolean cached) {
        Flags flags = new Flags();
        List<String> operands = new ArrayList<>();
        for (int i = startIndex; i < args.length; i++) {
            int consumed = flags.parse(args, i);
            if (consumed < 0) {
                if (args[i].startsWith("-")) {
                    throw new IllegalArgumentException("Неизвестный флаг: " + args[i]);
                }
                operands.add(args[i]);
            } else {
                i += consumed;
            }
        }

        if (!operands.isEmpty() && registry.supports(operands.getFirst())) {
            flags.language = language(operands.removeFirst());
        }

        EntityKind kind = flags.kind;
        SearchTarget target = SearchTarget.CONTENT;
        String query;
        String path = flags.path;
        SearchKinds.SearchKind searchKind = operands.isEmpty() ? null : SearchKinds.parseOrNull(operands.getFirst());
        boolean kindOperand = flags.kind == null && searchKind != null;

        if (operands.size() == 1 && kindOperand && searchKind.listable()) {
            kind = searchKind.kind();
            query = null;
        } else if (operands.size() == 1) {
            query = operands.getFirst();
        } else if (operands.size() == 2 && kindOperand) {
            kind = searchKind.kind();
            target = searchKind.target();
            query = operands.get(1);
        } else if (operands.size() == 2) {
            query = operands.getFirst();
            path = operands.get(1);
        } else if (operands.size() == 3 && kindOperand) {
            kind = searchKind.kind();
            target = searchKind.target();
            query = operands.get(1);
            path = operands.get(2);
        } else if (operands.size() == 3) {
            throw new IllegalArgumentException(cached
                    ? "Нужно указать запрос и необязательный фильтр пути."
                    : "Нужно указать запрос и путь.");
        } else {
            throw new IllegalArgumentException("Нужно указать запрос.");
        }

        return flags.toCommand(query, kind, target, path == null && !cached ? "." : path);
    }

    SearchCommand parseExactSearch(String[] args) {
        String language = language(args[1]);
        SearchKinds.SearchKind searchKind = SearchKinds.parseOrNull(args[2]);
        if (searchKind == null) {
            throw new IllegalArgumentException("Unsupported entity kind: " + args[2]);
        }

        Flags flags = new Flags();
        flags.language = language;
        for (int i = 4; i < args.length; i++) {
            int consumed = flags.parse(args, i);
            if (consumed < 0) {
                throw new IllegalArgumentException("Неизвестный флаг: " + args[i]);
            }
            i += consumed;
        }
        return flags.toCommand(args[3], searchKind.kind(), searchKind.target(), flags.path);
    }

    String language(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Не указан язык.");
        }
        return registry.require(value.trim()).language();
    }

    private static boolean isFlag(String arg, String... names) {
        for (String name : names) {
            if (name.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String requireValue(String[] args, int index, String message) {
        if (index >= args.length) {
            throw new IllegalArgumentException(message);
        }
        return args[index];
    }

    private static int parseLimit(String value) {
        int limit;
        try {
            limit = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Лимит должен быть числом: " + value, e);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Лимит должен быть положительным числом.");
        }
        return limit;
    }

    private static int parseLineCount(String[] args, int index, String flag) {
        String value = requireValue(args, index, "Не указано количество строк для " + flag + ".");
        int lines;
        try {
            lines = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Количество строк для " + flag + " должно быть числом: " + value, e);
        }
        if (lines < 0) {
            throw new IllegalArgumentException("Количество строк для " + flag + " не может быть отрицательным.");
        }
        return lines;
    }

    private final class Flags {
        private String language;
        private EntityKind kind;
        private boolean caseSensitive;
        private boolean fuzzy;
        private int limit = SearchRequest.DEFAULT_LIMIT;
        private boolean explain;
        private boolean json;
        private String path;
        private String containerFilter;
        private boolean snippet;
        private int before;
        private int after;

        int parse(String[] args, int i) {
            String arg = args[i];
            if (isFlag(arg, "--snippet")) {
                snippet = true;
                if (before == 0 && after == 0) {
                    before = DEFAULT_SNIPPET_CONTEXT;
                    after = DEFAULT_SNIPPET_CONTEXT;
                }
                return 0;
            }
            if ("-A".equals(arg) || isFlag(arg, "--after")) {
                snippet = true;
                after = parseLineCount(args, i + 1, arg);
                return 1;
            }
            if ("-B".equals(arg) || isFlag(arg, "--before")) {
                snippet = true;
                before = parseLineCount(args, i + 1, arg);
                return 1;
            }
            if ("-C".equals(arg) || isFlag(arg, "--context")) {
                snippet = true;
                before = parseLineCount(args, i + 1, arg);
                after = before;
                return 1;
            }
            if (isFlag(arg, "--json")) {
                json = true;
                return 0;
            }
            if (isFlag(arg, "--explain")) {
                explain = true;
                return 0;
            }
            if (isFlag(arg, "--fuzzy", "-f")) {
                fuzzy = true;
                return 0;
            }
            if (isFlag(arg, "-r", "--recursive")) {
                return 0;
            }
            if (isFlag(arg, "-cs", "--case-sensitive")) {
                caseSensitive = true;
                return 0;
            }
            if (isFlag(arg, "--lang", "-l")) {
                language = language(requireValue(args, i + 1, "Не указан язык."));
                return 1;
            }
            if (isFlag(arg, "--kind", "-k", "--type")) {
                kind = EntityKind.fromValue(requireValue(args, i + 1, "Не указан тип сущности."));
                return 1;
            }
            if (isFlag(arg, "--limit", "-n")) {
                limit = parseLimit(requireValue(args, i + 1, "Не указан лимит результатов."));
                return 1;
            }
            if (isFlag(arg, "--path", "-p")) {
                path = requireValue(args, i + 1, "Не указан путь.");
                return 1;
            }
            if (isFlag(arg, "--in")) {
                containerFilter = requireValue(args, i + 1, "Не указан контейнер.");
                return 1;
            }
            return -1;
        }

        SearchCommand toCommand(String query, EntityKind kind, SearchTarget target, String path) {
            SnippetOptions snippetOptions = snippet ? new SnippetOptions(true, before, after) : SnippetOptions.disabled();
            return new SearchCommand(language, query, kind, target, fuzzy, caseSensitive, limit, path, containerFilter,
                    new OutputOptions(explain, json, snippetOptions));
        }
    }
}
