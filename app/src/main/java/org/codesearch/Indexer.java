package org.codesearch;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Set;

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

    private static Logger logger = LogManager.getLogger();
    protected Set<String> infoKeys = new HashSet<>();

    public void indexSources(Path dirPath, Path indexDirPath) 
        throws IOException, InterruptedException {
        try {
            clearIndices(indexDirPath);
        } catch (RuntimeException e) {
            throw new IOException(e);
        }

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
                        } catch (Exception e) {
                            logger.error("indexing error on file: {}", file, e);
                        }
                    }));

                executor.shutdown();
                if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                    logger.error("indexing time has expired");
                    executor.shutdownNow();
                }

                try {
                    List<String> infoKeysArr = new ArrayList<>(infoKeys);
                    infoKeysArr.sort(String::compareTo);
                    Path infoKeysPath = indexDirPath.resolve("info_keys.txt");
                    Files.createFile(infoKeysPath);
                    FileWriter infoKeysWriter = new FileWriter(infoKeysPath.toString(), true);
                    for (String key: infoKeysArr) {
                        infoKeysWriter.write(key + '\n');
                    }
                    infoKeysWriter.close();
                } catch (IOException e) {
                    logger.error("info_keys file error", e);
                }   
            } 
            catch (IOException | InterruptedException e) {
                logger.error("file reading error", e);
                throw e;
            } 
            finally {
                if (!executor.isTerminated()) {
                    logger.warn("indexing failed with an error");
                    executor.shutdownNow();
                }
            }
        } 
        catch (IOException | InterruptedException e) {
            logger.error("indexing error", e);
            throw e;
        }
    }

    private void clearIndices(Path path) throws IOException, RuntimeException {
        if (!Files.exists(path)) {return;}
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    logger.error("deleting error on file: ", file, e);
                    throw new RuntimeException();
                }
            });
    }
}
