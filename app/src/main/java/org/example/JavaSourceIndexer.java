package org.example;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.document.Document;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.antlr.v4.runtime.CommonTokenStream;

public class JavaSourceIndexer {

    private static final Logger logger = LogManager.getLogger();

    public static void indexJavaSources(String directoryPath) {
        try {
            Path indexPath = Paths.get("index");
            MMapDirectory directory = new MMapDirectory(indexPath);

            StandardAnalyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);

            IndexWriter writer = new IndexWriter(directory, config);

            Files.walk(Paths.get(directoryPath))
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            indexJavaFile(file, writer);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

            writer.close();
            logger.info("Индексация завершена");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void indexJavaFile(Path file, IndexWriter writer) throws Exception {
        String content = Files.readString(file);

        JavaStringExtractor extractor = new JavaStringExtractor();
        extractor.setCurrentFile(file.toString());

        List<JavaStringExtractor.ExtractedString> stringLiterals = extractStringLiterals(content, extractor);
        for (JavaStringExtractor.ExtractedString literal : stringLiterals) {
            Document doc = new Document();
            doc.add(new StringField("content", literal.getValue(), StringField.Store.YES));
            doc.add(new StringField("file", literal.getFile(), StringField.Store.YES));
            doc.add(new StringField("line", String.valueOf(literal.getLine()), StringField.Store.YES));
            writer.addDocument(doc);

            logger.info("Индексируем: " + literal);
        }
    }


    private static List<JavaStringExtractor.ExtractedString> extractStringLiterals(String content, JavaStringExtractor extractor) {
        JavaLexer lexer = new JavaLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);

        ParseTree tree = parser.compilationUnit();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(extractor, tree);
        String str = "Hello, Lucene!";

        return extractor.getStrings();
    }

    public static void main(String[] args) {
        //indexJavaSources("src");
    }
}
