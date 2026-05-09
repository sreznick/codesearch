package org.codesearch.java;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.EntityLocation;
import org.example.JavaBaseListener;
import org.example.JavaLexer;
import org.example.JavaParser;
import org.example.extractors.JavaClassExtractor;
import org.example.extractors.JavaFieldExtractor;
import org.example.extractors.JavaInterfaceExtractor;
import org.example.extractors.JavaLiteralExtractor;
import org.example.extractors.JavaLocalVariableExtractor;
import org.example.extractors.JavaMethodExtractor;
import org.example.extractors.JavaStringExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JavaEntityExtractor {
    public List<CodeEntity> extractEntities(Path file) throws Exception {
        String content = Files.readString(file);
        String filePath = file.toString();

        List<CodeEntity> entities = new ArrayList<>();
        entities.addAll(extractStringConstants(content, filePath));
        entities.addAll(extractClasses(content, filePath));
        entities.addAll(extractMethods(content, filePath));
        entities.addAll(extractInterfaces(content, filePath));
        entities.addAll(extractFields(content, filePath));
        entities.addAll(extractLocalVariables(content, filePath));
        entities.addAll(extractLiterals(content, filePath));
        return entities;
    }

    private List<CodeEntity> extractStringConstants(String content, String filePath) {
        JavaStringExtractor extractor = new JavaStringExtractor();
        extractor.setCurrentFile(filePath);

        return parse(content, extractor, parsed -> ((JavaStringExtractor) parsed).getStrings().stream()
                .map(value -> new CodeEntity(
                        EntityKind.STRING_CONSTANT,
                        value.getValue(),
                        new EntityLocation(value.getFile(), value.getLine(), 0),
                        JavaLanguageModule.LANGUAGE
                ))
                .toList());
    }

    private List<CodeEntity> extractClasses(String content, String filePath) {
        JavaClassExtractor extractor = new JavaClassExtractor();
        extractor.setCurrentFile(filePath);

        return parse(content, extractor, parsed -> ((JavaClassExtractor) parsed).getClasses().stream()
                .map(value -> new CodeEntity(
                        EntityKind.CLASS,
                        value.getClassName(),
                        new EntityLocation(value.getFile(), value.getLine(), 0),
                        JavaLanguageModule.LANGUAGE
                ))
                .toList());
    }

    private List<CodeEntity> extractMethods(String content, String filePath) {
        JavaMethodExtractor extractor = new JavaMethodExtractor();
        extractor.setCurrentFile(filePath);

        return parse(content, extractor, parsed -> ((JavaMethodExtractor) parsed).getMethods().stream()
                .map(value -> new CodeEntity(
                        EntityKind.METHOD,
                        value.getMethodName(),
                        new EntityLocation(value.getFile(), value.getLine(), 0),
                        JavaLanguageModule.LANGUAGE
                ))
                .toList());
    }

    private List<CodeEntity> extractInterfaces(String content, String filePath) {
        JavaInterfaceExtractor extractor = new JavaInterfaceExtractor();
        extractor.setCurrentFile(filePath);

        return parse(content, extractor, parsed -> ((JavaInterfaceExtractor) parsed).getInterfaces().stream()
                .map(value -> new CodeEntity(
                        EntityKind.INTERFACE,
                        value.getInterfaceName(),
                        new EntityLocation(value.getFile(), value.getLine(), 0),
                        JavaLanguageModule.LANGUAGE
                ))
                .toList());
    }

    private List<CodeEntity> extractFields(String content, String filePath) {
        JavaFieldExtractor extractor = new JavaFieldExtractor();
        extractor.setCurrentFile(filePath);

        return parse(content, extractor, parsed -> ((JavaFieldExtractor) parsed).getFields().stream()
                .map(value -> new CodeEntity(
                        EntityKind.FIELD,
                        value.getFieldName(),
                        new EntityLocation(value.getFile(), value.getLine(), 0),
                        JavaLanguageModule.LANGUAGE,
                        value.getType(),
                        Map.of("declaredType", value.getType())
                ))
                .toList());
    }

    private List<CodeEntity> extractLocalVariables(String content, String filePath) {
        JavaLocalVariableExtractor extractor = new JavaLocalVariableExtractor();
        extractor.setCurrentFile(filePath);

        return parse(content, extractor, parsed -> ((JavaLocalVariableExtractor) parsed).getVariables().stream()
                .map(value -> new CodeEntity(
                        EntityKind.LOCAL_VARIABLE,
                        value.getVariableName(),
                        new EntityLocation(value.getFile(), value.getLine(), 0),
                        JavaLanguageModule.LANGUAGE,
                        value.getType(),
                        Map.of("declaredType", value.getType())
                ))
                .toList());
    }

    private List<CodeEntity> extractLiterals(String content, String filePath) {
        JavaLiteralExtractor extractor = new JavaLiteralExtractor();
        extractor.setCurrentFile(filePath);

        return parse(content, extractor, parsed -> ((JavaLiteralExtractor) parsed).getLiterals().stream()
                .map(value -> new CodeEntity(
                        EntityKind.fromValue(value.getType()),
                        value.getValue(),
                        new EntityLocation(value.getFile(), value.getLine(), 0),
                        JavaLanguageModule.LANGUAGE
                ))
                .toList());
    }

    private <T> T parse(String content, JavaBaseListener extractor, ParseResultMapper<T> callback) {
        JavaLexer lexer = new JavaLexer(CharStreams.fromString(content));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);

        ParseTree tree = parser.compilationUnit();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(extractor, tree);

        return callback.map(extractor);
    }

    @FunctionalInterface
    private interface ParseResultMapper<T> {
        T map(JavaBaseListener extractor);
    }
}
