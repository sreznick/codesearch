package org.codesearch.cli;

import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityAttributes;
import org.codesearch.core.EntityKind;
import org.codesearch.core.MatchMode;
import org.codesearch.core.SearchRequest;
import org.codesearch.core.SearchResponse;
import org.codesearch.core.SearchResult;
import org.codesearch.core.SearchTarget;
import org.codesearch.engine.CodeSearchEngine;
import org.codesearch.engine.LanguageRegistry;
import org.codesearch.index.IndexReport;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

final class BenchmarkCommand {
    private static final int SAMPLE_LIMIT = 5000;

    record Scenario(String title, String query, SearchRequest request) {}

    record Measurement(Scenario scenario, long hits, double medianMillis, double p95Millis) {}

    private final LanguageRegistry registry;
    private final PrintStream out;

    BenchmarkCommand(LanguageRegistry registry, PrintStream out) {
        this.registry = registry;
        this.out = out;
    }

    void run(String language, Path sourceRoot, int repeat) throws IOException, InterruptedException {
        Path indexRoot = Files.createTempDirectory("codesearch-bench");
        try {
            CodeSearchEngine engine = new CodeSearchEngine(registry, indexRoot);
            long start = System.nanoTime();
            List<IndexReport> reports = engine.index(language, sourceRoot);
            double indexSeconds = (System.nanoTime() - start) / 1e9;

            printIndexing(sourceRoot, reports, indexSeconds, directorySize(indexRoot));

            List<Measurement> measurements = new ArrayList<>();
            for (Scenario scenario : scenarios(engine, language)) {
                measurements.add(measure(engine, language, scenario, repeat));
            }
            printQueries(measurements, repeat);
        } finally {
            deleteQuietly(indexRoot);
        }
    }

    private void printIndexing(Path sourceRoot, List<IndexReport> reports, double seconds, long indexBytes) {
        long files = reports.stream().mapToLong(IndexReport::indexedFiles).sum();
        long entities = reports.stream().mapToLong(IndexReport::entities).sum();
        out.println("## Индексация: " + sourceRoot);
        out.println();
        out.println("| Язык | Файлов | Пропущено | Сущностей |");
        out.println("|---|---:|---:|---:|");
        for (IndexReport report : reports) {
            out.printf("| %s | %d | %d | %d |%n", report.language(), report.indexedFiles(), report.skippedFiles(), report.entities());
        }
        out.println();
        out.printf(Locale.ROOT, "Время индексации: %.2f с (%.0f файлов/с), размер индекса: %.1f МБ, сущностей: %d%n",
                seconds, files / Math.max(seconds, 1e-9), indexBytes / 1024.0 / 1024.0, entities);
        out.println();
    }

    private void printQueries(List<Measurement> measurements, int repeat) {
        out.println("## Запросы (повторов: " + repeat + ")");
        out.println();
        out.println("| Сценарий | Запрос | Найдено | Медиана, мс | p95, мс |");
        out.println("|---|---|---:|---:|---:|");
        for (Measurement measurement : measurements) {
            out.printf(Locale.ROOT, "| %s | `%s` | %d | %.2f | %.2f |%n",
                    measurement.scenario().title(), measurement.scenario().query(),
                    measurement.hits(), measurement.medianMillis(), measurement.p95Millis());
        }
    }

    private Measurement measure(CodeSearchEngine engine, String language, Scenario scenario, int repeat) throws IOException {
        SearchResponse warmup = engine.search(language, scenario.request());
        List<Double> timings = new ArrayList<>();
        for (int i = 0; i < repeat; i++) {
            long start = System.nanoTime();
            engine.search(language, scenario.request());
            timings.add((System.nanoTime() - start) / 1e6);
        }
        timings.sort(Comparator.naturalOrder());
        double median = timings.get(timings.size() / 2);
        double p95 = timings.get(Math.min(timings.size() - 1, (int) Math.ceil(timings.size() * 0.95) - 1));
        return new Measurement(scenario, warmup.totalHits(), median, p95);
    }

