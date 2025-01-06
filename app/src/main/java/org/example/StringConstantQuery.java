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
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.example.JavaSourceIndexer.indexJavaSources;

public class StringConstantQuery {
    private static final Logger logger = LogManager.getLogger();

    private static void findBoolLiteralsTest(Boolean flag) {
        return;
    }

    private static void runQuery(String queryString, String type, Consumer<Document> documentConsumer) {
        String str = "Hello, Lucene!";
        findBoolLiteralsTest(true);

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());


        try (MMapDirectory directory = new MMapDirectory(Paths.get("index"));
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);

            BooleanQuery query = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term("content", queryString)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("type", type)), BooleanClause.Occur.MUST)
                    .build();

            logger.info("Запрос на {}: {}", type, queryString);

            TopDocs results = searcher.search(query, 100000);
            logger.info("Найдено совпадений: {}", results.totalHits.toString());

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                executor.submit(() -> {
                    try {
                        Document doc = searcher.storedFields().document(scoreDoc.doc);
                        documentConsumer.accept(doc);
                    } catch (IOException e) {
                        logger.error("Ошибка при обработке документа: {}", e.getMessage(), e);
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (IOException | InterruptedException e) {
            logger.error("Ошибка при выполнении запроса: {}", e.getMessage(), e);
        }
    }

    public static void findStringConstants(String queryString) {
        runQuery(queryString, "StringLiteral", doc -> {
            String content = doc.get("content");
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Строка: {}, Файл: {}, Строка: {}", content, file, line);
        });
    }

    public static void findClass(String className) {
        runQuery(className, "Class", doc -> {
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Класс: {}, Файл: {}, Строка: {}", className, file, line);
        });
    }

    public static void findMethod(String methodName) {
        runQuery(methodName, "Method", doc -> {
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Метод: {}, Файл: {}, Строка: {}", methodName, file, line);
        });
    }

    public static void findInterface(String interfaceName) {
        runQuery(interfaceName, "Interface", doc -> {
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Интерфейс: {}, Файл: {}, Строка: {}", interfaceName, file, line);
        });
    }

    public static void findField(String fieldName) {
        runQuery(fieldName, "Field", doc -> {
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Поле: {}, Файл: {}, Строка: {}", fieldName, file, line);
        });
    }

    public static void findLocalVariable(String variableName) {
        runQuery(variableName, "LocalVariable", doc -> {
            String file = doc.get("file");
            String line = doc.get("line");
            logger.info("Переменная: " + variableName + ", Файл: " + file + ", Строка: " + line);
        });
    }

    public static void findLiteral(String literalValue, String type) {
        runQuery(literalValue, type, doc -> {
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
        findStringConstants("Hello, Lucene!");
        findClass("StringConstantQuery");
        findMethod("extractWithWalker");
        findInterface("ExtractResultCallback");
        findField("logger");
        findLocalVariable("file");
        findLiteral("10", "IntegerLiteral");
        findLiteral("3.13F", "FloatLiteral");
        findLiteral("true", "BooleanLiteral");
        findLiteral("}", "CharLiteral");
        findLiteral("file", "StringLiteral");
    }
}
