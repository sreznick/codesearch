package org.example.extractors;

import org.example.JavaBaseListener;
import org.example.JavaParser;

import java.util.ArrayList;
import java.util.List;

public class JavaMethodExtractor extends JavaBaseListener {
    private final List<ExtractedMethod> methods = new ArrayList<>();
    private String currentFile;

    public void setCurrentFile(String fileName) {
        this.currentFile = fileName;
    }

    @Override
    public void enterMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        String methodName = ctx.Identifier().getText();
        int lineNumber = ctx.getStart().getLine();
        methods.add(new ExtractedMethod(currentFile, lineNumber, methodName));
    }

    public List<ExtractedMethod> getMethods() {
        return methods;
    }

    public static class ExtractedMethod {
        private final String file;
        private final int line;
        private final String methodName;

        public ExtractedMethod(String file, int line, String methodName) {
            this.file = file;
            this.line = line;
            this.methodName = methodName;
        }

        public String getFile() {
            return file;
        }

        public int getLine() {
            return line;
        }

        public String getMethodName() {
            return methodName;
        }

        @Override
        public String toString() {
            return "File: " + file + ", Line: " + line + ", Method: " + methodName;
        }
    }
}
