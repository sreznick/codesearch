package org.example;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.MMapDirectory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codesearch.core.CodeEntity;
import org.codesearch.java.JavaEntityExtractor;
import org.codesearch.java.JavaIndexDocumentMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Класс для индексации Java-исходных кодов.
 * Проходит по указанным каталогам, анализирует содержимое и создает индекс данных,
 * которые можно использовать для быстрого поиска.
 */
public class JavaSourceIndexer {

    private static final Logger logger = LogManager.getLogger();

    public static void indexJavaSources(String directoryPath) throws IOException, InterruptedException {
        Path indexDirectoryPath = Paths.get("index");

        deleteDirectoryRecursively(indexDirectoryPath);

        try (MMapDirectory directory = new MMapDirectory(indexDirectoryPath);
             StandardAnalyzer analyzer = new StandardAnalyzer();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {

            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            try {
                Files.walk(Paths.get(directoryPath))
                        .filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".java"))
                        .forEach(file -> executor.submit(() -> {
                            try {
                                indexJavaFile(file, writer);
                                logger.info("Файл проиндексирован: {}", file);
                            } catch (Exception e) {
                                logger.error("Ошибка при индексации файла: {}", file, e);
                            }
                        }));

                executor.shutdown();
                if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                    logger.error("Время ожидания завершения задач индексации истекло.");
                    executor.shutdownNow();
                }

            } catch (IOException | InterruptedException e) {
                logger.error("Ошибка при чтении файлов для индексации.", e);
                throw e;
            } finally {
                if (!executor.isTerminated()) {
                    logger.warn("Индексация не была завершена.");
                    executor.shutdownNow();
                }
            }
            //logger.info("Индексация успешно завершена.");
        } catch (IOException | InterruptedException e) {
            logger.error("Ошибка при индексировании.", e);
            throw e;
        }
    }

    private static void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            throw new RuntimeException("Ошибка при удалении файла: " + file, e);
                        }
                    });
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