    private List<Scenario> scenarios(CodeSearchEngine engine, String language) {
        List<Scenario> scenarios = new ArrayList<>();
        List<CodeEntity> types = sample(engine, language, EntityKind.CLASS, EntityKind.STRUCT, EntityKind.INTERFACE);
        List<CodeEntity> callables = sample(engine, language, EntityKind.METHOD, EntityKind.FUNCTION);
        List<CodeEntity> calls = sample(engine, language, EntityKind.CALL);

        middle(types).ifPresent(type -> scenarios.add(new Scenario("Тип по имени (точно)", type.kind().key() + " " + type.content(),
                SearchRequest.builder(type.content()).kind(type.kind()).build())));
        middle(callables).filter(callable -> callable.content().length() >= 4).ifPresent(callable -> {
            String fragment = callable.content().substring(0, 4);
            scenarios.add(new Scenario("Метод/функция по подстроке", callable.kind().key() + " " + fragment,
                    SearchRequest.builder(fragment).kind(callable.kind()).matchMode(MatchMode.SUBSTRING).build()));
        });
        middle(types).filter(type -> type.content().length() >= 5).ifPresent(type -> {
            String typo = type.content().substring(0, type.content().length() - 2) + type.content().charAt(type.content().length() - 1);
            scenarios.add(new Scenario("Имя с опечаткой (fuzzy)", type.kind().key() + " " + typo + " --fuzzy",
                    SearchRequest.builder(typo).kind(type.kind()).matchMode(MatchMode.FUZZY).build()));
        });
        middle(calls).ifPresent(call -> scenarios.add(new Scenario("Места вызова", "calls " + call.content(),
                SearchRequest.builder(call.content()).kind(EntityKind.CALL).build())));
        types.stream()
                .map(type -> EntityAttributes.splitList(type.attribute(EntityAttributes.SUPERTYPES)))
                .flatMap(java.util.Set::stream)
                .findFirst()
                .ifPresent(supertype -> scenarios.add(new Scenario("Наследники/реализации", "subtypes-of " + supertype,
                        SearchRequest.builder(supertype).target(SearchTarget.SUPERTYPE).build())));
        sample(engine, language, EntityKind.LOCAL_VARIABLE, EntityKind.FIELD).stream()
                .map(CodeEntity::declaredType)
                .filter(Objects::nonNull)
                .filter(type -> Character.isUpperCase(type.charAt(0)))
                .findFirst()
                .ifPresent(type -> scenarios.add(new Scenario("Совместимые с типом", "variable-assignable-to " + type,
                        SearchRequest.builder(type).target(SearchTarget.ASSIGNABLE_TYPE).build())));
        return scenarios.stream()
                .filter(scenario -> isSupported(engine, language, scenario.request()))
                .toList();
    }

    private static boolean isSupported(CodeSearchEngine engine, String language, SearchRequest request) {
        try {
            engine.search(language, request);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    private static List<CodeEntity> sample(CodeSearchEngine engine, String language, EntityKind... kinds) {
        List<CodeEntity> entities = new ArrayList<>();
        for (EntityKind kind : kinds) {
            try {
                engine.search(language, SearchRequest.listKind(kind).limit(SAMPLE_LIMIT).build()).results().stream()
                        .map(SearchResult::entity)
                        .forEach(entities::add);
            } catch (IOException | IllegalArgumentException ignored) {
            }
        }
        return entities;
    }

    private static Optional<CodeEntity> middle(List<CodeEntity> entities) {
        return entities.isEmpty() ? Optional.empty() : Optional.of(entities.get(entities.size() / 2));
    }

    private static long directorySize(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> file.toFile().length()).sum();
        }
    }

    private static void deleteQuietly(Path path) {
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        } catch (IOException ignored) {
        }
    }
}
