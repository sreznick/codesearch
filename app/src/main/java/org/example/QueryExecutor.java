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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.example.JavaSourceIndexer.indexJavaSources;

/**
 * Класс для выполнения запросов поиска по индексированным данным.
 * Поддерживает различные типы запросов, включая строки, классы, методы, интерфейсы и литералы.
 * Обрабатывает поисковые запросы с возможностью учета регистра и работы с неточными совпадениями.
 */
public class QueryExecutor {
    private static final Logger logger = LogManager.getLogger();

    public static StringBuilder logBuilder = new StringBuilder();

    private static final ReentrantLock lock = new ReentrantLock();

    private static void runQueryWithConfig(String queryString, String type, boolean isFuzzy, boolean isCaseSensitive, Consumer<Document> documentConsumer) {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try (MMapDirectory directory = new MMapDirectory(Paths.get("index"));
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);

            String field = isCaseSensitive ? "content" : "content_lowercase";

            Query query = new BooleanQuery.Builder()
                    .add(isFuzzy ? new FuzzyQuery(new Term(field, isCaseSensitive ? queryString : queryString.toLowerCase()), 2)
                            : new TermQuery(new Term(field, isCaseSensitive ? queryString : queryString.toLowerCase())), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("type", type)), BooleanClause.Occur.MUST)
                    .build();

            String queryTypeMessage = isFuzzy ? " (с неточностями)" : "";
            String logMessage = "Запрос на " + type + queryTypeMessage + ": " + queryString + " (учет регистра: " + isCaseSensitive + ")";
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

    private static void runQuery(String queryString, String type, boolean isCaseSensitive, Consumer<Document> documentConsumer) {
        runQueryWithConfig(queryString, type, false, isCaseSensitive, documentConsumer);
    }

    private static void runFuzzyQuery(String queryString, String type, boolean isCaseSensitive, Consumer<Document> documentConsumer) {
        runQueryWithConfig(queryString, type, true, isCaseSensitive, documentConsumer);
    }

    private static void findWithQuery(String queryString, String type, boolean isFuzzy, boolean isCaseSensitive, Consumer<Document> documentConsumer) {
        if (isFuzzy) {
            runFuzzyQuery(queryString, type, isCaseSensitive, documentConsumer);
        } else {
            runQuery(queryString, type, isCaseSensitive, documentConsumer);
        }
    }

    public static void findStringConstants(String queryString, boolean isFuzzy, boolean isCaseSensitive, int maxMatches, String directoryToSearch) {
        findWithQuery(queryString, "StringConstant", isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
    }

    public static void findClass(String className, boolean isFuzzy, boolean isCaseSensitive, int maxMatches, String directoryToSearch) {
        findWithQuery(className, "Class", isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
    }

    public static void findMethod(String methodName, boolean isFuzzy, boolean isCaseSensitive, int maxMatches, String directoryToSearch) {
        findWithQuery(methodName, "Method", isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
    }

    public static void findInterface(String interfaceName, boolean isFuzzy, boolean isCaseSensitive, int maxMatches, String directoryToSearch) {
        findWithQuery(interfaceName, "Interface", isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
    }

    public static void findField(String fieldName, boolean isFuzzy, boolean isCaseSensitive, int maxMatches, String directoryToSearch) {
        findWithQuery(fieldName, "Field", isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
    }

    public static void findLocalVariable(String variableName, boolean isFuzzy, boolean isCaseSensitive, int maxMatches, String directoryToSearch) {
        findWithQuery(variableName, "LocalVariable", isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
    }

    public static void findLiteral(String literalValue, String type, boolean isFuzzy, boolean isCaseSensitive, int maxMatches, String directoryToSearch) {
        findWithQuery(literalValue, type, isFuzzy, isCaseSensitive, maxMatches, directoryToSearch);
    }

    private static void findWithQuery(String queryString, String type, boolean isFuzzy, boolean isCaseSensitive, int maxMatches, String directoryToSearch) {
        AtomicInteger matchCount = new AtomicInteger(0);

        findWithQuery(queryString, type, isFuzzy, isCaseSensitive, doc -> {
            String file = doc.get("file");

            if (directoryToSearch != null && !file.contains(directoryToSearch)) {
                return;
            }

            if (matchCount.get() >= maxMatches) {
                return;
            }

            lock.lock();
            try {
                if (matchCount.get() >= maxMatches) {
                    return;
                }

                String content = doc.get("content");
                String line = doc.get("line");
                String logMessage = String.format("%s: %s, Файл: %s, Строка: %s", type, content, file, line);

                if (type.equals("LocalVariable") || type.equals("Field")) {
                    String varType = doc.get("varType");
                    logMessage = String.format("%s: %s, Тип: %s, Файл: %s, Строка: %s", type, content, varType, file, line);
                }

                logger.info(logMessage);
                logBuilder.append(logMessage).append("\n");

                matchCount.incrementAndGet();
            } finally {
                lock.unlock();
            }
        });
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        indexJavaSources("src");

        findStringConstants("Test String", false, false, 200, null);
        findClass("Stringdonstantuery", true, false, 200, null);
        findMethod("toString", false, false, 200, null);
        findInterface("TestInterface", false, false, 200, null);
        findField("testField", false, false, 200, null);
        findLocalVariable("content", false, false, 200, null);
        findLiteral("100000", "IntegerLiteral", false, false, 200, null);
        findLiteral("3.13F", "FloatLiteral", false, false, 200, null);
        findLiteral("true", "BooleanLiteral", false, false, 200, null);
        findLiteral("}", "CharLiteral", false, false, 200, null);
        findLiteral("Hello, Lucene!", "StringLiteral", true, false, 200, null);
    }
}
