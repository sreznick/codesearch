package org.codesearch.java;

import org.antlr.v4.runtime.ParserRuleContext;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityAttributes;
import org.codesearch.core.EntityKind;
import org.codesearch.core.EntityLocation;
import org.codesearch.grammar.JavaParser;
import org.codesearch.grammar.JavaParserBaseListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class JavaCallExtractor extends JavaParserBaseListener {
    private final String filePath;
    private final List<CodeEntity> calls = new ArrayList<>();
    private final Deque<String> types = new ArrayDeque<>();
    private final Deque<String> methods = new ArrayDeque<>();

    JavaCallExtractor(String filePath) {
        this.filePath = filePath;
    }

    List<CodeEntity> calls() {
        return calls;
    }

    @Override
    public void enterClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
        types.push(ctx.identifier().getText());
    }

    @Override
    public void exitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
        types.pop();
    }

    @Override
    public void enterInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        types.push(ctx.identifier().getText());
    }

    @Override
    public void exitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        types.pop();
    }

    @Override
    public void enterEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
        types.push(ctx.identifier().getText());
    }

    @Override
    public void exitEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
        types.pop();
    }

    @Override
    public void enterRecordDeclaration(JavaParser.RecordDeclarationContext ctx) {
        types.push(ctx.identifier().getText());
    }

    @Override
    public void exitRecordDeclaration(JavaParser.RecordDeclarationContext ctx) {
        types.pop();
    }

    @Override
    public void enterMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        methods.push(ctx.identifier().getText());
    }

    @Override
    public void exitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        methods.pop();
    }

    @Override
    public void enterInterfaceCommonBodyDeclaration(JavaParser.InterfaceCommonBodyDeclarationContext ctx) {
        methods.push(ctx.identifier().getText());
    }

    @Override
    public void exitInterfaceCommonBodyDeclaration(JavaParser.InterfaceCommonBodyDeclarationContext ctx) {
        methods.pop();
    }

    @Override
    public void enterConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
        methods.push(ctx.identifier().getText());
    }

    @Override
    public void exitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
        methods.pop();
    }

    @Override
    public void enterCompactConstructorDeclaration(JavaParser.CompactConstructorDeclarationContext ctx) {
        methods.push(ctx.identifier().getText());
    }

    @Override
    public void exitCompactConstructorDeclaration(JavaParser.CompactConstructorDeclarationContext ctx) {
        methods.pop();
    }

    @Override
    public void enterMethodCall(JavaParser.MethodCallContext ctx) {
        if (ctx.identifier() == null) {
            return;
        }
        String qualifier = ctx.getParent() instanceof JavaParser.MemberReferenceExpressionContext member
                ? member.expression().getText()
                : null;
        add(ctx.identifier().getText(), qualifier, "method", ctx);
    }

    @Override
    public void enterObjectCreationExpression(JavaParser.ObjectCreationExpressionContext ctx) {
        JavaParser.CreatorContext creator = ctx.creator();
        if (creator.classCreatorRest() == null || creator.createdName().identifier().isEmpty()) {
            return;
        }
        List<JavaParser.IdentifierContext> names = creator.createdName().identifier();
        String qualifier = names.size() > 1
                ? String.join(".", names.subList(0, names.size() - 1).stream().map(JavaParser.IdentifierContext::getText).toList())
                : null;
        add(names.getLast().getText(), qualifier, "constructor", ctx);
    }

    @Override
    public void enterMethodReferenceExpression(JavaParser.MethodReferenceExpressionContext ctx) {
        if (ctx.identifier() == null) {
            return;
        }
        String qualifier = ctx.expression() != null ? ctx.expression().getText()
                : ctx.typeType() != null ? JavaSyntax.typeText(ctx.typeType()) : null;
        add(ctx.identifier().getText(), qualifier, "reference", ctx);
    }

    private void add(String name, String qualifier, String callKind, ParserRuleContext ctx) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(EntityAttributes.CALL_KIND, callKind);
        if (qualifier != null && !qualifier.isBlank()) {
            attributes.put(EntityAttributes.CALL_QUALIFIER, qualifier);
        }
        if (!methods.isEmpty() && !types.isEmpty()) {
            attributes.put(EntityAttributes.CONTAINER_KIND, "method");
            attributes.put(EntityAttributes.CONTAINER_NAME, types.peek() + "." + methods.peek());
        } else if (!types.isEmpty()) {
            attributes.put(EntityAttributes.CONTAINER_KIND, "class");
            attributes.put(EntityAttributes.CONTAINER_NAME, types.peek());
        }
        calls.add(new CodeEntity(
                EntityKind.CALL,
                name,
                new EntityLocation(filePath, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()),
                JavaLanguagePlugin.LANGUAGE,
                null,
                attributes
        ));
    }
}
