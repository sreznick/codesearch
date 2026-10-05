package org.codesearch.python;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codesearch.grammar.Python3Lexer;
import org.codesearch.grammar.Python3Parser;
import org.codesearch.grammar.Python3ParserBaseListener;
import org.codesearch.plugin.AntlrParsing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PythonProjectTypes {
    private static final Logger logger = LogManager.getLogger(PythonProjectTypes.class);

    private final Map<String, List<String>> classBases;
    private final Map<String, String> functionReturnTypes;

    private PythonProjectTypes(Map<String, List<String>> classBases, Map<String, String> functionReturnTypes) {
        this.classBases = classBases;
        this.functionReturnTypes = functionReturnTypes;
    }

    static PythonProjectTypes empty() {
        return new PythonProjectTypes(Map.of(), Map.of());
    }

    static PythonProjectTypes collect(List<Path> pythonFiles) {
        Map<String, List<String>> classBases = new HashMap<>();
        Map<String, String> functionReturnTypes = new HashMap<>();
        pythonFiles.parallelStream()
                .map(PythonProjectTypes::collectFile)
                .toList()
                .forEach(collector -> {
                    collector.classBases.forEach(classBases::putIfAbsent);
                    collector.functionReturnTypes.forEach(functionReturnTypes::putIfAbsent);
                });
        return new PythonProjectTypes(classBases, functionReturnTypes);
    }

    private static Collector collectFile(Path file) {
        Collector collector = new Collector();
        try {
            ParseTreeWalker.DEFAULT.walk(collector, parse(Files.readString(file)));
        } catch (IOException | RuntimeException e) {
            logger.debug("Типы не извлечены из {}: {}", file, e.getMessage());
        }
        return collector;
    }

    static ParseTree parse(String content) {
        String source = content.endsWith("\n") ? content : content + "\n";
        return AntlrParsing.parse(new Python3Lexer(CharStreams.fromString(source)), Python3Parser::new, Python3Parser::file_input);
    }

    Set<String> classNames() {
        return classBases.keySet();
    }

    Map<String, String> functionReturnTypes() {
        return functionReturnTypes;
    }

    Set<String> assignableTypes(String declaredType) {
        String name = PythonTypes.searchableName(declaredType);
        if (name == null || name.isBlank() || "None".equals(name)) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(name);
        collectBases(name, result, new HashSet<>());
        if ("bool".equals(name)) {
            result.add("int");
        }
        result.add("object");
        return result;
    }

    Set<String> supertypes(String className) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        collectBases(className, result, new HashSet<>());
        result.remove(className);
        result.remove("object");
        return result;
    }

    private void collectBases(String type, Set<String> result, Set<String> visited) {
        if (!visited.add(type)) {
            return;
        }
        for (String base : classBases.getOrDefault(type, List.of())) {
            result.add(base);
            collectBases(base, result, visited);
        }
    }

    static List<String> baseClassNames(Python3Parser.ClassdefContext ctx) {
        List<String> bases = new ArrayList<>();
        if (ctx.arglist() == null) {
            return bases;
        }
        for (Python3Parser.ArgumentContext argument : ctx.arglist().argument()) {
            if (argument.test().size() != 1 || argument.ASSIGN() != null || argument.STAR() != null || argument.POWER() != null) {
                continue;
            }
            String base = PythonTypes.searchableName(argument.test(0).getText());
            if (base != null && !base.isBlank() && !"object".equals(base)) {
                bases.add(base);
            }
        }
        return bases;
    }

    private static final class Collector extends Python3ParserBaseListener {
        private final Map<String, List<String>> classBases = new HashMap<>();
        private final Map<String, String> functionReturnTypes = new HashMap<>();

        private int nesting;

        @Override
        public void enterClassdef(Python3Parser.ClassdefContext ctx) {
            classBases.putIfAbsent(ctx.name().getText(), baseClassNames(ctx));
            nesting++;
        }

        @Override
        public void exitClassdef(Python3Parser.ClassdefContext ctx) {
            nesting--;
        }

        @Override
        public void exitFuncdef(Python3Parser.FuncdefContext ctx) {
            nesting--;
        }

        @Override
        public void enterFuncdef(Python3Parser.FuncdefContext ctx) {
            nesting++;
            if (nesting == 1 && ctx.test() != null) {
                String returnType = PythonTypes.normalizeAnnotation(ctx.test().getText());
                if (returnType != null) {
                    functionReturnTypes.putIfAbsent(ctx.name().getText(), returnType);
                }
            }
        }
    }
}
