package org.codesearch.python;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityAttributes;
import org.codesearch.core.EntityKind;
import org.codesearch.core.EntityLocation;
import org.codesearch.grammar.Python3Parser;
import org.codesearch.grammar.Python3ParserBaseListener;
import org.codesearch.plugin.EntityExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PythonEntityExtractor implements EntityExtractor {
    private static final Pattern SELF_ATTRIBUTE = Pattern.compile("^self\\.([A-Za-z_][\\w]*)$");
    private static final Pattern NAME_TUPLE = Pattern.compile("^\\(?([A-Za-z_][\\w]*(?:,[A-Za-z_][\\w]*)+),?\\)?$");
    private static final Pattern INTEGER = Pattern.compile("(?:0[xX][\\da-fA-F_]+|0[oO][0-7_]+|0[bB][01_]+|\\d[\\d_]*)");
    private static final Pattern STRING_PREFIX = Pattern.compile("^[rRbBuUfF]{0,2}");

    private final PythonProjectTypes projectTypes;

    public PythonEntityExtractor() {
        this(PythonProjectTypes.empty());
    }

    PythonEntityExtractor(PythonProjectTypes projectTypes) {
        this.projectTypes = projectTypes;
    }

    @Override
    public List<CodeEntity> extractEntities(Path file) throws IOException {
        PythonCollector collector = new PythonCollector(file.toString(), projectTypes);
        ParseTreeWalker.DEFAULT.walk(collector, PythonProjectTypes.parse(Files.readString(file)));
        return collector.entities;
    }

    private enum ScopeKind { MODULE, CLASS, FUNCTION }

    private record Scope(ScopeKind kind, String name, Set<String> declaredNames, Scope owningClass) {
        static Scope module() {
            return new Scope(ScopeKind.MODULE, null, new HashSet<>(), null);
        }
    }

    private static final class PythonCollector extends Python3ParserBaseListener {
        private final String filePath;
        private final PythonProjectTypes projectTypes;
        private final Map<String, String> functionReturnTypes;
        private final List<CodeEntity> entities = new ArrayList<>();
        private final Deque<Scope> scopes = new ArrayDeque<>();

        private PythonCollector(String filePath, PythonProjectTypes projectTypes) {
            this.filePath = filePath;
            this.projectTypes = projectTypes;
            this.functionReturnTypes = new HashMap<>(projectTypes.functionReturnTypes());
            scopes.push(Scope.module());
        }

        @Override
        public void enterImport_name(Python3Parser.Import_nameContext ctx) {
            for (Python3Parser.Dotted_as_nameContext name : ctx.dotted_as_names().dotted_as_name()) {
                Map<String, String> attributes = name.name() == null
                        ? Map.of()
                        : Map.of(EntityAttributes.ALIAS, name.name().getText());
                add(EntityKind.IMPORT, name.dotted_name().getText(), name, null, attributes);
            }
        }

        @Override
        public void enterImport_from(Python3Parser.Import_fromContext ctx) {
            String module = moduleOf(ctx);
            if (ctx.import_as_names() == null) {
                add(EntityKind.IMPORT, module + ".*", ctx, null, Map.of());
                return;
            }
            for (Python3Parser.Import_as_nameContext name : ctx.import_as_names().import_as_name()) {
                Map<String, String> attributes = name.name().size() > 1
                        ? Map.of(EntityAttributes.ALIAS, name.name(1).getText())
                        : Map.of();
                String imported = module.endsWith(".") ? module + name.name(0).getText() : module + "." + name.name(0).getText();
                add(EntityKind.IMPORT, imported, name, null, attributes);
            }
        }

        @Override
        public void enterClassdef(Python3Parser.ClassdefContext ctx) {
            String name = ctx.name().getText();
            Map<String, String> attributes = new HashMap<>(containerAttributes());
            List<String> bases = PythonProjectTypes.baseClassNames(ctx);
            if (!bases.isEmpty()) {
                attributes.put(EntityAttributes.EXTENDS_TYPES, String.join(", ", bases));
            }
            Set<String> supertypes = projectTypes.supertypes(name);
            if (!supertypes.isEmpty()) {
                attributes.put(EntityAttributes.SUPERTYPES, EntityAttributes.joinList(supertypes));
            }
            add(EntityKind.CLASS, name, ctx.name(), null, attributes);
            scopes.push(new Scope(ScopeKind.CLASS, name, new HashSet<>(), null));
        }

        @Override
        public void exitClassdef(Python3Parser.ClassdefContext ctx) {
            scopes.pop();
        }

        @Override
        public void enterFuncdef(Python3Parser.FuncdefContext ctx) {
            Scope parent = scopes.peek();
            boolean isMethod = parent.kind() == ScopeKind.CLASS;
            String name = ctx.name().getText();
            String returnType = ctx.test() == null ? null : PythonTypes.normalizeAnnotation(ctx.test().getText());
            if (returnType != null && parent.kind() == ScopeKind.MODULE) {
                functionReturnTypes.putIfAbsent(name, returnType);
            }

            add(isMethod ? EntityKind.METHOD : EntityKind.FUNCTION, name, ctx.name(), returnType,
                    typedAttributes(returnType, containerAttributes(), null));
            scopes.push(new Scope(ScopeKind.FUNCTION, name, new HashSet<>(), isMethod ? parent : null));
        }

        @Override
        public void exitFuncdef(Python3Parser.FuncdefContext ctx) {
            scopes.pop();
        }

        @Override
        public void enterDecorated(Python3Parser.DecoratedContext ctx) {
            String targetKind;
            String targetName;
            if (ctx.classdef() != null) {
                targetKind = "Class";
                targetName = ctx.classdef().name().getText();
            } else {
                Python3Parser.FuncdefContext funcdef = ctx.funcdef() != null ? ctx.funcdef() : ctx.async_funcdef().funcdef();
                targetKind = scopes.peek().kind() == ScopeKind.CLASS ? "Method" : "Function";
                targetName = funcdef.name().getText();
            }

            for (Python3Parser.DecoratorContext decorator : ctx.decorators().decorator()) {
                Map<String, String> attributes = new HashMap<>(containerAttributes());
                attributes.put(EntityAttributes.ANNOTATION_TARGET_KIND, targetKind);
                attributes.put(EntityAttributes.ANNOTATION_TARGET_NAME, targetName);
                add(EntityKind.DECORATOR, decorator.dotted_name().getText(), decorator.dotted_name(), null, attributes);
            }
        }

        @Override
        public void enterExpr_stmt(Python3Parser.Expr_stmtContext ctx) {
            if (ctx.annassign() != null) {
                Python3Parser.AnnassignContext annassign = ctx.annassign();
                String annotation = PythonTypes.normalizeAnnotation(annassign.test(0).getText());
                declare(ctx.testlist_star_expr(0).getText(), ctx, annotation, null);
                return;
            }
            if (ctx.augassign() != null || ctx.ASSIGN().isEmpty()) {
                return;
            }

            int targetCount = ctx.ASSIGN().size();
            String value = ctx.getChild(ctx.getChildCount() - 1).getText();
            for (int i = 0; i < targetCount && i < ctx.testlist_star_expr().size(); i++) {
                String target = ctx.testlist_star_expr(i).getText();
                String inferredType = PythonTypes.inferExpressionType(value, projectTypes.classNames(), functionReturnTypes);
                declare(target, ctx, inferredType, inferredType == null ? null : "= -> " + inferredType);
            }
        }

        @Override
        public void enterAtom_expr(Python3Parser.Atom_exprContext ctx) {
            List<Python3Parser.TrailerContext> trailers = ctx.trailer();
            for (int i = 0; i < trailers.size(); i++) {
                if (!"(".equals(trailers.get(i).getChild(0).getText())) {
                    continue;
                }
                if (i == 0 && ctx.atom().name() != null) {
                    addCall(ctx.atom().name().getText(), null, ctx);
                } else if (i > 0 && trailers.get(i - 1).name() != null) {
                    StringBuilder qualifier = new StringBuilder(ctx.atom().getText());
                    for (int j = 0; j < i - 1; j++) {
                        qualifier.append(trailers.get(j).getText());
                    }
                    addCall(trailers.get(i - 1).name().getText(), qualifier.toString(), trailers.get(i - 1));
                }
            }
        }

        private void addCall(String name, String qualifier, ParserRuleContext ctx) {
            Map<String, String> attributes = new HashMap<>();
            if (qualifier != null) {
                attributes.put(EntityAttributes.CALL_QUALIFIER, qualifier);
            }
            attributes.putAll(containerAttributes());
            add(EntityKind.CALL, name, ctx, null, attributes);
        }

        @Override
        public void enterAtom(Python3Parser.AtomContext ctx) {
            if (!ctx.STRING().isEmpty()) {
                StringBuilder value = new StringBuilder();
                for (TerminalNode string : ctx.STRING()) {
                    value.append(unquote(string.getText()));
                }
                add(EntityKind.STRING_LITERAL, value.toString(), ctx, null, Map.of());
            } else if (ctx.NUMBER() != null) {
                String number = ctx.NUMBER().getText();
                if (INTEGER.matcher(number).matches()) {
                    add(EntityKind.INTEGER_LITERAL, number, ctx, null, Map.of());
                } else if (!number.endsWith("j") && !number.endsWith("J")) {
                    add(EntityKind.FLOAT_LITERAL, number, ctx, null, Map.of());
                }
            }
        }

        private void declare(String target, ParserRuleContext ctx, String declaredType, String typeInference) {
            Scope scope = scopes.peek();

            Matcher selfAttribute = SELF_ATTRIBUTE.matcher(target);
            if (selfAttribute.matches()) {
                if (scope.kind() == ScopeKind.FUNCTION && scope.owningClass() != null) {
                    Scope owningClass = scope.owningClass();
                    declareInScope(owningClass, EntityKind.FIELD, selfAttribute.group(1), ctx, declaredType, typeInference,
                            Map.of(EntityAttributes.CONTAINER_KIND, "class", EntityAttributes.CONTAINER_NAME, owningClass.name()));
                }
                return;
            }

            if (PythonTypes.isIdentifier(target)) {
                declareInScope(scope, kindFor(scope, target), target, ctx, declaredType, typeInference, containerAttributes());
                return;
            }

            Matcher tuple = NAME_TUPLE.matcher(target);
            if (tuple.matches()) {
                for (String name : tuple.group(1).split(",")) {
                    declareInScope(scope, kindFor(scope, name), name, ctx, null, null, containerAttributes());
                }
            }
        }

        private void declareInScope(Scope scope, EntityKind kind, String name, ParserRuleContext ctx,
                                    String declaredType, String typeInference, Map<String, String> attributes) {
            if ("_".equals(name) || !scope.declaredNames().add(name)) {
                return;
            }
            add(kind, name, ctx, declaredType, typedAttributes(declaredType, attributes, typeInference));
        }

        private static EntityKind kindFor(Scope scope, String name) {
            return switch (scope.kind()) {
                case MODULE -> PythonTypes.isConstantName(name) ? EntityKind.CONSTANT : EntityKind.VARIABLE;
                case CLASS -> EntityKind.FIELD;
                case FUNCTION -> EntityKind.LOCAL_VARIABLE;
            };
        }

        private Map<String, String> typedAttributes(String declaredType, Map<String, String> attributes, String typeInference) {
            if (declaredType == null) {
                return attributes;
            }
            Map<String, String> typed = new HashMap<>(attributes);
            typed.put(EntityAttributes.DECLARED_TYPE, declaredType);
            if (typeInference != null) {
                typed.put(EntityAttributes.TYPE_INFERENCE, typeInference);
            }
            Set<String> assignableTypes = projectTypes.assignableTypes(declaredType);
            if (!assignableTypes.isEmpty()) {
                typed.put(EntityAttributes.ASSIGNABLE_TYPES, EntityAttributes.joinList(assignableTypes));
            }
            return typed;
        }

        private Map<String, String> containerAttributes() {
            Scope scope = scopes.peek();
            if (scope == null || scope.kind() == ScopeKind.MODULE) {
                return Map.of();
            }
            if (scope.kind() == ScopeKind.CLASS) {
                return Map.of(EntityAttributes.CONTAINER_KIND, "class", EntityAttributes.CONTAINER_NAME, scope.name());
            }
            if (scope.owningClass() != null) {
                return Map.of(EntityAttributes.CONTAINER_KIND, "method",
                        EntityAttributes.CONTAINER_NAME, scope.owningClass().name() + "." + scope.name());
            }
            return Map.of(EntityAttributes.CONTAINER_KIND, "function", EntityAttributes.CONTAINER_NAME, scope.name());
        }

        private static String moduleOf(Python3Parser.Import_fromContext ctx) {
            StringBuilder module = new StringBuilder();
            for (int i = 1; i < ctx.getChildCount(); i++) {
                String text = ctx.getChild(i).getText();
                if ("import".equals(text)) {
                    break;
                }
                module.append(text);
            }
            return module.toString();
        }

        private static String unquote(String literal) {
            String value = STRING_PREFIX.matcher(literal).replaceFirst("");
            if (value.length() >= 6 && (value.startsWith("\"\"\"") || value.startsWith("'''"))) {
                return value.substring(3, value.length() - 3);
            }
            if (value.length() >= 2) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }

        private void add(EntityKind kind, String content, ParserRuleContext ctx, String declaredType, Map<String, String> attributes) {
            if (content == null || content.isBlank()) {
                return;
            }
            entities.add(new CodeEntity(
                    kind,
                    content,
                    new EntityLocation(filePath, ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine()),
                    PythonLanguagePlugin.LANGUAGE,
                    declaredType,
                    attributes
            ));
        }
    }
}
