package org.example;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Совместимая обертка для старого legacy-пакета.
 *
 * <p>Основная реализация Java-индексации теперь находится в
 * {@link org.codesearch.java.JavaSourceIndexer}.
 */
public class JavaSourceIndexer {

    public static void indexJavaSources(String directoryPath) throws IOException, InterruptedException {
        org.codesearch.java.JavaSourceIndexer.indexJavaSources(directoryPath);
    }

    public static void indexJavaSources(String directoryPath, Path indexDirectoryPath) throws IOException, InterruptedException {
        org.codesearch.java.JavaSourceIndexer.indexJavaSources(directoryPath, indexDirectoryPath);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        indexJavaSources("src");
    }
}
