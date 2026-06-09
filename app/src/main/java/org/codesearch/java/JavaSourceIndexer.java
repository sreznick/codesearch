package org.codesearch.java;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.MMapDirectory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codesearch.core.CodeEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Класс для индексации Java-исходных кодов.
 * Проходит по указанным каталогам, анализирует содержимое и создает индекс данных,
 * которые можно использовать для быстрого поиска.
 */
public class JavaSourceIndexer {

    private static final Logger logger = LogManager.getLogger();
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

    public static void indexJavaSources(String directoryPath) throws IOException, InterruptedException {
        indexJavaSources(directoryPath, Paths.get("index"));
    }

    public static void indexJavaSources(String directoryPath, Path indexDirectoryPath) throws IOException, InterruptedException {
        Path sourcePath = Paths.get(directoryPath);
        validateSourcePath(sourcePath);

        List<Path> javaFiles = collectJavaFiles(sourcePath);
        if (javaFiles.isEmpty()) {
            throw new IllegalArgumentException("В указанном пути нет .java файлов: " + sourcePath);
        }

        deleteDirectoryRecursively(indexDirectoryPath);

        try (MMapDirectory directory = new MMapDirectory(indexDirectoryPath);
             StandardAnalyzer analyzer = new StandardAnalyzer();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {

            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            try {
                javaFiles.forEach(file -> executor.submit(() -> {
                    try {
                        indexJavaFile(file, writer);
                        logger.info("Файл проиндексирован: {}", file);
                    } catch (Exception e) {
                        logger.debug("Файл пропущен при индексации {}: {}", file, e.getMessage());
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

    private static List<Path> collectJavaFiles(Path sourcePath) throws IOException {
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .filter(JavaSourceIndexer::isIndexablePath)
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

    private static void indexJavaFile(Path file, IndexWriter writer) throws Exception {
        JavaEntityExtractor extractor = new JavaEntityExtractor();
        List<CodeEntity> entities = extractor.extractEntities(file);

        synchronized (writer) {
            for (CodeEntity entity : entities) {
                Document document = JavaIndexDocumentMapper.toDocument(entity);
                writer.addDocument(document);
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        indexJavaSources("src");
    }
}
