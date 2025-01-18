package org.example.extractors;

import org.example.JavaBaseListener;
import org.example.JavaParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс анализирует Java-код с помощью ANTLR
 * и извлекает информацию об объявленных полях.
 * Он сохраняет имя файла, строку, где встретился определенный интерфейс.
 */
public class JavaInterfaceExtractor extends JavaBaseListener {
    private final List<ExtractedInterface> interfaces = new ArrayList<>();
    private String currentFile;

    public void setCurrentFile(String fileName) {
        this.currentFile = fileName;
    }

    @Override
    public void enterInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        String interfaceName = ctx.Identifier().getText();
        int lineNumber = ctx.getStart().getLine();
        interfaces.add(new ExtractedInterface(currentFile, lineNumber, interfaceName));
    }

    public List<ExtractedInterface> getInterfaces() {
        return interfaces;
    }

    public static class ExtractedInterface {
        private final String file;
        private final int line;
        private final String interfaceName;

        public ExtractedInterface(String file, int line, String interfaceName) {
            this.file = file;
            this.line = line;
            this.interfaceName = interfaceName;
        }

        public String getFile() {
            return file;
        }

        public int getLine() {
            return line;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        @Override
        public String toString() {
            return "File: " + file + ", Line: " + line + ", Interface: " + interfaceName;
        }
    }
}
