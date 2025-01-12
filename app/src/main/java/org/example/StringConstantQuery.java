package org.example;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.MMapDirectory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.example.JavaSourceIndexer.indexJavaSources;

public class StringConstantQuery {
    private static final Logger logger = LogManager.getLogger();

    public static StringBuilder logBuilder = new StringBuilder();


    private static void runQueryWithConfig(String queryString, String type, boolean isFuzzy, Consumer<Document> documentConsumer) {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try (MMapDirectory directory = new MMapDirectory(Paths.get("index"));
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);

            Query query = new BooleanQuery.Builder()
                    .add(isFuzzy ? new FuzzyQuery(new Term("content", queryString), 2)
                            : new TermQuery(new Term("content", queryString)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("type", type)), BooleanClause.Occur.MUST)
                    .build();

            String queryTypeMessage = isFuzzy ? " (с неточностями)" : "";
            String logMessage = "Запрос на " + type + queryTypeMessage + ": " + queryString;
            logger.info(logMessage);
            logBuilder.append(logMessage).append("\n");

            TopDocs results = searcher.search(query, 100000);
            logMessage = "Найдено совпадений " + (isFuzzy ? "с" : "c") + " " + queryString + ": " +
                    results.totalHits.toString().substring(0, Math.max(0, results.totalHits.toString().length() - 5));
            logger.info(logMessage);
            logBuilder.append(logMessage).append("\n");

            processResults(executor, results, searcher, documentConsumer);

        } catch (IOException e) {
            String errorMessage = "Ошибка при выполнении запроса: " + e.getMessage();
            logger.error(errorMessage, e);
            logBuilder.append(errorMessage).append("\n");
        }
    }

    private static void processResults(ExecutorService executor, TopDocs results, IndexSearcher searcher, Consumer<Document> documentConsumer) {
        Set<String> processedResults = ConcurrentHashMap.newKeySet();

        for (ScoreDoc scoreDoc : results.scoreDocs) {
            executor.submit(() -> {
                try {
                    Document doc = searcher.storedFields().document(scoreDoc.doc);
                    String content = doc.get("content");
                    String file = doc.get("file");
                    String line = doc.get("line");

                    String logMessage = String.format("Литерал: %s, Файл: %s, Строка: %s", content, file, line);
                    if (processedResults.add(logMessage)) {
                        documentConsumer.accept(doc);
                        logBuilder.append(logMessage).append("\n");
                    }
                } catch (IOException e) {
                    String errorMessage = "Ошибка при обработке документа: " + e.getMessage();
                    logger.error(errorMessage, e);
                    logBuilder.append(errorMessage).append("\n");
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            String errorMessage = "Ошибка при ожидании завершения обработки: " + e.getMessage();
            logger.error(errorMessage, e);
            logBuilder.append(errorMessage).append("\n");
        }
    }

    private static void runQuery(String queryString, String type, Consumer<Document> documentConsumer) {
        runQueryWithConfig(queryString, type, false, documentConsumer);
    }

    private static void runFuzzyQuery(String queryString, String type, Consumer<Document> documentConsumer) {
        runQueryWithConfig(queryString, type, true, documentConsumer);
    }


    private static void findWithQuery(String queryString, String type, boolean isFuzzy, Consumer<Document> documentConsumer) {
        if (isFuzzy) {
            runFuzzyQuery(queryString, type, documentConsumer);
        } else {
            runQuery(queryString, type, documentConsumer);
        }
    }

    public static void findStringConstants(String queryString, boolean isFuzzy) {
        findWithQuery(queryString, "StringConstant", isFuzzy);
    }

    public static void findClass(String className, boolean isFuzzy) {
        findWithQuery(className, "Class", isFuzzy);
    }

    public static void findMethod(String methodName, boolean isFuzzy) {
        findWithQuery(methodName, "Method", isFuzzy);
    }

    public static void findInterface(String interfaceName, boolean isFuzzy) {
        findWithQuery(interfaceName, "Interface", isFuzzy);
    }

    public static void findField(String fieldName, boolean isFuzzy) {
        findWithQuery(fieldName, "Field", isFuzzy);
    }

    public static void findLocalVariable(String variableName, boolean isFuzzy) {
        findWithQuery(variableName, "LocalVariable", isFuzzy);
    }

    public static void findLiteral(String literalValue, String type, boolean isFuzzy) {
        findWithQuery(literalValue, type, isFuzzy);
    }

    private static void findWithQuery(String queryString, String type, boolean isFuzzy) {
        findWithQuery(queryString, type, isFuzzy, doc -> {
            String content = doc.get("content");
            String file = doc.get("file");
            String line = doc.get("line");
            String logMessage = String.format("%s: %s, Файл: %s, Строка: %s", type, content, file, line);
            logger.info(logMessage);
            logBuilder.append(logMessage).append("\n");
        });
    }

    public static void main(String[] args) {
        indexJavaSources("src");

        findStringConstants("Test String", false);
        findClass("Stringdonstantuery", true);
        findMethod("toString", false);
        findInterface("TestInterface", false);
        findField("testField", false);
        findLocalVariable("content", false);
        findLiteral("100000", "IntegerLiteral", false);
        findLiteral("3.13F", "FloatLiteral", false);
        findLiteral("true", "BooleanLiteral", false);
        findLiteral("}", "CharLiteral", false);
        findLiteral("Hello, Lucene!", "StringLiteral", true);
    }
}
