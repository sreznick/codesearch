package org.example.extractors;

import org.antlr.v4.runtime.Token;
import org.example.JavaBaseListener;
import org.example.JavaParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс анализирует Java-код с помощью ANTLR
 * и извлекает информацию об объявленных полях.
 * Он сохраняет имя файла, строку, где встретился определенный литерал.
 */
public class JavaLiteralExtractor extends JavaBaseListener {

    private final List<ExtractedLiteral> literals = new ArrayList<>();
    private String currentFile;

    public void setCurrentFile(String file) {
        this.currentFile = file;
    }

    public List<ExtractedLiteral> getLiterals() {
        return literals;
    }

    @Override
    public void enterLiteral(JavaParser.LiteralContext ctx) {
        if (ctx.getChildCount() == 0) {
            return;
        }

        Token token = ctx.getStart();
        String type;
        String value = ctx.getText();

        if (ctx.IntegerLiteral() != null) {
            type = "IntegerLiteral";
        } else if (ctx.FloatingPointLiteral() != null) {
            type = "FloatLiteral";
        } else if (ctx.BooleanLiteral() != null) {
            type = "BooleanLiteral";
        } else if (ctx.CharacterLiteral() != null) {
            type = "CharLiteral";
            value = value.substring(1, value.length() - 1);
        } else if (ctx.StringLiteral() != null) {
            type = "StringLiteral";
            value = value.substring(1, value.length() - 1);
        } else {
            return;
        }

        literals.add(new ExtractedLiteral(currentFile, token.getLine(), value, type));
    }

    public static class ExtractedLiteral {
        private final String file;
        private final int line;
        private final String value;
        private final String type;

        public ExtractedLiteral(String file, int line, String value, String type) {
            this.file = file;
            this.line = line;
            this.value = value;
            this.type = type;
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

        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return "ExtractedLiteral{" +
                    "file='" + file + '\'' +
                    ", line=" + line +
                    ", value='" + value + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }
    }
}
