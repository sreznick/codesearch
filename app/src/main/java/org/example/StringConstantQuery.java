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

import static org.example.JavaSourceIndexer.indexJavaSources;

public class StringConstantQuery {
    private static final Logger logger = LogManager.getLogger();
    public static void runQueryOnStringConstants(String queryString) {
        try {
            MMapDirectory directory = new MMapDirectory(Paths.get("index"));
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);

            String str = "Hello, Lucene!";
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(new TermQuery(new Term("content", queryString)), BooleanClause.Occur.MUST);
            builder.add(new TermQuery(new Term("type", "StringLiteral")), BooleanClause.Occur.MUST);

            Query query = builder.build();

            logger.info("Запрос: " + queryString);

            TopDocs results = searcher.search(query, 10);
            logger.info("Найдено совпадений: " + results.totalHits);

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String content = doc.get("content");
                String file = doc.get("file");
                String line = doc.get("line");
                logger.info("Найдено: " + content + " Файл: " + file + ", Строка: " + line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void runQueryOnClass(String className) {
        try {
            MMapDirectory directory = new MMapDirectory(Paths.get("index"));
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);

            BooleanQuery query = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term("content", className)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("type", "Class")), BooleanClause.Occur.MUST)
                    .build();

            logger.info("Запрос: класс " + className);

            TopDocs results = searcher.search(query, 10);
            logger.info("Найдено классов: " + results.totalHits.toString());

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String file = doc.get("file");
                String line = doc.get("line");
                logger.info("Класс: " + className + ", Файл: " + file + ", Строка: " + line);
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void runQueryOnMethod(String methodName) {
        try {
            MMapDirectory directory = new MMapDirectory(Paths.get("index"));
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);

            BooleanQuery query = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term("content", methodName)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("type", "Method")), BooleanClause.Occur.MUST)
                    .build();

            logger.info("Запрос: метод " + methodName);

            TopDocs results = searcher.search(query, 10);
            logger.info("Найдено методов: " + results.totalHits);

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String file = doc.get("file");
                String line = doc.get("line");
                logger.info("Метод: " + methodName + ", Файл: " + file + ", Строка: " + line);
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void runQueryOnInterface(String interfaceName) {
        try {
            MMapDirectory directory = new MMapDirectory(Paths.get("index"));
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);

            BooleanQuery query = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term("content", interfaceName)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("type", "Interface")), BooleanClause.Occur.MUST)
                    .build();

            logger.info("Запрос: интерфейс " + interfaceName);

            TopDocs results = searcher.search(query, 10);
            logger.info("Найдено интерфейсов: " + results.totalHits.toString());

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String file = doc.get("file");
                String line = doc.get("line");
                logger.info("Интерфейс: " + interfaceName + ", Файл: " + file + ", Строка: " + line);
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void runQueryOnFields(String fieldName) {
        try {
            MMapDirectory directory = new MMapDirectory(Paths.get("index"));
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);

            BooleanQuery query = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term("content", fieldName)), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("type", "Field")), BooleanClause.Occur.MUST)
                    .build();

            logger.info("Запрос: поле " + fieldName);

            TopDocs results = searcher.search(query, 10);
            logger.info("Найдено полей: " + results.totalHits.toString());

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String type = doc.get("type");
                if (type.equals("Field")) {
                    String file = doc.get("file");
                    String line = doc.get("line");
                    logger.info("Поле: " + fieldName + ", Файл: " + file + ", Строка: " + line);
                }
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public static void main(String[] args) {
        indexJavaSources("src");
        runQueryOnStringConstants("Hello, Lucene!");
        runQueryOnClass("StringConstantQuery");
        runQueryOnMethod("indexJavaFile");
        runQueryOnInterface("ExtractResultCallback");
        runQueryOnFields("results");
    }
}
