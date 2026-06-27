package org.codesearch.java;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.EntityLocation;
import org.example.JavaBaseListener;
import org.example.JavaLexer;
import org.example.JavaParser;
import org.example.extractors.JavaAnnotationExtractor;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JavaEntityExtractor {
    private static final Logger logger = LogManager.getLogger(JavaEntityExtractor.class);

    private final JavaTypeHierarchy typeHierarchy;

    public JavaEntityExtractor() {
        this(JavaTypeHierarchy.empty());
    }

    JavaEntityExtractor(JavaTypeHierarchy typeHierarchy) {
        this.typeHierarchy = typeHierarchy;
    }

    public List<CodeEntity> extractEntities(Path file) throws IOException {
        String content = Files.readString(file);
        String filePath = file.toString();

        List<CodeEntity> entities = new ArrayList<>();
        entities.addAll(extractSafely(filePath, "string constants", () -> extractStringConstants(content, filePath)));
        entities.addAll(extractSafely(filePath, "annotations", () -> extractAnnotations(content, filePath)));
        entities.addAll(extractSafely(filePath, "classes", () -> extractClasses(content, filePath)));
        entities.addAll(extractSafely(filePath, "methods", () -> extractMethods(content, filePath)));
        entities.addAll(extractSafely(filePath, "interfaces", () -> extractInterfaces(content, filePath)));
        entities.addAll(extractSafely(filePath, "fields", () -> extractFields(content, filePath)));
        entities.addAll(extractSafely(filePath, "local variables", () -> extractLocalVariables(content, filePath)));
        entities.addAll(extractSafely(filePath, "literals", () -> extractLiterals(content, filePath)));
        List<JavaContainer> containers = extractSafely(filePath, "containers", () -> extractContainers(content));
        return addContainerAttributes(entities, containers);
    }

    private List<CodeEntity> extractAnnotations(String content, String filePath) {
        JavaAnnotationExtractor extractor = new JavaAnnotationExtractor();

        return extractor.extract(content, filePath).stream()
                .map(value -> new CodeEntity(
                        EntityKind.ANNOTATION,
                        value.annotationName(),
                        new EntityLocation(value.file(), value.line(), 0),
                        JavaLanguageModule.LANGUAGE,
                        null,
                        annotationAttributes(value)
                ))
                .toList();
    }

    private <T> List<T> extractSafely(String filePath, String extractionName, ExtractionStep<T> extractionStep) {
        try {
            return extractionStep.extract();
        } catch (RuntimeException e) {
            logger.debug("Не удалось извлечь {} из {}: {}", extractionName, filePath, e.getMessage(), e);
            return List.of();
        }
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
                .map(value -> typedEntity(
                        EntityKind.METHOD,
                        value.getMethodName(),
                        value.getFile(),
                        value.getLine(),
                        value.getReturnType(),
                        null
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
                .map(value -> typedEntity(
                        EntityKind.FIELD,
                        value.getFieldName(),
                        value.getFile(),
                        value.getLine(),
                        value.getType(),
                        null
                ))
                .toList());
    }

    private List<CodeEntity> extractLocalVariables(String content, String filePath) {
        JavaLocalVariableExtractor extractor = new JavaLocalVariableExtractor();
        extractor.setCurrentFile(filePath);

        return parse(content, extractor, parsed -> ((JavaLocalVariableExtractor) parsed).getVariables().stream()
                .map(value -> typedEntity(
                        EntityKind.LOCAL_VARIABLE,
                        value.getVariableName(),
                        value.getFile(),
                        value.getLine(),
                        value.getType(),
                        value.getInitializer()
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

    private List<JavaContainer> extractContainers(String content) {
        JavaContainerExtractor extractor = new JavaContainerExtractor();
        return parse(content, extractor, parsed -> ((JavaContainerExtractor) parsed).containers());
    }

    private <T> T parse(String content, JavaBaseListener extractor, ParseResultMapper<T> callback) {
        JavaLexer lexer = new JavaLexer(CharStreams.fromString(content));
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        parser.removeErrorListeners();

        ParseTree tree = parser.compilationUnit();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(extractor, tree);

        return callback.map(extractor);
    }

    private CodeEntity typedEntity(EntityKind kind, String content, String file, int line, String declaredType, String initializer) {
        String originalType = JavaTypeResolver.normalizeType(declaredType);
        String resolvedType = JavaTypeResolver.resolveDeclaredType(declaredType, initializer);
        return new CodeEntity(
                kind,
                content,
                new EntityLocation(file, line, 0),
                JavaLanguageModule.LANGUAGE,
                resolvedType,
                typedAttributes(originalType, resolvedType)
        );
    }

    private Map<String, String> typedAttributes(String originalType, String declaredType) {
        Map<String, String> attributes = new HashMap<>();
        if (declaredType != null && !declaredType.isBlank()) {
            attributes.put(JavaEntityAttributes.DECLARED_TYPE, declaredType);
            if ("var".equals(originalType) && !"var".equals(declaredType)) {
                attributes.put(JavaEntityAttributes.TYPE_INFERENCE, "var -> " + declaredType);
            }
            if (!"var".equals(declaredType)) {
                Set<String> assignableTypes = typeHierarchy.assignableTypes(declaredType);
                if (!assignableTypes.isEmpty()) {
                    attributes.put(JavaEntityAttributes.ASSIGNABLE_TYPES, JavaTypeResolver.serializeTypes(assignableTypes));
                }
            }
        }
        return attributes;
    }

    private Map<String, String> annotationAttributes(JavaAnnotationExtractor.ExtractedAnnotation annotation) {
        Map<String, String> attributes = new HashMap<>();
        if (annotation.targetKind() != null && annotation.targetName() != null) {
            attributes.put(JavaEntityAttributes.ANNOTATION_TARGET_KIND, annotation.targetKind());
            attributes.put(JavaEntityAttributes.ANNOTATION_TARGET_NAME, annotation.targetName());
        }
        return attributes;
    }

    private List<CodeEntity> addContainerAttributes(List<CodeEntity> entities, List<JavaContainer> containers) {
        if (containers.isEmpty()) {
            return entities;
        }

        return entities.stream()
                .map(entity -> withContainer(entity, containers))
                .toList();
    }

    private CodeEntity withContainer(CodeEntity entity, List<JavaContainer> containers) {
        if (entity.kind() == EntityKind.CLASS || entity.kind() == EntityKind.INTERFACE) {
            return entity;
        }

        JavaContainer container = containers.stream()
                .filter(value -> value.contains(entity.location().line()))
                .min((left, right) -> Integer.compare(left.length(), right.length()))
                .orElse(null);
        if (container == null) {
            return entity;
        }

        Map<String, String> attributes = new HashMap<>(entity.attributes());
        attributes.put(JavaEntityAttributes.CONTAINER_KIND, container.kind());
        attributes.put(JavaEntityAttributes.CONTAINER_NAME, container.name());
        return new CodeEntity(
                entity.kind(),
                entity.content(),
                entity.location(),
                entity.language(),
                entity.declaredType(),
                attributes
        );
    }

    private record JavaContainer(String kind, String name, int startLine, int endLine) {
        boolean contains(int line) {
            return line >= startLine && line <= endLine;
        }

        int length() {
            return endLine - startLine;
        }
    }

    private static final class JavaContainerExtractor extends JavaBaseListener {
        private final List<JavaContainer> containers = new ArrayList<>();

        private List<JavaContainer> containers() {
            return containers;
        }

        @Override
        public void enterClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
            containers.add(new JavaContainer(
                    "class",
                    ctx.Identifier().getText(),
                    ctx.getStart().getLine(),
                    ctx.getStop().getLine()
            ));
        }

        @Override
        public void enterInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
            containers.add(new JavaContainer(
                    "interface",
                    ctx.Identifier().getText(),
                    ctx.getStart().getLine(),
                    ctx.getStop().getLine()
            ));
        }
    }

    @FunctionalInterface
    private interface ParseResultMapper<T> {
        T map(JavaBaseListener extractor);
    }

    @FunctionalInterface
    private interface ExtractionStep<T> {
        List<T> extract();
    }
}
