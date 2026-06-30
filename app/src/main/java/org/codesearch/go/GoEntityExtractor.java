package org.codesearch.go;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityAttributes;
import org.codesearch.core.EntityExtractor;
import org.codesearch.core.EntityKind;
import org.codesearch.core.EntityLocation;
import org.example.GoLexer;
import org.example.GoParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GoEntityExtractor implements EntityExtractor {
    private static final Logger logger = LogManager.getLogger(GoEntityExtractor.class);

    private final GoTypeHierarchy typeHierarchy;

    public GoEntityExtractor() {
        this(GoTypeHierarchy.empty());
    }

    GoEntityExtractor(GoTypeHierarchy typeHierarchy) {
        this.typeHierarchy = typeHierarchy == null ? GoTypeHierarchy.empty() : typeHierarchy;
    }

    @Override
    public List<CodeEntity> extractEntities(Path file) throws IOException {
        String content = Files.readString(file);
        String filePath = file.toAbsolutePath().toString();

        try {
            GoLexer lexer = new GoLexer(CharStreams.fromString(content));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            GoParser parser = new GoParser(tokens);
            ParseTree tree = parser.sourceFile();

            GoEntityListener listener = new GoEntityListener(filePath, typeHierarchy);
            ParseTreeWalker.DEFAULT.walk(listener, tree);
            return listener.entities();
        } catch (RuntimeException e) {
            logger.debug("Не удалось разобрать Go-файл {}: {}", filePath, e.getMessage(), e);
            return List.of();
        }
    }

    static final class GoEntityListener extends org.example.GoParserBaseListener {
        private final String filePath;
        private final GoTypeHierarchy typeHierarchy;
        private final List<CodeEntity> entities = new ArrayList<>();
        private final Map<String, String> functionReturnTypes = new HashMap<>();

        GoEntityListener(String filePath, GoTypeHierarchy typeHierarchy) {
            this.filePath = filePath;
            this.typeHierarchy = typeHierarchy;
        }

        List<CodeEntity> entities() {
            return List.copyOf(entities);
        }

        @Override
        public void enterFunctionDecl(GoParser.FunctionDeclContext ctx) {
            if (ctx.IDENTIFIER() == null) {
                return;
            }
            String name = ctx.IDENTIFIER().getText();
            String returnType = extractReturnType(ctx.signature());
            if (returnType != null) {
                functionReturnTypes.put(name, returnType);
            }
            entities.add(typedEntity(EntityKind.METHOD, name, line(ctx), returnType));
        }

        @Override
        public void enterMethodDecl(GoParser.MethodDeclContext ctx) {
            if (ctx.IDENTIFIER() == null) {
                return;
            }
            String name = ctx.IDENTIFIER().getText();
            String returnType = extractReturnType(ctx.signature());
            entities.add(typedEntity(EntityKind.METHOD, name, line(ctx), returnType));
        }

        @Override
        public void enterTypeSpec(GoParser.TypeSpecContext ctx) {
            if (ctx.typeDef() == null || ctx.typeDef().IDENTIFIER() == null) {
                return;
            }
            String name = ctx.typeDef().IDENTIFIER().getText();
            if (ctx.typeDef().type_() != null && ctx.typeDef().type_().typeLit() != null) {
                GoParser.TypeLitContext typeLit = ctx.typeDef().type_().typeLit();
                if (typeLit.structType() != null) {
                    entities.add(entity(EntityKind.CLASS, name, line(ctx)));
                    indexStructFields(typeLit.structType(), line(ctx));
                    return;
                }
                if (typeLit.interfaceType() != null) {
                    entities.add(entity(EntityKind.INTERFACE, name, line(ctx)));
                    return;
                }
            }
            entities.add(entity(EntityKind.CLASS, name, line(ctx)));
        }

        @Override
        public void enterVarSpec(GoParser.VarSpecContext ctx) {
            if (ctx.identifierList() == null) {
                return;
            }
            String typeName = ctx.type_() == null ? null : extractTypeText(ctx.type_());
            for (var id : ctx.identifierList().IDENTIFIER()) {
                entities.add(typedEntity(EntityKind.FIELD, id.getText(), line(ctx), typeName));
            }
        }

        @Override
        public void enterShortVarDecl(GoParser.ShortVarDeclContext ctx) {
            if (ctx.identifierList() == null || ctx.expressionList() == null) {
                return;
            }
            List<GoParser.ExpressionContext> expressions = ctx.expressionList().expression();
            var identifiers = ctx.identifierList().IDENTIFIER();
            for (int i = 0; i < identifiers.size(); i++) {
                String name = identifiers.get(i).getText();
                String inferredType = i < expressions.size()
                        ? GoTypeResolver.inferTypeFromExpression(expressions.get(i).getText(), functionReturnTypes)
                        : null;
                entities.add(typedEntity(EntityKind.LOCAL_VARIABLE, name, line(ctx), inferredType));
            }
        }

        @Override
        public void enterConstSpec(GoParser.ConstSpecContext ctx) {
            if (ctx.identifierList() == null) {
                return;
            }
            String typeName = ctx.type_() == null ? null : extractTypeText(ctx.type_());
            for (var id : ctx.identifierList().IDENTIFIER()) {
                entities.add(typedEntity(EntityKind.FIELD, id.getText(), line(ctx), typeName));
            }
        }

        @Override
        public void enterBasicLit(GoParser.BasicLitContext ctx) {
            if (ctx.integer() != null) {
                entities.add(entity(EntityKind.INTEGER_LITERAL, ctx.integer().getText(), line(ctx)));
            } else if (ctx.string_() != null) {
                entities.add(entity(EntityKind.STRING_LITERAL, unquote(ctx.string_().getText()), line(ctx)));
            } else if (ctx.FLOAT_LIT() != null) {
                entities.add(entity(EntityKind.FLOAT_LITERAL, ctx.FLOAT_LIT().getText(), line(ctx)));
            }
        }

        private void indexStructFields(GoParser.StructTypeContext structType, int defaultLine) {
            if (structType.fieldDecl() == null) {
                return;
            }
            for (GoParser.FieldDeclContext fieldDecl : structType.fieldDecl()) {
                if (fieldDecl.identifierList() == null || fieldDecl.type_() == null) {
                    continue;
                }
                String typeName = extractTypeText(fieldDecl.type_());
                for (var id : fieldDecl.identifierList().IDENTIFIER()) {
                    entities.add(typedEntity(EntityKind.FIELD, id.getText(), line(fieldDecl, defaultLine), typeName));
                }
            }
        }

        private String extractReturnType(GoParser.SignatureContext signature) {
            if (signature == null || signature.result() == null) {
                return null;
            }
            GoParser.ResultContext result = signature.result();
            if (result.type_() != null) {
                return extractTypeText(result.type_());
            }
            if (result.parameters() != null && !result.parameters().parameterDecl().isEmpty()) {
                GoParser.ParameterDeclContext first = result.parameters().parameterDecl().getFirst();
                if (first.type_() != null) {
                    return extractTypeText(first.type_());
                }
            }
            return null;
        }

        private String extractTypeText(GoParser.Type_Context ctx) {
            if (ctx == null) {
                return null;
            }
            if (ctx.typeName() != null) {
                return ctx.typeName().getText();
            }
            if (ctx.typeLit() != null) {
                GoParser.TypeLitContext lit = ctx.typeLit();
                if (lit.pointerType() != null && lit.pointerType().type_() != null) {
                    return "*" + extractTypeText(lit.pointerType().type_());
                }
                if (lit.sliceType() != null) {
                    return "[]" + extractTypeText(lit.sliceType().elementType().type_());
                }
                if (lit.arrayType() != null) {
                    return "[]" + extractTypeText(lit.arrayType().elementType().type_());
                }
                if (lit.mapType() != null) {
                    return "map[" + extractTypeText(lit.mapType().type_()) + "]"
                            + extractTypeText(lit.mapType().elementType().type_());
                }
                if (lit.structType() != null) {
                    return "struct";
                }
                if (lit.interfaceType() != null) {
                    return "interface";
                }
                if (lit.functionType() != null) {
                    return "func";
                }
            }
            return ctx.getText();
        }

        private static String unquote(String literal) {
            if (literal == null || literal.length() < 2) {
                return literal;
            }
            return literal.substring(1, literal.length() - 1);
        }

        private CodeEntity entity(EntityKind kind, String content, int line) {
            return new CodeEntity(
                    kind,
                    content,
                    new EntityLocation(filePath, line, 0),
                    GoLanguageModule.LANGUAGE
            );
        }

        private CodeEntity typedEntity(EntityKind kind, String content, int line, String declaredType) {
            String resolvedType = GoTypeResolver.normalizeType(declaredType);
            if (resolvedType == null || resolvedType.isBlank()) {
                return entity(kind, content, line);
            }

            Map<String, String> attributes = new HashMap<>();
            attributes.put(EntityAttributes.DECLARED_TYPE, resolvedType);
            Set<String> assignableTypes = typeHierarchy.assignableTypes(resolvedType);
            if (!assignableTypes.isEmpty()) {
                attributes.put(EntityAttributes.ASSIGNABLE_TYPES, GoTypeResolver.serializeTypes(assignableTypes));
            }

            return new CodeEntity(
                    kind,
                    content,
                    new EntityLocation(filePath, line, 0),
                    GoLanguageModule.LANGUAGE,
                    resolvedType,
                    attributes
            );
        }

        private static int line(org.antlr.v4.runtime.ParserRuleContext ctx) {
            return line(ctx, 1);
        }

        private static int line(org.antlr.v4.runtime.ParserRuleContext ctx, int fallback) {
            if (ctx == null || ctx.getStart() == null) {
                return fallback;
            }
            return ctx.getStart().getLine();
        }
    }
}
