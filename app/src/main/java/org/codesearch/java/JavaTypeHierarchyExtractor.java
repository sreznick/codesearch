package org.codesearch.java;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.JavaBaseListener;
import org.example.JavaLexer;
import org.example.JavaParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JavaTypeHierarchyExtractor extends JavaBaseListener {
    private static final Logger logger = LogManager.getLogger();

    private final Map<String, Set<String>> directParents = new LinkedHashMap<>();

    static JavaTypeHierarchy extract(List<Path> javaFiles) {
        JavaTypeHierarchyExtractor extractor = new JavaTypeHierarchyExtractor();
        for (Path file : javaFiles) {
            try {
                extractor.parseFile(file);
            } catch (Exception e) {
                logger.debug("Иерархия типов не извлечена из {}: {}", file, e.getMessage());
            }
        }
        return new JavaTypeHierarchy(extractor.directParents);
    }

    @Override
    public void enterClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
        if (ctx.IMPLEMENTS() == null || ctx.typeList() == null) {
            return;
        }

        String className = JavaTypeResolver.searchableTypeName(ctx.Identifier().getText());
        addParents(className, ctx.typeList());
    }

    @Override
    public void enterInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        if (ctx.EXTENDS() == null || ctx.typeList() == null) {
            return;
        }

        String interfaceName = JavaTypeResolver.searchableTypeName(ctx.Identifier().getText());
        addParents(interfaceName, ctx.typeList());
    }

    private void parseFile(Path file) throws IOException {
        JavaLexer lexer = new JavaLexer(CharStreams.fromString(Files.readString(file)));
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        parser.removeErrorListeners();

        ParseTree tree = parser.compilationUnit();
        ParseTreeWalker.DEFAULT.walk(this, tree);
    }

    private void addParents(String child, JavaParser.TypeListContext typeList) {
        if (child == null || child.isBlank()) {
            return;
        }

        Set<String> parents = directParents.computeIfAbsent(child, ignored -> new LinkedHashSet<>());
        for (JavaParser.TypeSpecContext typeSpec : typeList.typeSpec()) {
            String parent = JavaTypeResolver.searchableTypeName(typeSpec.getText());
            if (parent != null && !parent.isBlank()) {
                parents.add(parent);
            }
        }
    }
}
