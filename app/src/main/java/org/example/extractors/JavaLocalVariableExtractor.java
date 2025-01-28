package org.example.extractors;

import org.example.JavaBaseListener;
import org.example.JavaParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс анализирует Java-код с помощью ANTLR
 * и извлекает информацию об объявленных полях.
 * Он сохраняет имя файла, строку, где встретилась определенная локальная переменная.
 */
public class JavaLocalVariableExtractor extends JavaBaseListener {

    private final List<ExtractedLocalVariable> variables = new ArrayList<>();
    private String currentFile;

    public void setCurrentFile(String currentFile) {
        this.currentFile = currentFile;
    }

    public List<ExtractedLocalVariable> getVariables() {
        return variables;
    }

    @Override
    public void enterLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
        if (ctx.variableDeclarators() != null) {
            int line = ctx.start.getLine();
            String type = ctx.typeSpec().getText();
            ctx.variableDeclarators().variableDeclarator().forEach(varDeclarator -> {
                String varName = varDeclarator.variableDeclaratorId().getText();
                variables.add(new ExtractedLocalVariable(currentFile, line, varName, type));
            });
        }
    }

    public static class ExtractedLocalVariable {
        private final String file;
        private final int line;
        private final String variableName;

        private final String type;

        public ExtractedLocalVariable(String file, int line, String variableName, String type) {
            this.file = file;
            this.line = line;
            this.variableName = variableName;
            this.type = type;
        }

        public String getFile() {
            return file;
        }

        public int getLine() {
            return line;
        }

        public String getVariableName() {
            return variableName;
        }

        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return "File: " + file + ", Line: " + line + ", Variable: " + variableName;
        }
    }
}
