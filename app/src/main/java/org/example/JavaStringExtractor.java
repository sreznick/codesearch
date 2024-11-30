package org.example;

import java.util.ArrayList;
import java.util.List;

public class JavaStringExtractor extends JavaBaseListener {
    private List<String> strings = new ArrayList<>();
    @Override
    public void enterLiteral(JavaParser.LiteralContext ctx) {
        if (ctx.StringLiteral() != null) {
            System.out.println("Found string: " + ctx.StringLiteral().getText());
            strings.add(ctx.StringLiteral().getText());
        }
    }

    public List<String> getStrings() {
        return strings;
    }
}
