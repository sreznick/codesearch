package org.example.extractors;

import org.example.JavaBaseListener;
import org.example.JavaParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс анализирует Java-код с помощью ANTLR
 * и извлекает информацию об объявленных полях.
 * Он сохраняет имя файла, строку, где встретилось определенное поле.
 */
public class JavaFieldExtractor extends JavaBaseListener {
    private final List<ExtractedField> fields = new ArrayList<>();
    private String currentFile;

    public void setCurrentFile(String file) {
        this.currentFile = file;
    }

    @Override
    public void enterFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
        String fieldName = ctx.variableDeclarators().variableDeclarator(0).variableDeclaratorId().getText();
        int line = ctx.start.getLine();
        fields.add(new ExtractedField(fieldName, currentFile, line));
    }

    public List<ExtractedField> getFields() {
        return fields;
    }

    public static class ExtractedField {
        private final String fieldName;
        private final String file;
        private final int line;

        public ExtractedField(String fieldName, String file, int line) {
            this.fieldName = fieldName;
            this.file = file;
            this.line = line;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getFile() {
            return file;
        }

        public int getLine() {
            return line;
        }

        @Override
        public String toString() {
            return "File: " + file + ", Line: " + line + ", Field: " + fieldName;
        }
    }
}
