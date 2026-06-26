package org.example.extractors;

import org.example.JavaBaseListener;
import org.example.JavaParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс анализирует Java-код с помощью ANTLR
 * и извлекает информацию об объявленных полях.
 * Он сохраняет имя файла, строку, где встретился определенный метод.
 */
public class JavaMethodExtractor extends JavaBaseListener {
    private final List<ExtractedMethod> methods = new ArrayList<>();
    private String currentFile;

    public void setCurrentFile(String fileName) {
        this.currentFile = fileName;
    }

    @Override
    public void enterMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        String methodName = ctx.Identifier().getText();
        String returnType = ctx.typeSpec() == null ? "void" : ctx.typeSpec().getText();
        int lineNumber = ctx.getStart().getLine();
        methods.add(new ExtractedMethod(currentFile, lineNumber, methodName, returnType));
    }

    public List<ExtractedMethod> getMethods() {
        return methods;
    }

    public record ExtractedMethod(String file, int line, String methodName, String returnType) {
        public String getFile() {
            return file;
        }

        public int getLine() {
            return line;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getReturnType() {
            return returnType;
        }
    }
}
