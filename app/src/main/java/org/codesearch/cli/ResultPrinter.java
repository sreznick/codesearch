package org.codesearch.cli;

import org.codesearch.cli.CommandLine.OutputOptions;
import org.codesearch.cli.CommandLine.SnippetOptions;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityAttributes;
import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchResponse;
import org.codesearch.core.SearchResult;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ResultPrinter {
    private record SnippetLine(int number, String text, boolean match) {}

    private ResultPrinter() {}

    static void print(PrintStream out, SearchResponse response, OutputOptions options, String assignableTargetType) {
        if (options.json()) {
            printJson(out, response, options, assignableTargetType);
        } else {
            printText(out, response, options, assignableTargetType);
        }
    }

    private static void printText(PrintStream out, SearchResponse response, OutputOptions options, String assignableTargetType) {
        out.println(Ansi.color(Ansi.BLUE, "Найдено совпадений: ") + response.totalHits());
        if (response.results().isEmpty()) {
            out.println(Ansi.color(Ansi.YELLOW, "Совпадений нет."));
            return;
        }

        boolean showLanguage = response.results().stream().map(result -> result.entity().language()).distinct().count() > 1;
        int index = 1;
        for (SearchResult result : response.results()) {
            out.println(formatResult(index++, result.entity(), showLanguage));
            if (options.explain()) {
                explain(result.entity(), assignableTargetType)
                        .forEach(line -> out.println(Ansi.color(Ansi.DIM, "   explain: ") + line));
            }
            printSnippet(out, result.entity(), options.snippet());
        }
    }

    private static String formatResult(int index, CodeEntity entity, boolean showLanguage) {
        String prefix = Ansi.color(Ansi.BLUE, index + ".");
        String kind = Ansi.color(Ansi.GREEN, entity.kind().displayName());
        String location = (showLanguage ? "[" + entity.language() + "] " : "")
                + entity.location().filePath() + ":" + entity.location().line();
        String file = Ansi.color(Ansi.DIM, location);

        StringBuilder line = new StringBuilder()
                .append(prefix).append(' ')
                .append(kind).append(' ')
                .append(entity.kind() == EntityKind.CALL ? callText(entity) : entity.content());

        if (entity.kind() == EntityKind.ANNOTATION || entity.kind() == EntityKind.DECORATOR) {
            String targetKind = entity.attribute(EntityAttributes.ANNOTATION_TARGET_KIND);
            String targetName = entity.attribute(EntityAttributes.ANNOTATION_TARGET_NAME);
            if (targetKind != null && targetName != null) {
                line.append(" on ").append(targetKind).append(' ').append(targetName);
            }
            return line.append("  ").append(file).toString();
        }

        if (entity.declaredType() != null && !entity.declaredType().isBlank() && !entity.kind().isTypeDeclaration()) {
            line.append("  [").append(entity.declaredType()).append(']');
        }
        if (entity.kind().isTypeDeclaration()) {
            appendIfPresent(line, " extends ", entity.attribute(EntityAttributes.EXTENDS_TYPES));
            appendIfPresent(line, " implements ", entity.kind() == EntityKind.STRUCT
                    ? String.join(", ", EntityAttributes.splitList(entity.attribute(EntityAttributes.SUPERTYPES)))
                    : entity.attribute(EntityAttributes.IMPLEMENTS_TYPES));
        }
        String containerKind = entity.attribute(EntityAttributes.CONTAINER_KIND);
        String containerName = entity.attribute(EntityAttributes.CONTAINER_NAME);
        if (containerKind != null && containerName != null) {
            line.append(" in ").append(containerKind).append(' ').append(containerName);
        }
        return line.append("  ").append(file).toString();
    }

    private static String callText(CodeEntity entity) {
        String qualifier = entity.attribute(EntityAttributes.CALL_QUALIFIER);
        String call = qualifier == null ? entity.content() : qualifier + "." + entity.content();
        return "constructor".equals(entity.attribute(EntityAttributes.CALL_KIND)) ? "new " + call : call;
    }

    private static void appendIfPresent(StringBuilder line, String label, String value) {
        if (value != null && !value.isEmpty()) {
            line.append(label).append(value);
        }
    }

    static List<String> explain(CodeEntity entity, String assignableTargetType) {
        List<String> lines = new ArrayList<>();
        if (assignableTargetType != null && entity.declaredType() != null) {
            String typeInference = entity.attribute(EntityAttributes.TYPE_INFERENCE);
            if (typeInference != null) {
                lines.add(typeInference + " -> " + assignableTargetType);
            } else if (entity.declaredType().equals(assignableTargetType)) {
                lines.add(entity.declaredType());
            } else {
                lines.add(entity.declaredType() + " -> " + assignableTargetType);
            }
        }

        String assignableTypes = entity.attribute(EntityAttributes.ASSIGNABLE_TYPES);
        if (assignableTypes != null) {
            lines.add("совместимые типы: " + assignableTypes.replace(",", ", "));
        }
        String supertypes = entity.attribute(EntityAttributes.SUPERTYPES);
        if (supertypes != null) {
            lines.add("надтипы: " + supertypes.replace(",", ", "));
        }
        if (lines.isEmpty() && entity.declaredType() != null && !entity.declaredType().isBlank()) {
            lines.add("объявленный тип: " + entity.declaredType());
        }
        return lines;
    }

    private static void printSnippet(PrintStream out, CodeEntity entity, SnippetOptions snippet) {
        if (!snippet.enabled()) {
            return;
        }
        try {
            for (SnippetLine line : readSnippet(entity, snippet)) {
                out.printf("   %s %4d | %s%n", line.match() ? ">" : " ", line.number(), line.text());
            }
        } catch (IOException e) {
            out.println(Ansi.color(Ansi.DIM, "   snippet: ") + "не удалось прочитать " + entity.location().filePath());
        }
    }

    private static List<SnippetLine> readSnippet(CodeEntity entity, SnippetOptions snippet) throws IOException {
        List<String> fileLines = Files.readAllLines(Path.of(entity.location().filePath()));
        int matchLine = entity.location().line();
        if (matchLine > fileLines.size()) {
            return List.of();
        }
        int start = Math.max(1, matchLine - snippet.before());
        int end = Math.min(fileLines.size(), matchLine + snippet.after());
        List<SnippetLine> lines = new ArrayList<>();
        for (int number = start; number <= end; number++) {
            lines.add(new SnippetLine(number, fileLines.get(number - 1), number == matchLine));
        }
        return lines;
    }

    private static void printJson(PrintStream out, SearchResponse response, OutputOptions options, String assignableTargetType) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"totalHits\": ").append(response.totalHits()).append(",\n");
        json.append("  \"results\": [");
        List<SearchResult> results = response.results();
        if (!results.isEmpty()) {
            json.append('\n');
        }
        for (int i = 0; i < results.size(); i++) {
            appendJsonResult(json, results.get(i), options, assignableTargetType);
            json.append(i + 1 < results.size() ? ",\n" : "\n");
        }
        json.append(results.isEmpty() ? "]\n" : "  ]\n");
        json.append('}');
        out.println(json);
    }

    private static void appendJsonResult(StringBuilder json, SearchResult result, OutputOptions options, String assignableTargetType) {
        CodeEntity entity = result.entity();
        json.append("    {\n");
        json.append("      \"kind\": ").append(Json.string(entity.kind().displayName())).append(",\n");
        json.append("      \"name\": ").append(Json.string(entity.content())).append(",\n");
        json.append("      \"language\": ").append(Json.string(entity.language())).append(",\n");
        json.append("      \"file\": ").append(Json.string(entity.location().filePath())).append(",\n");
        json.append("      \"line\": ").append(entity.location().line()).append(",\n");
        json.append("      \"column\": ").append(entity.location().column()).append(",\n");
        json.append("      \"declaredType\": ").append(Json.stringOrNull(entity.declaredType())).append(",\n");
        json.append("      \"score\": ").append(result.score()).append(",\n");
        json.append("      \"attributes\": ").append(Json.object(entity.attributes(), "      ")).append(",\n");
        List<String> explanation = options.explain() ? explain(entity, assignableTargetType) : List.of();
        json.append("      \"explanation\": ").append(Json.stringArray(explanation, "      "));
        if (options.snippet().enabled()) {
            json.append(",\n      \"snippet\": ").append(jsonSnippet(entity, options.snippet()));
        }
        json.append("\n    }");
    }

    private static String jsonSnippet(CodeEntity entity, SnippetOptions snippet) {
        List<SnippetLine> lines;
        try {
            lines = readSnippet(entity, snippet);
        } catch (IOException e) {
            lines = List.of();
        }
        if (lines.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < lines.size(); i++) {
            SnippetLine line = lines.get(i);
            json.append("        {\"line\": ").append(line.number())
                    .append(", \"match\": ").append(line.match())
                    .append(", \"text\": ").append(Json.string(line.text()))
                    .append('}')
                    .append(i + 1 < lines.size() ? ",\n" : "\n");
        }
        return json.append("      ]").toString();
    }

    static final class Json {
        private Json() {}

        static String object(Map<String, String> values, String indent) {
            if (values.isEmpty()) {
                return "{}";
            }
            List<Map.Entry<String, String>> entries = values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
            StringBuilder json = new StringBuilder("{\n");
            for (int i = 0; i < entries.size(); i++) {
                json.append(indent).append("  ")
                        .append(string(entries.get(i).getKey())).append(": ").append(string(entries.get(i).getValue()))
                        .append(i + 1 < entries.size() ? ",\n" : "\n");
            }
            return json.append(indent).append('}').toString();
        }

        static String stringArray(List<String> values, String indent) {
            if (values.isEmpty()) {
                return "[]";
            }
            StringBuilder json = new StringBuilder("[\n");
            for (int i = 0; i < values.size(); i++) {
                json.append(indent).append("  ").append(string(values.get(i))).append(i + 1 < values.size() ? ",\n" : "\n");
            }
            return json.append(indent).append(']').toString();
        }

        static String stringOrNull(String value) {
            return value == null ? "null" : string(value);
        }

        static String string(String value) {
            StringBuilder escaped = new StringBuilder("\"");
            for (char ch : value.toCharArray()) {
                switch (ch) {
                    case '"' -> escaped.append("\\\"");
                    case '\\' -> escaped.append("\\\\");
                    case '\b' -> escaped.append("\\b");
                    case '\f' -> escaped.append("\\f");
                    case '\n' -> escaped.append("\\n");
                    case '\r' -> escaped.append("\\r");
                    case '\t' -> escaped.append("\\t");
                    default -> {
                        if (ch < 0x20) {
                            escaped.append(String.format("\\u%04x", (int) ch));
                        } else {
                            escaped.append(ch);
                        }
                    }
                }
            }
            return escaped.append('"').toString();
        }
    }
}
