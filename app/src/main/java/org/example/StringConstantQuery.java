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

            Query query = new TermQuery(new Term("content", queryString));
            logger.info("Запрос: " + queryString);

            TopDocs results = searcher.search(query, 10);
            String matchCount = results.totalHits.toString();
            logger.info("Найдено " + matchCount.substring(0, matchCount.length() - 5) + " совпадений");

            for (ScoreDoc scoreDoc : results.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                logger.info("Вот они: " + doc.get("content"));
            }
            String str = "Hello, Lucene!";
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        indexJavaSources("src");
        runQueryOnStringConstants("Hello, Lucene!");
    }
}
