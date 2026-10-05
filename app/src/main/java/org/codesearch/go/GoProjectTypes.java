package org.codesearch.go;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codesearch.grammar.GoLexer;
import org.codesearch.grammar.GoParser;
import org.codesearch.grammar.GoParserBaseListener;
import org.codesearch.plugin.AntlrParsing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GoProjectTypes {
    private static final Logger logger = LogManager.getLogger(GoProjectTypes.class);

    private final Map<String, Set<String>> methodsByType;
    private final Map<String, Set<String>> interfaceMethods;
    private final Map<String, Set<String>> embeddedInterfaces;
    private final Map<String, String> functionResultTypes;

    private GoProjectTypes(
            Map<String, Set<String>> methodsByType,
            Map<String, Set<String>> interfaceMethods,
            Map<String, Set<String>> embeddedInterfaces,
            Map<String, String> functionResultTypes
    ) {
        this.methodsByType = methodsByType;
        this.interfaceMethods = interfaceMethods;
        this.embeddedInterfaces = embeddedInterfaces;
        this.functionResultTypes = functionResultTypes;
    }

    static GoProjectTypes empty() {
        return new GoProjectTypes(Map.of(), Map.of(), Map.of(), Map.of());
    }

    static GoProjectTypes collect(List<Path> goFiles) {
        Map<String, Set<String>> methodsByType = new HashMap<>();
        Map<String, Set<String>> interfaceMethods = new HashMap<>();
        Map<String, Set<String>> embeddedInterfaces = new HashMap<>();
        Map<String, String> functionResultTypes = new HashMap<>();
        goFiles.parallelStream()
                .map(GoProjectTypes::collectFile)
                .toList()
                .forEach(collector -> {
                    mergeSets(methodsByType, collector.methodsByType);
                    mergeSets(interfaceMethods, collector.interfaceMethods);
                    mergeSets(embeddedInterfaces, collector.embeddedInterfaces);
                    collector.functionResultTypes.forEach(functionResultTypes::putIfAbsent);
                });
        return new GoProjectTypes(methodsByType, interfaceMethods, embeddedInterfaces, functionResultTypes);
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

    private static void mergeSets(Map<String, Set<String>> target, Map<String, Set<String>> source) {
        source.forEach((key, values) -> target.computeIfAbsent(key, ignored -> new HashSet<>()).addAll(values));
    }

    static ParseTree parse(String content) {
        return AntlrParsing.parse(new GoLexer(CharStreams.fromString(content)), GoParser::new, GoParser::sourceFile);
    }

    Map<String, String> functionResultTypes() {
        return functionResultTypes;
    }

    Set<String> supertypes(String typeName) {
        Set<String> result = new LinkedHashSet<>(assignableTypes(typeName));
        result.remove(GoTypes.searchableName(typeName));
        return result;
    }

    Set<String> assignableTypes(String declaredType) {
        String name = GoTypes.searchableName(declaredType);
        if (name == null || name.isBlank()) {
            return Set.of();
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(name);
        if (GoTypes.isComposite(name)) {
            return result;
        }
        if (interfaceMethods.containsKey(name)) {
            result.addAll(embeddedClosure(name));
            return result;
        }

        Set<String> methods = methodsByType.getOrDefault(name, Set.of());
        for (String interfaceName : interfaceMethods.keySet()) {
            Set<String> required = requiredMethods(interfaceName, new HashSet<>());
            if (!required.isEmpty() && methods.containsAll(required)) {
                result.add(interfaceName);
            }
        }
        return result;
    }

    private Set<String> requiredMethods(String interfaceName, Set<String> visited) {
        if (!visited.add(interfaceName)) {
            return Set.of();
        }
        Set<String> methods = new HashSet<>(interfaceMethods.getOrDefault(interfaceName, Set.of()));
        for (String embedded : embeddedInterfaces.getOrDefault(interfaceName, Set.of())) {
            methods.addAll(requiredMethods(embedded, visited));
        }
        return methods;
    }

    private Set<String> embeddedClosure(String interfaceName) {
        Set<String> result = new LinkedHashSet<>();
        for (String embedded : embeddedInterfaces.getOrDefault(interfaceName, Set.of())) {
            if (result.add(embedded)) {
                result.addAll(embeddedClosure(embedded));
            }
        }
        return result;
    }

    private static final class Collector extends GoParserBaseListener {
        private final Map<String, Set<String>> methodsByType = new HashMap<>();
        private final Map<String, Set<String>> interfaceMethods = new HashMap<>();
        private final Map<String, Set<String>> embeddedInterfaces = new HashMap<>();
        private final Map<String, String> functionResultTypes = new HashMap<>();

        @Override
        public void enterTypeDef(GoParser.TypeDefContext ctx) {
            GoParser.TypeLitContext typeLit = ctx.type_() == null ? null : ctx.type_().typeLit();
            if (typeLit == null || typeLit.interfaceType() == null) {
                return;
            }
            String name = ctx.IDENTIFIER().getText();
            Set<String> methods = interfaceMethods.computeIfAbsent(name, ignored -> new HashSet<>());
            for (GoParser.MethodSpecContext method : typeLit.interfaceType().methodSpec()) {
                methods.add(method.IDENTIFIER().getText());
            }
            for (GoParser.TypeElementContext element : typeLit.interfaceType().typeElement()) {
                if (element.typeTerm().size() == 1 && element.typeTerm(0).UNDERLYING() == null) {
                    String embedded = GoTypes.searchableName(element.typeTerm(0).getText());
                    embeddedInterfaces.computeIfAbsent(name, ignored -> new HashSet<>()).add(embedded);
                }
            }
        }

        @Override
        public void enterMethodDecl(GoParser.MethodDeclContext ctx) {
            String receiverType = receiverTypeName(ctx.receiver());
            if (receiverType != null) {
                methodsByType.computeIfAbsent(receiverType, ignored -> new HashSet<>()).add(ctx.IDENTIFIER().getText());
            }
        }

        @Override
        public void enterFunctionDecl(GoParser.FunctionDeclContext ctx) {
            String resultType = firstResultType(ctx.signature());
            if (resultType != null) {
                functionResultTypes.putIfAbsent(ctx.IDENTIFIER().getText(), resultType);
            }
        }
    }

    static String receiverTypeName(GoParser.ReceiverContext receiver) {
        if (receiver == null
                || receiver.parameters() == null
                || receiver.parameters().parameterDecl().isEmpty()
                || receiver.parameters().parameterDecl(0).type_() == null) {
            return null;
        }
        return GoTypes.searchableName(receiver.parameters().parameterDecl(0).type_().getText());
    }

    static String firstResultType(GoParser.SignatureContext signature) {
        if (signature == null || signature.result() == null) {
            return null;
        }
        GoParser.ResultContext result = signature.result();
        if (result.type_() != null) {
            return GoTypes.normalize(result.type_().getText());
        }
        if (result.parameters() != null && !result.parameters().parameterDecl().isEmpty()) {
            GoParser.ParameterDeclContext first = result.parameters().parameterDecl(0);
            return first.type_() == null ? null : GoTypes.normalize(first.type_().getText());
        }
        return null;
    }
}
