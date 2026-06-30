package org.codesearch.core;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.MMapDirectory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codesearch.plugin.LanguagePlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class SourceIndexer {
    private static final Logger logger = LogManager.getLogger(SourceIndexer.class);
    private static final Set<String> EXCLUDED_DIRECTORY_NAMES = Set.of(
            ".git",
            ".gradle",
            ".idea",
            "build",
            "dist",
            "node_modules",
            "out",
            "target"
    );

    private SourceIndexer() {}

    public static void indexSources(LanguagePlugin plugin, Path sourceRoot, Path indexDirectory)
            throws IOException, InterruptedException {
        validateSourcePath(sourceRoot);

        List<Path> sourceFiles = collectSourceFiles(sourceRoot, plugin.fileExtensions());
        if (sourceFiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "В указанном пути нет файлов " + plugin.fileExtensions() + ": " + sourceRoot
            );
        }

        IndexingContext context = plugin.prepareIndexing(sourceFiles);
        deleteDirectoryRecursively(indexDirectory);

        try (MMapDirectory directory = new MMapDirectory(indexDirectory);
             StandardAnalyzer analyzer = new StandardAnalyzer();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {

            EntityExtractor extractor = plugin.createExtractor(context);
            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            try {
                sourceFiles.forEach(file -> executor.submit(() -> {
                    try {
                        indexFile(file, writer, extractor);
                        logger.info("Файл проиндексирован: {}", file);
                    } catch (IOException | RuntimeException e) {
                        logger.debug("Файл пропущен при индексации {}: {}", file, e.getMessage(), e);
                    }
                }));

                executor.shutdown();
                if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                    logger.error("Время ожидания завершения задач индексации истекло.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } finally {
                if (!executor.isTerminated()) {
                    logger.warn("Индексация не была завершена.");
                    executor.shutdownNow();
                }
            }
        }
    }

    private static void validateSourcePath(Path sourcePath) {
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Путь не найден: " + sourcePath);
        }
        if (!Files.isReadable(sourcePath)) {
            throw new IllegalArgumentException("Нет доступа на чтение: " + sourcePath);
        }
    }

    private static List<Path> collectSourceFiles(Path sourcePath, Set<String> extensions) throws IOException {
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(file -> extensions.stream().anyMatch(ext -> file.toString().endsWith(ext)))
                    .filter(SourceIndexer::isIndexablePath)
                    .toList();
        }
    }

    private static boolean isIndexablePath(Path file) {
        for (Path part : file.normalize()) {
            if (EXCLUDED_DIRECTORY_NAMES.contains(part.toString())) {
                return false;
            }
        }
        return true;
    }

    private static void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> paths = Files.walk(path)) {
                for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private static void indexFile(Path file, IndexWriter writer, EntityExtractor extractor) throws IOException {
        List<CodeEntity> entities = extractor.extractEntities(file);
        synchronized (writer) {
            for (CodeEntity entity : entities) {
                writer.addDocument(IndexDocumentMapper.toDocument(entity));
            }
        }
    }
}
