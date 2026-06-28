package org.example.extractors;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaAnnotationExtractor {
    private static final Pattern ANNOTATION = Pattern.compile("@([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)");
    private static final Pattern CLASS_DECLARATION = Pattern.compile(".*\\b(class|interface|enum)\\s+([A-Za-z_$][\\w$]*).*");
    private static final Pattern METHOD_DECLARATION = Pattern.compile(".*\\b([A-Za-z_$][\\w$]*)\\s*\\([^;]*\\)\\s*(?:throws\\s+[^\\{]+)?(?:\\{|;).*");
    private static final Pattern FIELD_WITH_MODIFIER_DECLARATION = Pattern.compile(".*\\b(?:public|private|protected|static)\\b.*\\b([A-Za-z_$][\\w$]*)\\s*(?:=.+)?;\\s*$");
    private static final Pattern FIELD_DECLARATION = Pattern.compile(".*\\b([A-Za-z_$][\\w$]*)\\s*(?:=.+)?;\\s*$");
    private static final Pattern LOCAL_VARIABLE_DECLARATION = Pattern.compile(".*\\b(?:var|[A-Za-z_$][\\w$<>\\[\\].]*)\\s+([A-Za-z_$][\\w$]*)\\s*(?:=.+)?;\\s*$");

    public List<ExtractedAnnotation> extract(String content, String filePath) {
        String[] lines = content.split("\\R", -1);
        List<ExtractedAnnotation> annotations = new ArrayList<>();

        for (int index = 0; index < lines.length; index++) {
            Matcher matcher = ANNOTATION.matcher(lines[index]);
            while (matcher.find()) {
                String annotationName = simpleName(matcher.group(1));
                AnnotationTarget target = resolveTarget(lines, index, matcher.end());
                annotations.add(new ExtractedAnnotation(
                        filePath,
                        index + 1,
                        annotationName,
                        target.kind(),
                        target.name()
                ));
            }
        }

        return annotations;
    }

    private AnnotationTarget resolveTarget(String[] lines, int annotationLineIndex, int annotationEndColumn) {
        String sameLineTarget = lines[annotationLineIndex].substring(annotationEndColumn).trim();
        AnnotationTarget target = parseTarget(sameLineTarget);
        if (!target.isUnknown()) {
            return target;
        }

        for (int index = annotationLineIndex + 1; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isBlank() || line.startsWith("@")) {
                continue;
            }
            return parseTarget(line);
        }

        return AnnotationTarget.unknown();
    }

    private AnnotationTarget parseTarget(String line) {
        Matcher classMatcher = CLASS_DECLARATION.matcher(line);
        if (classMatcher.matches()) {
            return new AnnotationTarget(capitalize(classMatcher.group(1)), classMatcher.group(2));
        }

        Matcher methodMatcher = METHOD_DECLARATION.matcher(line);
        if (methodMatcher.matches()) {
            return new AnnotationTarget("Method", methodMatcher.group(1));
        }

        Matcher fieldWithModifierMatcher = FIELD_WITH_MODIFIER_DECLARATION.matcher(line);
        if (fieldWithModifierMatcher.matches()) {
            return new AnnotationTarget("Field", fieldWithModifierMatcher.group(1));
        }

        Matcher localVariableMatcher = LOCAL_VARIABLE_DECLARATION.matcher(line);
        if (localVariableMatcher.matches()) {
            return new AnnotationTarget("LocalVariable", localVariableMatcher.group(1));
        }

        Matcher fieldMatcher = FIELD_DECLARATION.matcher(line);
        if (fieldMatcher.matches()) {
            return new AnnotationTarget("Field", fieldMatcher.group(1));
        }

        return AnnotationTarget.unknown();
    }

    private String simpleName(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    private String capitalize(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    public record ExtractedAnnotation(String file, int line, String annotationName, String targetKind, String targetName) {}

    private record AnnotationTarget(String kind, String name) {
        static AnnotationTarget unknown() {
            return new AnnotationTarget(null, null);
        }

        boolean isUnknown() {
            return kind == null || name == null;
        }
    }
}
