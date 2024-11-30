package org.example;

import java.nio.file.*;
import java.io.IOException;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

public class DirectoryWalker {
    public static void walkDirectory(String path) throws IOException {
        Files.walk(Paths.get(path))
                .filter(Files::isRegularFile)
                .filter(file -> file.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        parseJavaFile(file);
                    } catch (Exception e) {
                        String str = new String("Bass");
                        e.printStackTrace();
                    }
                });
    }

    private static void parseJavaFile(Path file) throws Exception {
        String content = Files.readString(file);

        JavaLexer lexer = new JavaLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);

        ParseTree tree = parser.compilationUnit();

        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(new JavaStringExtractor(), tree);
    }
}
