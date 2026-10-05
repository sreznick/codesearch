package org.codesearch.go;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityAttributes;
import org.codesearch.core.EntityKind;
import org.codesearch.core.EntityLocation;
import org.codesearch.grammar.GoParser;
import org.codesearch.grammar.GoParserBaseListener;
import org.codesearch.plugin.EntityExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GoEntityExtractor implements EntityExtractor {
    private final GoProjectTypes projectTypes;

    public GoEntityExtractor() {
        this(GoProjectTypes.empty());
    }

    GoEntityExtractor(GoProjectTypes projectTypes) {
        this.projectTypes = projectTypes;
    }

    @Override
    public List<CodeEntity> extractEntities(Path file) throws IOException {
        GoCollector collector = new GoCollector(file.toString(), projectTypes);
        ParseTreeWalker.DEFAULT.walk(collector, GoProjectTypes.parse(Files.readString(file)));
        return collector.entities();
    }

    private static final class GoCollector extends GoParserBaseListener {
        private final String filePath;
        private final GoProjectTypes projectTypes;
        private final Map<String, String> functionResultTypes;
        private final List<CodeEntity> entities = new ArrayList<>();
        private String containerKind;
        private String containerName;
        private int functionDepth;
        private String currentFunction;
        private final Set<String> importedPackages = new HashSet<>();

        private GoCollector(String filePath, GoProjectTypes projectTypes) {
            this.filePath = filePath;
            this.projectTypes = projectTypes;
            this.functionResultTypes = new HashMap<>(projectTypes.functionResultTypes());
        }

        private List<CodeEntity> entities() {
            return entities;
        }

        @Override
        public void enterPackageClause(GoParser.PackageClauseContext ctx) {
            add(EntityKind.PACKAGE, ctx.IDENTIFIER().getText(), ctx);
        }

        @Override
        public void enterImportSpec(GoParser.ImportSpecContext ctx) {
            Map<String, String> attributes = ctx.alias == null
                    ? Map.of()
                    : Map.of(EntityAttributes.ALIAS, ctx.alias.getText());
            String path = unquote(ctx.importPath().string_().getText());
            importedPackages.add(ctx.alias == null ? path.substring(path.lastIndexOf('/') + 1) : ctx.alias.getText());
            add(EntityKind.IMPORT, path, ctx, null, attributes);
        }

        @Override
        public void enterFunctionDecl(GoParser.FunctionDeclContext ctx) {
            functionDepth++;
            currentFunction = ctx.IDENTIFIER().getText();
            String resultType = GoProjectTypes.firstResultType(ctx.signature());
            if (resultType != null) {
                functionResultTypes.putIfAbsent(ctx.IDENTIFIER().getText(), resultType);
            }
            addTyped(EntityKind.FUNCTION, ctx.IDENTIFIER(), ctx, resultType, Map.of(), null);
        }

        @Override
        public void exitFunctionDecl(GoParser.FunctionDeclContext ctx) {
            functionDepth--;
            currentFunction = null;
        }

        @Override
        public void enterMethodDecl(GoParser.MethodDeclContext ctx) {
            functionDepth++;
            Map<String, String> attributes = new HashMap<>();
            attributes.put(EntityAttributes.RECEIVER, ctx.receiver().getText());
            String receiverType = GoProjectTypes.receiverTypeName(ctx.receiver());
            if (receiverType != null) {
                attributes.put(EntityAttributes.CONTAINER_KIND, "type");
                attributes.put(EntityAttributes.CONTAINER_NAME, receiverType);
            }
            currentFunction = receiverType == null ? ctx.IDENTIFIER().getText() : receiverType + "." + ctx.IDENTIFIER().getText();
            addTyped(EntityKind.METHOD, ctx.IDENTIFIER(), ctx, GoProjectTypes.firstResultType(ctx.signature()), attributes, null);
        }

        @Override
        public void exitMethodDecl(GoParser.MethodDeclContext ctx) {
            functionDepth--;
            currentFunction = null;
        }

        @Override
        public void enterFunctionLit(GoParser.FunctionLitContext ctx) {
            functionDepth++;
        }

        @Override
        public void exitFunctionLit(GoParser.FunctionLitContext ctx) {
            functionDepth--;
        }

        @Override
        public void enterTypeSpec(GoParser.TypeSpecContext ctx) {
            if (ctx.typeDef() == null) {
                return;
            }
            String name = ctx.typeDef().IDENTIFIER().getText();
            GoParser.TypeLitContext typeLit = ctx.typeDef().type_() == null ? null : ctx.typeDef().type_().typeLit();
            if (typeLit != null && typeLit.structType() != null) {
                add(EntityKind.STRUCT, name, ctx, "struct", supertypeAttributes(name, Map.of()));
                containerKind = "struct";
                containerName = name;
            } else if (typeLit != null && typeLit.interfaceType() != null) {
                add(EntityKind.INTERFACE, name, ctx, "interface", supertypeAttributes(name, embeddedInterfaces(typeLit.interfaceType())));
                containerKind = "interface";
                containerName = name;
            }
        }

        @Override
        public void exitTypeSpec(GoParser.TypeSpecContext ctx) {
            containerKind = null;
            containerName = null;
        }

        @Override
        public void enterMethodSpec(GoParser.MethodSpecContext ctx) {
            String resultType = ctx.result() == null ? null
                    : ctx.result().type_() != null ? ctx.result().type_().getText() : ctx.result().getText();
            addTyped(EntityKind.METHOD, ctx.IDENTIFIER(), ctx, resultType, containerAttributes(), null);
        }

        @Override
        public void enterFieldDecl(GoParser.FieldDeclContext ctx) {
            if (ctx.identifierList() != null) {
                String declaredType = ctx.type_().getText();
                for (TerminalNode identifier : ctx.identifierList().IDENTIFIER()) {
                    addTyped(EntityKind.FIELD, identifier, ctx, declaredType, containerAttributes(), null);
                }
                return;
            }
            if (ctx.embeddedField() != null) {
                String declaredType = ctx.embeddedField().getText();
                add(EntityKind.FIELD, ctx.embeddedField().typeName().getText(), ctx,
                        GoTypes.normalize(declaredType), typedAttributes(declaredType, containerAttributes(), null));
            }
        }

        @Override
        public void enterVarSpec(GoParser.VarSpecContext ctx) {
            EntityKind kind = functionDepth > 0 ? EntityKind.LOCAL_VARIABLE : EntityKind.VARIABLE;
            List<TerminalNode> identifiers = ctx.identifierList().IDENTIFIER();
            for (int i = 0; i < identifiers.size(); i++) {
                if (ctx.type_() != null) {
                    addTyped(kind, identifiers.get(i), ctx, ctx.type_().getText(), Map.of(), null);
                } else {
                    addInferred(kind, identifiers.get(i), ctx, expressionAt(ctx.expressionList(), i, identifiers.size()), "=");
                }
            }
        }

        @Override
        public void enterConstSpec(GoParser.ConstSpecContext ctx) {
            List<TerminalNode> identifiers = ctx.identifierList().IDENTIFIER();
            for (int i = 0; i < identifiers.size(); i++) {
                if (ctx.type_() != null) {
                    addTyped(EntityKind.CONSTANT, identifiers.get(i), ctx, ctx.type_().getText(), Map.of(), null);
                } else {
                    addInferred(EntityKind.CONSTANT, identifiers.get(i), ctx, expressionAt(ctx.expressionList(), i, identifiers.size()), "=");
                }
            }
        }

        @Override
        public void enterShortVarDecl(GoParser.ShortVarDeclContext ctx) {
            List<TerminalNode> identifiers = ctx.identifierList().IDENTIFIER();
            for (int i = 0; i < identifiers.size(); i++) {
                if (!"_".equals(identifiers.get(i).getText())) {
                    addInferred(EntityKind.LOCAL_VARIABLE, identifiers.get(i), ctx,
                            expressionAt(ctx.expressionList(), i, identifiers.size()), ":=");
                }
            }
        }

        @Override
        public void enterPrimaryExpr(GoParser.PrimaryExprContext ctx) {
            if (ctx.arguments() == null || ctx.primaryExpr() == null) {
                return;
            }
            GoParser.PrimaryExprContext callee = ctx.primaryExpr();
            if (callee.IDENTIFIER() != null && callee.primaryExpr() != null) {
                addCall(callee.IDENTIFIER().getText(), callee.primaryExpr().getText(), ctx);
            } else if (callee.operand() != null && callee.operand().operandName() != null) {
                addCall(callee.operand().operandName().getText(), null, ctx);
            }
        }

        @Override
        public void enterConversion(GoParser.ConversionContext ctx) {
            GoParser.TypeNameContext typeName = ctx.type_().typeName();
            if (typeName == null) {
                return;
            }
            if (typeName.qualifiedIdent() != null) {
                addCall(typeName.qualifiedIdent().IDENTIFIER(1).getText(), typeName.qualifiedIdent().IDENTIFIER(0).getText(), ctx);
            } else {
                addCall(typeName.getText(), null, ctx);
            }
        }

        private void addCall(String name, String qualifier, ParserRuleContext ctx) {
            Map<String, String> attributes = new HashMap<>();
            if (qualifier != null) {
                attributes.put(EntityAttributes.CALL_QUALIFIER, qualifier);
            }
            if (currentFunction != null) {
                attributes.put(EntityAttributes.CONTAINER_KIND, "function");
                attributes.put(EntityAttributes.CONTAINER_NAME, currentFunction);
            }
            add(EntityKind.CALL, name, ctx, null, attributes);
        }

        private Map<String, String> supertypeAttributes(String typeName, Map<String, String> attributes) {
            Set<String> supertypes = projectTypes.supertypes(typeName);
            if (supertypes.isEmpty()) {
                return attributes;
            }
            Map<String, String> result = new HashMap<>(attributes);
            result.put(EntityAttributes.SUPERTYPES, EntityAttributes.joinList(supertypes));
            return result;
        }

        @Override
        public void enterBasicLit(GoParser.BasicLitContext ctx) {
            if (ctx.string_() != null) {
                add(EntityKind.STRING_LITERAL, unquote(ctx.string_().getText()), ctx);
            } else if (ctx.integer() != null) {
                add(EntityKind.INTEGER_LITERAL, ctx.integer().getText(), ctx);
            } else if (ctx.FLOAT_LIT() != null) {
                add(EntityKind.FLOAT_LITERAL, ctx.FLOAT_LIT().getText(), ctx);
            }
        }

        private static String expressionAt(GoParser.ExpressionListContext expressions, int index, int identifierCount) {
            if (expressions == null || expressions.expression().size() != identifierCount) {
                return null;
            }
            return expressions.expression(index).getText();
        }

        private void addInferred(EntityKind kind, TerminalNode identifier, ParserRuleContext ctx, String expression, String operator) {
            String inferredType = GoTypes.inferExpressionType(expression, functionResultTypes, importedPackages);
            addTyped(kind, identifier, ctx, inferredType, Map.of(), inferredType == null ? null : operator + " -> " + inferredType);
        }

        private void addTyped(EntityKind kind, TerminalNode identifier, ParserRuleContext ctx, String declaredType,
                              Map<String, String> attributes, String typeInference) {
            String normalizedType = GoTypes.normalize(declaredType);
            add(kind, identifier.getText(), identifier.getSymbol().getLine(), identifier.getSymbol().getCharPositionInLine(),
                    normalizedType, typedAttributes(normalizedType, attributes, typeInference));
        }

        private Map<String, String> typedAttributes(String declaredType, Map<String, String> attributes, String typeInference) {
            String normalizedType = GoTypes.normalize(declaredType);
            if (normalizedType == null) {
                return attributes;
            }
            Map<String, String> typed = new HashMap<>(attributes);
            typed.put(EntityAttributes.DECLARED_TYPE, normalizedType);
            if (typeInference != null) {
                typed.put(EntityAttributes.TYPE_INFERENCE, typeInference);
            }
            Set<String> assignableTypes = projectTypes.assignableTypes(normalizedType);
            if (!assignableTypes.isEmpty()) {
                typed.put(EntityAttributes.ASSIGNABLE_TYPES, EntityAttributes.joinList(assignableTypes));
            }
            return typed;
        }

        private Map<String, String> embeddedInterfaces(GoParser.InterfaceTypeContext interfaceType) {
            List<String> embedded = interfaceType.typeElement().stream()
                    .filter(element -> element.typeTerm().size() == 1 && element.typeTerm(0).UNDERLYING() == null)
                    .map(element -> GoTypes.searchableName(element.typeTerm(0).getText()))
                    .toList();
            return embedded.isEmpty() ? Map.of() : Map.of(EntityAttributes.EXTENDS_TYPES, String.join(", ", embedded));
        }

        private Map<String, String> containerAttributes() {
            if (containerKind == null || containerName == null) {
                return Map.of();
            }
            return Map.of(
                    EntityAttributes.CONTAINER_KIND, containerKind,
                    EntityAttributes.CONTAINER_NAME, containerName
            );
        }

        private static String unquote(String value) {
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

        private void add(EntityKind kind, String content, ParserRuleContext ctx) {
            add(kind, content, ctx, null, Map.of());
        }

        private void add(EntityKind kind, String content, ParserRuleContext ctx, String declaredType, Map<String, String> attributes) {
            add(kind, content, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine(), declaredType, attributes);
        }

        private void add(EntityKind kind, String content, int line, int column, String declaredType, Map<String, String> attributes) {
            if (content == null || content.isBlank()) {
                return;
            }
            entities.add(new CodeEntity(
                    kind,
                    content,
                    new EntityLocation(filePath, line, column),
                    GoLanguagePlugin.LANGUAGE,
                    declaredType,
                    attributes
            ));
        }
    }
}
