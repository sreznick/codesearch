package org.codesearch.go;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.example.GoLexer;
import org.example.GoParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GoTypeHierarchyExtractor {
    private GoTypeHierarchyExtractor() {}

    static GoTypeHierarchy extract(List<Path> goFiles) throws IOException {
        Map<String, Set<String>> structMethods = new HashMap<>();
        Map<String, Set<String>> interfaceMethods = new HashMap<>();

        for (Path file : goFiles) {
            try {
                extractFromFile(file, structMethods, interfaceMethods);
            } catch (RuntimeException ignored) {
                // Skip files that fail to parse.
            }
        }
        return new GoTypeHierarchy(structMethods, interfaceMethods);
    }

    private static void extractFromFile(
            Path file,
            Map<String, Set<String>> structMethods,
            Map<String, Set<String>> interfaceMethods
    ) throws IOException {
        String content = Files.readString(file);
        GoLexer lexer = new GoLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        GoParser parser = new GoParser(tokens);
        ParseTree tree = parser.sourceFile();

        HierarchyListener listener = new HierarchyListener(structMethods, interfaceMethods);
        ParseTreeWalker.DEFAULT.walk(listener, tree);
    }

    private static final class HierarchyListener extends org.example.GoParserBaseListener {
        private final Map<String, Set<String>> structMethods;
        private final Map<String, Set<String>> interfaceMethods;
        private String currentStruct;
        private String currentInterface;

        HierarchyListener(Map<String, Set<String>> structMethods, Map<String, Set<String>> interfaceMethods) {
            this.structMethods = structMethods;
            this.interfaceMethods = interfaceMethods;
        }

        @Override
        public void enterTypeSpec(GoParser.TypeSpecContext ctx) {
            if (ctx.typeDef() == null || ctx.typeDef().IDENTIFIER() == null || ctx.typeDef().type_() == null) {
                return;
            }
            String name = ctx.typeDef().IDENTIFIER().getText();
            GoParser.TypeLitContext typeLit = ctx.typeDef().type_().typeLit();
            if (typeLit == null) {
                return;
            }
            if (typeLit.structType() != null) {
                currentStruct = name;
                structMethods.putIfAbsent(name, new HashSet<>());
            } else if (typeLit.interfaceType() != null) {
                currentInterface = name;
                interfaceMethods.putIfAbsent(name, new HashSet<>());
                if (typeLit.interfaceType().methodSpec() != null) {
                    for (GoParser.MethodSpecContext method : typeLit.interfaceType().methodSpec()) {
                        if (method.IDENTIFIER() != null) {
                            interfaceMethods.get(name).add(method.IDENTIFIER().getText());
                        }
                    }
                }
            }
        }

        @Override
        public void exitTypeSpec(GoParser.TypeSpecContext ctx) {
            currentStruct = null;
            currentInterface = null;
        }

        @Override
        public void enterMethodDecl(GoParser.MethodDeclContext ctx) {
            if (currentStruct == null || ctx.IDENTIFIER() == null) {
                return;
            }
            structMethods.computeIfAbsent(currentStruct, ignored -> new HashSet<>())
                    .add(ctx.IDENTIFIER().getText());
        }
    }
}
