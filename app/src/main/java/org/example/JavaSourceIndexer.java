package org.example;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.MMapDirectory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.antlr.v4.runtime.CommonTokenStream;
import org.example.extractors.*;

/**
 * Класс для индексации Java-исходных кодов.
 * Проходит по указанным каталогам, анализирует содержимое и создает индекс данных,
 * которые можно использовать для быстрого поиска.
 */
public class JavaSourceIndexer {

    private static final Logger logger = LogManager.getLogger();

    public static void indexJavaSources(String directoryPath) throws IOException, InterruptedException {
        Path indexDirectoryPath = Paths.get("index");

        deleteDirectoryRecursively(indexDirectoryPath);

        try (MMapDirectory directory = new MMapDirectory(indexDirectoryPath);
             StandardAnalyzer analyzer = new StandardAnalyzer();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {

            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            try {
                Files.walk(Paths.get(directoryPath))
                        .filter(Files::isRegularFile)
                        .filter(file -> file.toString().endsWith(".java"))
                        .forEach(file -> executor.submit(() -> {
                            try {
                                indexJavaFile(file, writer);
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

            } catch (IOException | InterruptedException e) {
                logger.error("Ошибка при чтении файлов для индексации.", e);
                throw e;
            } finally {
                if (!executor.isTerminated()) {
                    logger.warn("Индексация не была завершена.");
                    executor.shutdownNow();
                }
            }
            //logger.info("Индексация успешно завершена.");
        } catch (IOException | InterruptedException e) {
            logger.error("Ошибка при индексировании.", e);
            throw e;
        }
    }

    private static void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
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

    private static void indexJavaFile(Path file, IndexWriter writer) throws Exception {
        String content = Files.readString(file);

        JavaStringExtractor stringExtractor = new JavaStringExtractor();
        stringExtractor.setCurrentFile(file.toString());

        synchronized (writer) {
            List<JavaStringExtractor.ExtractedString> stringLiterals = extractStringLiterals(content, stringExtractor);
            for (JavaStringExtractor.ExtractedString str : stringLiterals) {
                Document doc = new Document();
                doc.add(new StringField("content", str.getValue(), StringField.Store.YES));
                doc.add(new StringField("content_lowercase", str.getValue().toLowerCase(), StringField.Store.NO));
                doc.add(new StringField("file", str.getFile(), StringField.Store.YES));
                doc.add(new StringField("line", String.valueOf(str.getLine()), StringField.Store.YES));
                doc.add(new StringField("type", "StringConstant", StringField.Store.YES));
                writer.addDocument(doc);
            }
        }

        JavaClassExtractor classExtractor = new JavaClassExtractor();
        classExtractor.setCurrentFile(file.toString());

        synchronized (writer) {
            List<JavaClassExtractor.ExtractedClass> classes = extractClasses(content, classExtractor);
            for (JavaClassExtractor.ExtractedClass cls : classes) {
                Document doc = new Document();
                doc.add(new StringField("content", cls.getClassName(), StringField.Store.YES));
                doc.add(new StringField("content_lowercase", cls.getClassName().toLowerCase(), StringField.Store.NO));
                doc.add(new StringField("file", cls.getFile(), StringField.Store.YES));
                doc.add(new StringField("line", String.valueOf(cls.getLine()), StringField.Store.YES));
                doc.add(new StringField("type", "Class", StringField.Store.YES));
                writer.addDocument(doc);
            }
        }

        JavaMethodExtractor methodExtractor = new JavaMethodExtractor();
        methodExtractor.setCurrentFile(file.toString());

        synchronized (writer) {
            List<JavaMethodExtractor.ExtractedMethod> methods = extractMethods(content, methodExtractor);
            for (JavaMethodExtractor.ExtractedMethod method : methods) {
                Document doc = new Document();
                doc.add(new StringField("content", method.getMethodName(), StringField.Store.YES));
                doc.add(new StringField("content_lowercase", method.getMethodName().toLowerCase(), StringField.Store.NO));
                doc.add(new StringField("file", method.getFile(), StringField.Store.YES));
                doc.add(new StringField("line", String.valueOf(method.getLine()), StringField.Store.YES));
                doc.add(new StringField("type", "Method", StringField.Store.YES));
                writer.addDocument(doc);
            }
        }

        JavaInterfaceExtractor interfaceExtractor = new JavaInterfaceExtractor();
        interfaceExtractor.setCurrentFile(file.toString());

        synchronized (writer) {
            List<JavaInterfaceExtractor.ExtractedInterface> interfaces = extractInterfaces(content, interfaceExtractor);
            for (JavaInterfaceExtractor.ExtractedInterface iface : interfaces) {
                Document doc = new Document();
                doc.add(new StringField("content", iface.getInterfaceName(), StringField.Store.YES));
                doc.add(new StringField("content_lowercase", iface.getInterfaceName().toLowerCase(), StringField.Store.NO));
                doc.add(new StringField("file", iface.getFile(), StringField.Store.YES));
                doc.add(new StringField("line", String.valueOf(iface.getLine()), StringField.Store.YES));
                doc.add(new StringField("type", "Interface", StringField.Store.YES));
                writer.addDocument(doc);
            }
        }

        JavaFieldExtractor fieldExtractor = new JavaFieldExtractor();
        fieldExtractor.setCurrentFile(file.toString());

        synchronized (writer) {
            List<JavaFieldExtractor.ExtractedField> fields = extractFields(content, fieldExtractor);
            for (JavaFieldExtractor.ExtractedField field : fields) {
                Document doc = new Document();
                doc.add(new StringField("content", field.getFieldName(), StringField.Store.YES));
                doc.add(new StringField("content_lowercase", field.getFieldName().toLowerCase(), StringField.Store.NO));
                doc.add(new StringField("file", field.getFile(), StringField.Store.YES));
                doc.add(new StringField("line", String.valueOf(field.getLine()), StringField.Store.YES));
                doc.add(new StringField("type", "Field", StringField.Store.YES));
                doc.add(new StringField("varType", field.getType(), Field.Store.YES));
                writer.addDocument(doc);
            }
        }

        JavaLocalVariableExtractor localVarExtractor = new JavaLocalVariableExtractor();
        localVarExtractor.setCurrentFile(file.toString());

        synchronized (writer) {
            List<JavaLocalVariableExtractor.ExtractedLocalVariable> localVariables = extractLocalVariables(content, localVarExtractor);
            for (JavaLocalVariableExtractor.ExtractedLocalVariable localVar : localVariables) {
                Document doc = new Document();
                doc.add(new StringField("content", localVar.getVariableName(), StringField.Store.YES));
                doc.add(new StringField("content_lowercase", localVar.getVariableName().toLowerCase(), StringField.Store.NO));
                doc.add(new StringField("file", localVar.getFile(), StringField.Store.YES));
                doc.add(new StringField("line", String.valueOf(localVar.getLine()), StringField.Store.YES));
                doc.add(new StringField("type", "LocalVariable", StringField.Store.YES));
                doc.add(new StringField("varType", localVar.getType(), Field.Store.YES));
                writer.addDocument(doc);
            }
        }

        JavaLiteralExtractor literalExtractor = new JavaLiteralExtractor();
        literalExtractor.setCurrentFile(file.toString());

        synchronized (writer) {
            List<JavaLiteralExtractor.ExtractedLiteral> literals = extractLiterals(content, literalExtractor);
            for (JavaLiteralExtractor.ExtractedLiteral literal : literals) {
                Document doc = new Document();
                doc.add(new StringField("content", literal.getValue(), StringField.Store.YES));
                doc.add(new StringField("content_lowercase", literal.getValue().toLowerCase(), StringField.Store.NO));
                doc.add(new StringField("file", literal.getFile(), StringField.Store.YES));
                doc.add(new StringField("line", String.valueOf(literal.getLine()), StringField.Store.YES));
                doc.add(new StringField("type", literal.getType(), StringField.Store.YES));
                writer.addDocument(doc);
            }
        }
    }

    private static <T> T extractWithWalker(String content, JavaBaseListener extractor, ExtractResultCallback<T> callback) {
        JavaLexer lexer = new JavaLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);

        ParseTree tree = parser.compilationUnit();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(extractor, tree);

        return callback.getResult(extractor);
    }

    @FunctionalInterface
    private interface ExtractResultCallback<T> {
        T getResult(JavaBaseListener extractor);
    }

    private static List<JavaStringExtractor.ExtractedString> extractStringLiterals(String content, JavaStringExtractor extractor) {
        return extractWithWalker(content, extractor, e -> ((JavaStringExtractor) e).getStrings());
    }

    private static List<JavaClassExtractor.ExtractedClass> extractClasses(String content, JavaClassExtractor extractor) {
        return extractWithWalker(content, extractor, e -> ((JavaClassExtractor) e).getClasses());
    }

    private static List<JavaMethodExtractor.ExtractedMethod> extractMethods(String content, JavaMethodExtractor extractor) {
        return extractWithWalker(content, extractor, e -> ((JavaMethodExtractor) e).getMethods());
    }

    private static List<JavaInterfaceExtractor.ExtractedInterface> extractInterfaces(String content, JavaInterfaceExtractor extractor) {
        return extractWithWalker(content, extractor, e -> ((JavaInterfaceExtractor) e).getInterfaces());
    }

    private static List<JavaFieldExtractor.ExtractedField> extractFields(String content, JavaFieldExtractor extractor) {
        return extractWithWalker(content, extractor, e -> ((JavaFieldExtractor) e).getFields());
    }

    private static List<JavaLocalVariableExtractor.ExtractedLocalVariable> extractLocalVariables(String content, JavaLocalVariableExtractor extractor) {
        return extractWithWalker(content, extractor, e -> ((JavaLocalVariableExtractor) e).getVariables());
    }

    private static List<JavaLiteralExtractor.ExtractedLiteral> extractLiterals(String content, JavaLiteralExtractor extractor) {
        return extractWithWalker(content, extractor, e -> ((JavaLiteralExtractor) e).getLiterals());
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        indexJavaSources("src");
    }
}
