package org.example.extractors;

import org.example.JavaBaseListener;
import org.example.JavaParser;

import java.util.ArrayList;
import java.util.List;

public class JavaStringExtractor extends JavaBaseListener {
    private List<ExtractedString> strings = new ArrayList<>();

    private String currentFile;

    public void setCurrentFile(String fileName) {
        this.currentFile = fileName;
    }

    @Override
    public void enterVariableDeclarator(JavaParser.VariableDeclaratorContext ctx) {
        if (ctx.variableInitializer() != null
                && ctx.variableInitializer().getText().startsWith("\"")) {
            String stringLiteral = ctx.variableInitializer().getText().replace("\"", "");
            int lineNumber = ctx.getStart().getLine();
            strings.add(new ExtractedString(currentFile, lineNumber, stringLiteral));
        }
    }

    public List<ExtractedString> getStrings() {
        return strings;
    }

    public static class ExtractedString {
        private final String file;
        private final int line;
        private final String value;

        public ExtractedString(String file, int line, String value) {
            this.file = file;
            this.line = line;
            this.value = value;
        }

        public String getFile() {
            return file;
        }

        public int getLine() {
            return line;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "File: " + file + ", Line: " + line + ", Value: " + value;
        }
    }
}
