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

    private static void findBoolLiteralsTest(Boolean flag) {
        return;
    }

    private static void runQuery(String queryString, String type, Consumer<Document> documentConsumer) {
        String str = "Hello, Lucene";
        findBoolLiteralsTest(true);

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try (MMapDirectory directory = new MMapDirectory(Paths.get("index"));
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            Query query = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term("content", queryString)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("type", type)), BooleanClause.Occur.MUST)
                    .build();

            logger.info("Запрос на {}: {}", type, queryString);

            TopDocs results = searcher.search(query, 100000);
            logger.info("Найдено совпадений: {}", results.totalHits.toString().substring(0, Math.max(0, results.totalHits.toString().length() - 4)));

            processResults(executor, results, searcher, documentConsumer);

        } catch (IOException e) {
            logger.error("Ошибка при выполнении запроса: {}", e.getMessage(), e);
        }
    }

    private static void runFuzzyQuery(String queryString, String type, Consumer<Document> documentConsumer) {
        try (MMapDirectory directory = new MMapDirectory(Paths.get("index"));
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);

            Query fuzzyQuery = new BooleanQuery.Builder()
                    .add(new FuzzyQuery(new Term("content", queryString), 2), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("type", type)), BooleanClause.Occur.MUST)
                    .build();

            logger.info("Запрос (с неточностями) на {}: {}", type, queryString);

            TopDocs results = searcher.search(fuzzyQuery, 100000);
            logger.info("Найдено совпадений: {}", results.totalHits.toString());

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                documentConsumer.accept(doc);
            }

        } catch (IOException e) {
            logger.error("Ошибка при выполнении нечеткого запроса: {}", e.getMessage(), e);
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

                    if (processedResults.add(String.format("Литерал: %s, Файл: %s, Строка: %s", content, file, line))) {
                        documentConsumer.accept(doc);
                    }
                } catch (IOException e) {
                    logger.error("Ошибка при обработке документа: {}", e.getMessage(), e);
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            logger.error("Ошибка при ожидании завершения обработки: {}", e.getMessage(), e);
        }
    }


    private static void findWithQuery(String queryString, String type, boolean isFuzzy, Consumer<Document> documentConsumer) {
        if (isFuzzy) {
            runFuzzyQuery(queryString, type, documentConsumer);
        } else {
            runQuery(queryString, type, documentConsumer);
        }
    }

    public static void findStringConstants(String queryString, boolean isFuzzy) {
        findWithQuery(queryString, "StringConstant", isFuzzy, doc -> {
            String content = doc.get("content");
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Строка: {}, Файл: {}, Строка: {}", content, file, line);
        });
    }

    public static void findClass(String className, boolean isFuzzy) {
        findWithQuery(className, "Class", isFuzzy, doc -> {
            String content = doc.get("content");
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Класс: {}, Файл: {}, Строка: {}", content, file, line);
        });
    }

    public static void findMethod(String methodName, boolean isFuzzy) {
        findWithQuery(methodName, "Method", isFuzzy, doc -> {
            String content = doc.get("content");
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Метод: {}, Файл: {}, Строка: {}", content, file, line);
        });
    }

    public static void findInterface(String interfaceName, boolean isFuzzy) {
        findWithQuery(interfaceName, "Interface", isFuzzy, doc -> {
            String content = doc.get("content");
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Интерфейс: {}, Файл: {}, Строка: {}", content, file, line);
        });
    }

    public static void findField(String fieldName, boolean isFuzzy) {
        findWithQuery(fieldName, "Field", isFuzzy, doc -> {
            String content = doc.get("content");
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Поле: {}, Файл: {}, Строка: {}", content, file, line);
        });
    }

    public static void findLocalVariable(String variableName, boolean isFuzzy) {
        findWithQuery(variableName, "LocalVariable", isFuzzy, doc -> {
            String content = doc.get("content");
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Переменная: {}, Файл: {}, Строка: {}", content, file, line);
        });
    }

    public static void findLiteral(String literalValue, String type, boolean isFuzzy) {
        findWithQuery(literalValue, type, isFuzzy, doc -> {
            String content = doc.get("content");
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Литерал: {}, Файл: {}, Строка: {}", content, file, line);
        });
    }

    public static void main(String[] args) {
        //indexJavaSources("src");

        float a = 3.13F;
        boolean b = true;

        findStringConstants("Hello, Lucene!", true);
        findClass("Stringdonstantuery", true);
        findMethod("extractWithWalker", false);
        findInterface("ExtractResultCallback", false);
        findField("logger", false);
        findLocalVariable("str", false);
        findLiteral("100000", "IntegerLiteral", false);
        findLiteral("3.13F", "FloatLiteral", false);
        findLiteral("true", "BooleanLiteral", false);
        findLiteral("}", "CharLiteral", false);
        findLiteral("Hello, Lucene!", "StringLiteral", true);
    }
}
