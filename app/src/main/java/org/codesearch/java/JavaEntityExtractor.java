package org.codesearch.java;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityAttributes;
import org.codesearch.core.EntityKind;
import org.codesearch.core.EntityLocation;
import org.codesearch.grammar.JavaLexer;
import org.codesearch.grammar.JavaParser;
import org.codesearch.grammar.JavaParserBaseListener;
import org.codesearch.plugin.AntlrParsing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JavaEntityExtractor {
    private final JavaTypeHierarchy typeHierarchy;

    public JavaEntityExtractor() {
        this(JavaTypeHierarchy.empty());
    }

    JavaEntityExtractor(JavaTypeHierarchy typeHierarchy) {
        this.typeHierarchy = typeHierarchy;
    }

    static ParseTree parse(String content) {
        return AntlrParsing.parse(new JavaLexer(CharStreams.fromString(content)), JavaParser::new, JavaParser::compilationunit);
    }

    public List<CodeEntity> extractEntities(Path file) throws IOException {
        String filePath = file.toString();
        ParseTree tree = parse(Files.readString(file));

        JavaCollector collector = new JavaCollector(filePath);
        ParseTreeWalker.DEFAULT.walk(collector, tree);
        JavaCallExtractor calls = new JavaCallExtractor(filePath);
        ParseTreeWalker.DEFAULT.walk(calls, tree);

        List<CodeEntity> entities = new ArrayList<>(collector.entities);
        entities.addAll(calls.calls());
        return entities;
    }

    private record Container(String kind, String name) {}

    private final class JavaCollector extends JavaParserBaseListener {
        private final String filePath;
        private final List<CodeEntity> entities = new ArrayList<>();
        private final Deque<Container> containers = new ArrayDeque<>();

        private JavaCollector(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void enterClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
            Map<String, String> attributes = new HashMap<>();
            if (ctx.EXTENDS() != null) {
                attributes.put(EntityAttributes.EXTENDS_TYPES, JavaTypeResolver.searchableTypeName(JavaSyntax.typeText(ctx.typeType())));
            }
            if (ctx.IMPLEMENTS() != null) {
                attributes.put(EntityAttributes.IMPLEMENTS_TYPES, JavaSyntax.typeListText(ctx.typeList(0)));
            }
            enterType(EntityKind.CLASS, "class", ctx.identifier(), ctx, attributes);
        }

        @Override
        public void exitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
            containers.pop();
        }

        @Override
        public void enterInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
            Map<String, String> attributes = new HashMap<>();
            if (ctx.EXTENDS() != null) {
                attributes.put(EntityAttributes.EXTENDS_TYPES, JavaSyntax.typeListText(ctx.typeList(0)));
            }
            enterType(EntityKind.INTERFACE, "interface", ctx.identifier(), ctx, attributes);
        }

        @Override
        public void exitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
            containers.pop();
        }

        @Override
        public void enterRecordDeclaration(JavaParser.RecordDeclarationContext ctx) {
            Map<String, String> attributes = new HashMap<>();
            if (ctx.IMPLEMENTS() != null) {
                attributes.put(EntityAttributes.IMPLEMENTS_TYPES, JavaSyntax.typeListText(ctx.typeList()));
            }
            enterType(EntityKind.RECORD, "record", ctx.identifier(), ctx, attributes);
            if (ctx.recordHeader().recordComponentList() != null) {
                for (JavaParser.RecordComponentContext component : ctx.recordHeader().recordComponentList().recordComponent()) {
                    addTyped(EntityKind.FIELD, component.identifier(), component, JavaSyntax.typeText(component.typeType()), null);
                }
            }
        }

        @Override
        public void exitRecordDeclaration(JavaParser.RecordDeclarationContext ctx) {
            containers.pop();
        }

        @Override
        public void enterEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
            Map<String, String> attributes = new HashMap<>();
            if (ctx.IMPLEMENTS() != null) {
                attributes.put(EntityAttributes.IMPLEMENTS_TYPES, JavaSyntax.typeListText(ctx.typeList()));
            }
            enterType(EntityKind.ENUM, "enum", ctx.identifier(), ctx, attributes);
        }

        @Override
        public void exitEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
            containers.pop();
        }

        @Override
        public void enterMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
            addTyped(EntityKind.METHOD, ctx.identifier(), ctx, JavaSyntax.typeText(ctx.typeTypeOrVoid()), null);
        }

        @Override
        public void enterInterfaceCommonBodyDeclaration(JavaParser.InterfaceCommonBodyDeclarationContext ctx) {
            addTyped(EntityKind.METHOD, ctx.identifier(), ctx, JavaSyntax.typeText(ctx.typeTypeOrVoid()), null);
        }

        @Override
        public void enterFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
            String type = JavaSyntax.typeText(ctx.typeType());
            for (JavaParser.VariableDeclaratorContext declarator : ctx.variableDeclarators().variableDeclarator()) {
                addTyped(EntityKind.FIELD, declarator.variableDeclaratorId().identifier(), ctx, type, null);
                addStringConstant(declarator.variableInitializer(), declarator);
            }
        }

        @Override
        public void enterConstDeclaration(JavaParser.ConstDeclarationContext ctx) {
            String type = JavaSyntax.typeText(ctx.typeType());
            for (JavaParser.ConstantDeclaratorContext declarator : ctx.constantDeclarator()) {
                addTyped(EntityKind.FIELD, declarator.identifier(), ctx, type, null);
                addStringConstant(declarator.variableInitializer(), declarator);
            }
        }

        @Override
        public void enterLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
            if (ctx.VAR() != null) {
                String initializer = ctx.expression().getText();
                addTyped(EntityKind.LOCAL_VARIABLE, ctx.identifier(), ctx, "var", initializer);
                if (initializer.startsWith("\"")) {
                    addStringConstantText(initializer, ctx);
                }
                return;
            }
            String type = JavaSyntax.typeText(ctx.typeType());
            for (JavaParser.VariableDeclaratorContext declarator : ctx.variableDeclarators().variableDeclarator()) {
                String initializer = declarator.variableInitializer() == null ? null : declarator.variableInitializer().getText();
                addTyped(EntityKind.LOCAL_VARIABLE, declarator.variableDeclaratorId().identifier(), ctx, type, initializer);
                addStringConstant(declarator.variableInitializer(), declarator);
            }
        }

        @Override
        public void enterEnhancedForControl(JavaParser.EnhancedForControlContext ctx) {
            String type = ctx.VAR() != null ? "var" : JavaSyntax.typeText(ctx.typeType());
            addTyped(EntityKind.LOCAL_VARIABLE, ctx.variableDeclaratorId().identifier(), ctx, type, null);
        }

        @Override
        public void enterAnnotation(JavaParser.AnnotationContext ctx) {
            List<JavaParser.IdentifierContext> names = ctx.qualifiedName().identifier();
            Map<String, String> attributes = new HashMap<>(containerAttributes());
            String[] target = annotationTarget(ctx);
            if (target != null) {
                attributes.put(EntityAttributes.ANNOTATION_TARGET_KIND, target[0]);
                attributes.put(EntityAttributes.ANNOTATION_TARGET_NAME, target[1]);
            }
            add(EntityKind.ANNOTATION, names.getLast().getText(), ctx.getStart(), null, attributes);
        }

        @Override
        public void enterLiteral(JavaParser.LiteralContext ctx) {
            String text = ctx.getText();
            if (ctx.integerLiteral() != null) {
                add(EntityKind.INTEGER_LITERAL, text, ctx.getStart(), null, Map.of());
            } else if (ctx.floatLiteral() != null) {
                add(EntityKind.FLOAT_LITERAL, text, ctx.getStart(), null, Map.of());
            } else if (ctx.BOOL_LITERAL() != null) {
                add(EntityKind.BOOLEAN_LITERAL, text, ctx.getStart(), null, Map.of());
            } else if (ctx.CHAR_LITERAL() != null) {
                add(EntityKind.CHAR_LITERAL, text.substring(1, text.length() - 1), ctx.getStart(), null, Map.of());
            } else if (ctx.STRING_LITERAL() != null) {
                add(EntityKind.STRING_LITERAL, text.substring(1, text.length() - 1), ctx.getStart(), null, Map.of());
            } else if (ctx.TEXT_BLOCK() != null) {
                add(EntityKind.STRING_LITERAL, text.substring(3, text.length() - 3).strip(), ctx.getStart(), null, Map.of());
            }
        }

        private void enterType(EntityKind kind, String containerKind, JavaParser.IdentifierContext identifier,
                               ParserRuleContext ctx, Map<String, String> attributes) {
            String name = identifier.getText();
            Map<String, String> typeAttributes = new HashMap<>(attributes);
            typeAttributes.values().removeIf(value -> value == null || value.isBlank());
            Set<String> supertypes = typeHierarchy.supertypes(name);
            if (!supertypes.isEmpty()) {
                typeAttributes.put(EntityAttributes.SUPERTYPES, EntityAttributes.joinList(supertypes));
            }
            add(kind, name, ctx.getStart(), null, typeAttributes);
            containers.push(new Container(containerKind, name));
        }

        private void addStringConstant(JavaParser.VariableInitializerContext initializer, ParserRuleContext ctx) {
            if (initializer != null && initializer.getText().startsWith("\"")) {
                addStringConstantText(initializer.getText(), ctx);
            }
        }

        private void addStringConstantText(String initializer, ParserRuleContext ctx) {
            add(EntityKind.STRING_CONSTANT, initializer.replace("\"", ""), ctx.getStart(), null, Map.of());
        }

        private void addTyped(EntityKind kind, JavaParser.IdentifierContext identifier, ParserRuleContext ctx,
                              String declaredType, String initializer) {
            String originalType = JavaTypeResolver.normalizeType(declaredType);
            String resolvedType = JavaTypeResolver.resolveDeclaredType(declaredType, initializer);
            Map<String, String> attributes = new HashMap<>(containerAttributes());
            if (resolvedType != null && !resolvedType.isBlank()) {
                attributes.put(EntityAttributes.DECLARED_TYPE, resolvedType);
                if ("var".equals(originalType) && !"var".equals(resolvedType)) {
                    attributes.put(EntityAttributes.TYPE_INFERENCE, "var -> " + resolvedType);
                }
                if (!"var".equals(resolvedType)) {
                    Set<String> assignableTypes = typeHierarchy.assignableTypes(resolvedType);
                    if (!assignableTypes.isEmpty()) {
                        attributes.put(EntityAttributes.ASSIGNABLE_TYPES, EntityAttributes.joinList(assignableTypes));
                    }
                }
            }
            entities.add(new CodeEntity(
                    kind,
                    identifier.getText(),
                    new EntityLocation(filePath, ctx.getStart().getLine(), identifier.getStart().getCharPositionInLine()),
                    JavaLanguagePlugin.LANGUAGE,
                    resolvedType,
                    attributes
            ));
        }

        private Map<String, String> containerAttributes() {
            Container container = containers.peek();
            if (container == null) {
                return Map.of();
            }
            return Map.of(
                    EntityAttributes.CONTAINER_KIND, container.kind(),
                    EntityAttributes.CONTAINER_NAME, container.name()
            );
        }

        private void add(EntityKind kind, String content, Token start, String declaredType, Map<String, String> attributes) {
            if (content == null || content.isBlank()) {
                return;
            }
            entities.add(new CodeEntity(
                    kind,
                    content,
                    new EntityLocation(filePath, start.getLine(), start.getCharPositionInLine()),
                    JavaLanguagePlugin.LANGUAGE,
                    declaredType,
                    attributes
            ));
        }

        private String[] annotationTarget(JavaParser.AnnotationContext annotation) {
            for (ParserRuleContext node = annotation.getParent(); node != null; node = node.getParent()) {
                if (node instanceof JavaParser.TypeTypeContext) {
                    return null;
                }
                if (node instanceof JavaParser.TypeDeclarationContext declaration) {
                    return typeTarget(declaration.classDeclaration(), declaration.interfaceDeclaration(),
                            declaration.enumDeclaration(), declaration.recordDeclaration());
                }
                if (node instanceof JavaParser.LocalTypeDeclarationContext declaration) {
                    return typeTarget(declaration.classDeclaration(), declaration.interfaceDeclaration(),
                            declaration.enumDeclaration(), declaration.recordDeclaration());
                }
                if (node instanceof JavaParser.ClassBodyDeclarationContext body && body.memberDeclaration() != null) {
                    return memberTarget(body.memberDeclaration());
                }
                if (node instanceof JavaParser.InterfaceBodyDeclarationContext body && body.interfaceMemberDeclaration() != null) {
                    return interfaceMemberTarget(body.interfaceMemberDeclaration());
                }
                if (node instanceof JavaParser.InterfaceMethodDeclarationContext method) {
                    return new String[]{"Method", method.interfaceCommonBodyDeclaration().identifier().getText()};
                }
                if (node instanceof JavaParser.LocalVariableDeclarationContext local) {
                    String name = local.VAR() != null
                            ? local.identifier().getText()
                            : local.variableDeclarators().variableDeclarator(0).variableDeclaratorId().identifier().getText();
                    return new String[]{"LocalVariable", name};
                }
                if (node instanceof JavaParser.FormalParameterContext parameter) {
                    return new String[]{"Parameter", parameter.variableDeclaratorId().identifier().getText()};
                }
                if (node instanceof JavaParser.EnumConstantContext constant) {
                    return new String[]{"EnumConstant", constant.identifier().getText()};
                }
            }
            return null;
        }

        private String[] typeTarget(JavaParser.ClassDeclarationContext classDeclaration,
                                    JavaParser.InterfaceDeclarationContext interfaceDeclaration,
                                    JavaParser.EnumDeclarationContext enumDeclaration,
                                    JavaParser.RecordDeclarationContext recordDeclaration) {
            if (classDeclaration != null) {
                return new String[]{"Class", classDeclaration.identifier().getText()};
            }
            if (interfaceDeclaration != null) {
                return new String[]{"Interface", interfaceDeclaration.identifier().getText()};
            }
            if (enumDeclaration != null) {
                return new String[]{"Enum", enumDeclaration.identifier().getText()};
            }
            if (recordDeclaration != null) {
                return new String[]{"Record", recordDeclaration.identifier().getText()};
            }
            return null;
        }

        private String[] memberTarget(JavaParser.MemberDeclarationContext member) {
            if (member.methodDeclaration() != null) {
                return new String[]{"Method", member.methodDeclaration().identifier().getText()};
            }
            if (member.genericMethodDeclaration() != null) {
                return new String[]{"Method", member.genericMethodDeclaration().methodDeclaration().identifier().getText()};
            }
            if (member.fieldDeclaration() != null) {
                return new String[]{"Field", member.fieldDeclaration().variableDeclarators().variableDeclarator(0)
                        .variableDeclaratorId().identifier().getText()};
            }
            if (member.constructorDeclaration() != null) {
                return new String[]{"Constructor", member.constructorDeclaration().identifier().getText()};
            }
            return typeTarget(member.classDeclaration(), member.interfaceDeclaration(),
                    member.enumDeclaration(), member.recordDeclaration());
        }

        private String[] interfaceMemberTarget(JavaParser.InterfaceMemberDeclarationContext member) {
            if (member.interfaceMethodDeclaration() != null) {
                return new String[]{"Method", member.interfaceMethodDeclaration().interfaceCommonBodyDeclaration().identifier().getText()};
            }
            if (member.genericInterfaceMethodDeclaration() != null) {
                return new String[]{"Method", member.genericInterfaceMethodDeclaration().interfaceCommonBodyDeclaration().identifier().getText()};
            }
            if (member.constDeclaration() != null) {
                return new String[]{"Field", member.constDeclaration().constantDeclarator(0).identifier().getText()};
            }
            return typeTarget(member.classDeclaration(), member.interfaceDeclaration(),
                    member.enumDeclaration(), member.recordDeclaration());
        }
    }
}
