package org.codesearch.java;

import org.antlr.v4.runtime.tree.ParseTree;
import org.codesearch.grammar.JavaParser;

final class JavaSyntax {
    private JavaSyntax() {}

    static String typeText(JavaParser.TypeTypeContext type) {
        if (type == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < type.getChildCount(); i++) {
            ParseTree child = type.getChild(i);
            if (!(child instanceof JavaParser.AnnotationContext)) {
                text.append(child.getText());
            }
        }
        return text.toString();
    }

    static String typeText(JavaParser.TypeTypeOrVoidContext type) {
        if (type == null) {
            return null;
        }
        return type.VOID() != null ? "void" : typeText(type.typeType());
    }

    static String typeListText(JavaParser.TypeListContext typeList) {
        if (typeList == null) {
            return null;
        }
        return typeList.typeType().stream()
                .map(type -> JavaTypeResolver.searchableTypeName(typeText(type)))
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }
}
