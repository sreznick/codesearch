package org.codesearch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.MMapDirectory;

public abstract class Indexer {
    protected abstract List<String> getExtensions();
    protected abstract List<Document> indexFile(Path file) throws Exception;

    private static final Logger logger = LogManager.getLogger();

    public void indexSources(Path dirPath, Path indexDirPath) 
        throws IOException, InterruptedException {
        clearIndices(indexDirPath);

        try (MMapDirectory directory = new MMapDirectory(indexDirPath);
             StandardAnalyzer analyzer = new StandardAnalyzer();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {

            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            try {
                Files.walk(dirPath)
                    .filter(Files::isRegularFile)
                    .filter(file -> {
                        for (String ext: getExtensions()) {
                            if (file.toString().endsWith(ext)) {return true;}
                        }
                        return false;
                    })
                    .forEach(file -> executor.submit(() -> {
                        try {
                            writer.addDocuments(indexFile(file));
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

            } 
            catch (IOException | InterruptedException e) {
                logger.error("Ошибка при чтении файлов для индексации.", e);
                throw e;
            } 
            finally {
                if (!executor.isTerminated()) {
                    logger.warn("Индексация не была завершена.");
                    executor.shutdownNow();
                }
            }
            logger.info("Индексация успешно завершена.");
        } 
        catch (IOException | InterruptedException e) {
            logger.error("Ошибка при индексировании.", e);
            throw e;
        }
    }

    private static void clearIndices(Path path) throws IOException {
        if (!Files.exists(path)) {return;}
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
