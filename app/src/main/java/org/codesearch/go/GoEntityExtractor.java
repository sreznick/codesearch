package org.codesearch.go;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.EntityLocation;
import org.example.GoParserBaseListener;
import org.example.GoLexer;
import org.example.GoParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GoEntityExtractor {
    public List<CodeEntity> extractEntities(Path file) throws IOException {
        String content = Files.readString(file);
        GoLexer lexer = new GoLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        GoParser parser = new GoParser(tokens);

        GoCollector collector = new GoCollector(file.toString());
        ParseTreeWalker.DEFAULT.walk(collector, parser.sourceFile());
        return collector.entities();
    }

    private static final class GoCollector extends GoParserBaseListener {
        private final String filePath;
        private final List<CodeEntity> entities = new ArrayList<>();

        private GoCollector(String filePath) {
            this.filePath = filePath;
        }

        private List<CodeEntity> entities() {
            return entities;
        }

        @Override
        public void enterPackageClause(GoParser.PackageClauseContext ctx) {
            add(EntityKind.PACKAGE, ctx.IDENTIFIER().getText(), ctx, null);
        }

        @Override
        public void enterImportSpec(GoParser.ImportSpecContext ctx) {
            Map<String, String> attributes = ctx.IDENTIFIER() == null
                    ? Map.of()
                    : Map.of("alias", ctx.IDENTIFIER().getText());
            add(EntityKind.IMPORT, cleanString(ctx.importPath().string_().getText()), ctx, null, attributes);
        }

        @Override
        public void enterFunctionDecl(GoParser.FunctionDeclContext ctx) {
            add(EntityKind.FUNCTION, ctx.IDENTIFIER().getText(), ctx, resultType(ctx.signature()));
        }

        @Override
        public void enterMethodDecl(GoParser.MethodDeclContext ctx) {
            add(EntityKind.METHOD, ctx.IDENTIFIER().getText(), ctx, resultType(ctx.signature()), Map.of("receiver", ctx.receiver().getText()));
        }

        @Override
        public void enterTypeSpec(GoParser.TypeSpecContext ctx) {
            if (ctx.typeDef() == null) {
                return;
            }

            String name = ctx.typeDef().IDENTIFIER().getText();
            GoParser.Type_Context type = ctx.typeDef().type_();
            if (isStruct(type)) {
                add(EntityKind.STRUCT, name, ctx, "struct");
            } else if (isInterface(type)) {
                add(EntityKind.INTERFACE, name, ctx, "interface");
            }
        }

        @Override
        public void enterFieldDecl(GoParser.FieldDeclContext ctx) {
            if (ctx.identifierList() != null) {
                String declaredType = textOrNull(ctx.type_());
                for (var identifier : ctx.identifierList().IDENTIFIER()) {
                    add(EntityKind.FIELD, identifier.getText(), ctx, declaredType);
                }
                return;
            }

            if (ctx.embeddedField() != null) {
                add(EntityKind.FIELD, ctx.embeddedField().typeName().getText(), ctx, ctx.embeddedField().getText());
            }
        }

        @Override
        public void enterVarSpec(GoParser.VarSpecContext ctx) {
            String declaredType = textOrNull(ctx.type_());
            for (var identifier : ctx.identifierList().IDENTIFIER()) {
                add(EntityKind.VARIABLE, identifier.getText(), ctx, declaredType);
            }
        }

        @Override
        public void enterConstSpec(GoParser.ConstSpecContext ctx) {
            String declaredType = textOrNull(ctx.type_());
            for (var identifier : ctx.identifierList().IDENTIFIER()) {
                add(EntityKind.CONSTANT, identifier.getText(), ctx, declaredType);
            }
        }

        @Override
        public void enterBasicLit(GoParser.BasicLitContext ctx) {
            if (ctx.string_() != null) {
                add(EntityKind.STRING_LITERAL, cleanString(ctx.string_().getText()), ctx, null);
            } else if (ctx.integer() != null) {
                add(EntityKind.INTEGER_LITERAL, ctx.integer().getText(), ctx, null);
            } else if (ctx.FLOAT_LIT() != null) {
                add(EntityKind.FLOAT_LITERAL, ctx.FLOAT_LIT().getText(), ctx, null);
            }
        }

        private boolean isStruct(GoParser.Type_Context type) {
            return type != null && type.typeLit() != null && type.typeLit().structType() != null;
        }

        private boolean isInterface(GoParser.Type_Context type) {
            return type != null && type.typeLit() != null && type.typeLit().interfaceType() != null;
        }

        private String resultType(GoParser.SignatureContext signature) {
            if (signature == null || signature.result() == null) {
                return null;
            }
            return signature.result().getText();
        }

        private String textOrNull(ParserRuleContext ctx) {
            if (ctx == null) {
                return null;
            }
            String text = ctx.getText();
            return text.isBlank() ? null : text;
        }

        private String cleanString(String value) {
            if (value == null || value.length() < 2) {
                return value;
            }
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '`' && last == '`')) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }

        private void add(EntityKind kind, String content, ParserRuleContext ctx, String declaredType) {
            add(kind, content, ctx, declaredType, Map.of());
        }

        private void add(EntityKind kind, String content, ParserRuleContext ctx, String declaredType, Map<String, String> attributes) {
            if (content == null || content.isBlank()) {
                return;
            }

            entities.add(new CodeEntity(
                    kind,
                    content,
                    new EntityLocation(filePath, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()),
                    GoLanguageModule.LANGUAGE,
                    declaredType,
                    attributes
            ));
        }
    }
}
