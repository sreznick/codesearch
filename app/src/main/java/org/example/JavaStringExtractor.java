package org.example;

import java.util.ArrayList;
import java.util.List;

public class JavaStringExtractor extends JavaBaseListener {
    private List<String> strings = new ArrayList<>();

    @Override
    public void enterVariableDeclarator(JavaParser.VariableDeclaratorContext ctx) {
        if (ctx.variableInitializer() != null
                && ctx.variableInitializer().getText().startsWith("\"")) {
            String stringLiteral = ctx.variableInitializer().getText();
            strings.add(stringLiteral.replace("\"", ""));
        }
    }

    public List<String> getStrings() {
        return strings;
    }
}
