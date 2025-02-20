package org.example.extractors;

import org.example.JavaBaseListener;
import org.example.JavaParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс анализирует Java-код с помощью ANTLR
 * и извлекает информацию об объявленных классах.
 * Он сохраняет имя файла, строку, где встретился определенный класс.
 */
public class JavaClassExtractor extends JavaBaseListener {
    private final List<ExtractedClass> classes = new ArrayList<>();
    private String currentFile;

    public void setCurrentFile(String fileName) {
        this.currentFile = fileName;
    }

    @Override
    public void enterClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
        String className = ctx.Identifier().getText();
        int lineNumber = ctx.getStart().getLine();
        classes.add(new ExtractedClass(currentFile, lineNumber, className));
    }

    public List<ExtractedClass> getClasses() {
        return classes;
    }

    public static class ExtractedClass {
        private final String file;
        private final int line;
        private final String className;

        public ExtractedClass(String file, int line, String className) {
            this.file = file;
            this.line = line;
            this.className = className;
        }

        public String getFile() {
            return file;
        }

        public int getLine() {
            return line;
        }

        public String getClassName() {
            return className;
        }

        @Override
        public String toString() {
            return "File: " + file + ", Line: " + line + ", Class: " + className;
        }
    }
}
