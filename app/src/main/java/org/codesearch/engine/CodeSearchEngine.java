package org.codesearch.engine;

import org.codesearch.core.IndexStats;
import org.codesearch.core.IndexUnavailableException;
import org.codesearch.core.SearchRequest;
import org.codesearch.core.SearchResponse;
import org.codesearch.index.IndexManifest;
import org.codesearch.index.IndexReport;
import org.codesearch.index.SourceFiles;
import org.codesearch.index.SourceIndexer;
import org.codesearch.plugin.LanguagePlugin;
import org.codesearch.search.LanguageSearchService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CodeSearchEngine {
    private final LanguageRegistry registry;
    private final Path indexRoot;

    public CodeSearchEngine(LanguageRegistry registry, Path indexRoot) {
        if (registry == null || indexRoot == null) {
            throw new IllegalArgumentException("Registry and index root must not be null");
        }
        this.registry = registry;
        this.indexRoot = indexRoot;
    }

    public LanguageRegistry registry() {
        return registry;
    }

    public Path indexRoot() {
        return indexRoot;
    }

    public Path indexDirectory(String language) {
        return indexRoot.resolve(registry.require(language).language());
    }

    public List<IndexReport> index(String language, Path sourceRoot) throws IOException, InterruptedException {
        Collection<LanguagePlugin> plugins = language == null ? registry.all() : List.of(registry.require(language));
        Map<LanguagePlugin, List<Path>> filesByPlugin = SourceFiles.collect(sourceRoot, plugins);
        if (filesByPlugin.isEmpty()) {
            throw new IllegalArgumentException("В указанном пути нет " + describeExtensions(plugins) + " файлов: " + sourceRoot);
        }

        IndexManifest manifest = language == null
                ? IndexManifest.empty()
                : IndexManifest.load(indexRoot).orElseGet(IndexManifest::empty);
        List<IndexReport> reports = new ArrayList<>();
        for (Map.Entry<LanguagePlugin, List<Path>> entry : filesByPlugin.entrySet()) {
            LanguagePlugin plugin = entry.getKey();
            IndexReport report = SourceIndexer.index(plugin, entry.getValue(), indexRoot.resolve(plugin.language()));
            manifest.record(report, sourceRoot);
            reports.add(report);
        }
        manifest.save(indexRoot);
        return reports;
    }

    public SearchResponse search(String language, SearchRequest request) throws IOException {
        if (language != null) {
            LanguagePlugin plugin = registry.require(language);
            return new LanguageSearchService(indexRoot.resolve(plugin.language()), plugin).search(request);
        }

        List<LanguagePlugin> indexed = indexedPlugins();
        if (indexed.isEmpty()) {
            throw new IndexUnavailableException("Индекс не найден: " + indexRoot);
        }
        List<LanguagePlugin> applicable = indexed.stream()
                .filter(plugin -> LanguageSearchService.supports(plugin, request))
                .toList();
        if (applicable.isEmpty()) {
            if (indexed.size() == 1) {
                LanguageSearchService.validate(indexed.getFirst(), request);
            }
            throw new IllegalArgumentException("Ни один из проиндексированных языков ("
                    + indexed.stream().map(LanguagePlugin::language).collect(Collectors.joining(", "))
                    + ") не поддерживает такой запрос");
        }

        List<SearchResponse> responses = new ArrayList<>();
        for (LanguagePlugin plugin : applicable) {
            responses.add(new LanguageSearchService(indexRoot.resolve(plugin.language()), plugin).search(request));
        }
        return SearchResponse.merge(responses, request.limit());
    }

    public static SearchResponse quickSearch(LanguageRegistry registry, String language, Path sourceRoot, SearchRequest request)
            throws IOException, InterruptedException {
        Collection<LanguagePlugin> plugins = language == null ? registry.all() : List.of(registry.require(language));
        if (language != null) {
            LanguageSearchService.validate(registry.require(language), request);
        }
        Map<LanguagePlugin, List<Path>> filesByPlugin = SourceFiles.collect(sourceRoot, plugins);
        if (filesByPlugin.isEmpty()) {
            throw new IllegalArgumentException("В указанном пути нет " + describeExtensions(plugins) + " файлов: " + sourceRoot);
        }

        Map<LanguagePlugin, List<Path>> applicable = new LinkedHashMap<>();
        filesByPlugin.forEach((plugin, files) -> {
            if (LanguageSearchService.supports(plugin, request)) {
                applicable.put(plugin, files);
            }
        });
        if (applicable.isEmpty()) {
            if (filesByPlugin.size() == 1) {
                LanguageSearchService.validate(filesByPlugin.keySet().iterator().next(), request);
            }
            throw new IllegalArgumentException("Ни один из найденных языков ("
                    + filesByPlugin.keySet().stream().map(LanguagePlugin::language).collect(Collectors.joining(", "))
                    + ") не поддерживает такой запрос");
        }

        Path temporaryRoot = Files.createTempDirectory("codesearch-quick-index");
        try {
            List<SearchResponse> responses = new ArrayList<>();
            for (Map.Entry<LanguagePlugin, List<Path>> entry : applicable.entrySet()) {
                LanguagePlugin plugin = entry.getKey();
                Path languageIndex = temporaryRoot.resolve(plugin.language());
                SourceIndexer.index(plugin, entry.getValue(), languageIndex);
                responses.add(new LanguageSearchService(languageIndex, plugin).search(request));
            }
            return SearchResponse.merge(responses, request.limit());
        } finally {
            deleteQuietly(temporaryRoot);
        }
    }

    public Map<String, IndexStats> stats(String language, String pathFilter) throws IOException {
        List<LanguagePlugin> plugins = language == null ? indexedPlugins() : List.of(registry.require(language));
        if (plugins.isEmpty()) {
            throw new IndexUnavailableException("Индекс не найден: " + indexRoot);
        }
        Map<String, IndexStats> stats = new LinkedHashMap<>();
        for (LanguagePlugin plugin : plugins) {
            stats.put(plugin.language(), new LanguageSearchService(indexRoot.resolve(plugin.language()), plugin).stats(pathFilter));
        }
        return stats;
    }

    public List<LanguagePlugin> indexedPlugins() throws IOException {
        var manifest = IndexManifest.load(indexRoot);
        if (manifest.isPresent()) {
            return manifest.get().languages().stream()
                    .map(registry::find)
                    .flatMap(java.util.Optional::stream)
                    .toList();
        }
        return registry.all().stream()
                .filter(plugin -> Files.isDirectory(indexRoot.resolve(plugin.language())))
                .toList();
    }

    private static String describeExtensions(Collection<LanguagePlugin> plugins) {
        return plugins.stream()
                .flatMap(plugin -> plugin.fileExtensions().stream())
                .sorted()
                .collect(Collectors.joining(", "));
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
