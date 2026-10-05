package org.codesearch.java;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codesearch.grammar.JavaParserBaseListener;
import org.codesearch.grammar.JavaParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class JavaTypeHierarchyExtractor extends JavaParserBaseListener {
    private static final Logger logger = LogManager.getLogger();

    private final Map<String, Set<String>> directParents = new LinkedHashMap<>();

    static JavaTypeHierarchy extract(List<Path> javaFiles) {
        Map<String, Set<String>> directParents = new LinkedHashMap<>();
        javaFiles.parallelStream()
                .map(JavaTypeHierarchyExtractor::extractFile)
                .toList()
                .forEach(extractor -> extractor.directParents.forEach((type, parents) ->
                        directParents.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).addAll(parents)));
        return new JavaTypeHierarchy(directParents);
    }

    private static JavaTypeHierarchyExtractor extractFile(Path file) {
        JavaTypeHierarchyExtractor extractor = new JavaTypeHierarchyExtractor();
        try {
            extractor.parseFile(file);
        } catch (IOException | RuntimeException e) {
            logger.debug("Иерархия типов не извлечена из {}: {}", file, e.getMessage());
        }
        return extractor;
    }

    @Override
    public void enterClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
        String className = ctx.identifier().getText();
        if (ctx.EXTENDS() != null) {
            addParent(className, JavaSyntax.typeText(ctx.typeType()));
        }
        if (ctx.IMPLEMENTS() != null) {
            addParents(className, ctx.typeList(0));
        }
    }

    @Override
    public void enterInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        if (ctx.EXTENDS() != null) {
            addParents(ctx.identifier().getText(), ctx.typeList(0));
        }
    }

    @Override
    public void enterRecordDeclaration(JavaParser.RecordDeclarationContext ctx) {
        if (ctx.IMPLEMENTS() != null) {
            addParents(ctx.identifier().getText(), ctx.typeList());
        }
    }

    @Override
    public void enterEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
        if (ctx.IMPLEMENTS() != null) {
            addParents(ctx.identifier().getText(), ctx.typeList());
        }
    }

    private void parseFile(Path file) throws IOException {
        ParseTree tree = JavaEntityExtractor.parse(Files.readString(file));
        ParseTreeWalker.DEFAULT.walk(this, tree);
    }

    private void addParents(String child, JavaParser.TypeListContext typeList) {
        if (child == null || child.isBlank()) {
            return;
        }

        for (JavaParser.TypeTypeContext type : typeList.typeType()) {
            addParent(child, JavaSyntax.typeText(type));
        }
    }

    private void addParent(String child, String rawParent) {
        if (child == null || child.isBlank()) {
            return;
        }

        String parent = JavaTypeResolver.searchableTypeName(rawParent);
        if (parent != null && !parent.isBlank()) {
            directParents.computeIfAbsent(child, ignored -> new LinkedHashSet<>()).add(parent);
        }
    }
}
