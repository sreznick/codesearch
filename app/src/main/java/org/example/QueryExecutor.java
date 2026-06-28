package org.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchQuery;
import org.codesearch.core.SearchResult;
import org.codesearch.java.JavaSearchService;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import static org.example.JavaSourceIndexer.indexJavaSources;

/**
 * Класс для выполнения запросов поиска по индексированным данным.
 * Поддерживает различные типы запросов, включая строки, классы, методы, интерфейсы и литералы.
 * Обрабатывает поисковые запросы с возможностью учета регистра и работы с неточными совпадениями.
 */
public class QueryExecutor {
    private static final Logger logger = LogManager.getLogger();
    private static final JavaSearchService SEARCH_SERVICE = new JavaSearchService(Paths.get("index"));

    public static StringBuilder logBuilder = new StringBuilder();


    public static void findStringConstants(String queryString, boolean isFuzzy, boolean isCaseSensitive) {
        findWithQuery(queryString, EntityKind.STRING_CONSTANT, isFuzzy, isCaseSensitive);
    }

    public static void findClass(String className, boolean isFuzzy, boolean isCaseSensitive) {
        findWithQuery(className, EntityKind.CLASS, isFuzzy, isCaseSensitive);
    }

    public static void findMethod(String methodName, boolean isFuzzy, boolean isCaseSensitive) {
        findWithQuery(methodName, EntityKind.METHOD, isFuzzy, isCaseSensitive);
    }

    public static void findInterface(String interfaceName, boolean isFuzzy, boolean isCaseSensitive) {
        findWithQuery(interfaceName, EntityKind.INTERFACE, isFuzzy, isCaseSensitive);
    }

    public static void findField(String fieldName, boolean isFuzzy, boolean isCaseSensitive) {
        findWithQuery(fieldName, EntityKind.FIELD, isFuzzy, isCaseSensitive);
    }

    public static void findLocalVariable(String variableName, boolean isFuzzy, boolean isCaseSensitive) {
        findWithQuery(variableName, EntityKind.LOCAL_VARIABLE, isFuzzy, isCaseSensitive);
    }

    public static void findLiteral(String literalValue, String type, boolean isFuzzy, boolean isCaseSensitive) {
        findWithQuery(literalValue, EntityKind.fromValue(type), isFuzzy, isCaseSensitive);
    }

    private static void findWithQuery(String queryString, EntityKind kind, boolean isFuzzy, boolean isCaseSensitive) {
        logQueryStart(queryString, kind.legacyJavaType(), isFuzzy, isCaseSensitive);
        if (queryString.isEmpty()) {
            logQuerySummary(queryString, isFuzzy, 0);
            return;
        }

        try {
            JavaSearchService.SearchResponse response = SEARCH_SERVICE.searchWithMetadata(
                    SearchQuery.builder(queryString, kind, "java")
                            .fuzzy(isFuzzy)
                            .caseSensitive(isCaseSensitive)
                            .limit(100000)
                            .build()
            );
            logQuerySummary(queryString, isFuzzy, response.totalHits());
            logResults(kind, response.results());
        } catch (IOException e) {
            String errorMessage = "Ошибка при выполнении запроса: " + e.getMessage();
            logger.error(errorMessage, e);
            logBuilder.append(errorMessage).append("\n");
        }
    }

    private static void logQueryStart(String queryString, String type, boolean isFuzzy, boolean isCaseSensitive) {
        String queryTypeMessage = isFuzzy ? " (с неточностями)" : "";
        String logMessage = "Запрос на " + type + queryTypeMessage + ": " + queryString + " (учет регистра: " + isCaseSensitive + ")";
        logger.info(logMessage);
        logBuilder.append(logMessage).append("\n");
    }

    private static void logQuerySummary(String queryString, boolean isFuzzy, long totalHits) {
        String logMessage = "Найдено совпадений " + (isFuzzy ? "с" : "c") + " " + queryString + ": " + totalHits;
        logger.info(logMessage);
        logBuilder.append(logMessage).append("\n");
    }

    private static void logResults(EntityKind kind, List<SearchResult> results) {
        for (SearchResult result : results) {
            CodeEntity entity = result.entity();
            String logMessage = String.format(
                    "%s: %s, Файл: %s, Строка: %d",
                    kind.legacyJavaType(),
                    entity.content(),
                    entity.location().filePath(),
                    entity.location().line()
            );

            if (kind == EntityKind.FIELD || kind == EntityKind.LOCAL_VARIABLE) {
                logMessage = String.format(
                        "%s: %s, Тип: %s, Файл: %s, Строка: %d",
                        kind.legacyJavaType(),
                        entity.content(),
                        entity.declaredType(),
                        entity.location().filePath(),
                        entity.location().line()
                );
            }

            logger.info(logMessage);
            logBuilder.append(logMessage).append("\n");
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        indexJavaSources("src");

        findStringConstants("Test String", false, false);
        findClass("Stringdonstantuery", true, false);
        findMethod("toString", false, false);
        findInterface("TestInterface", false, false);
        findField("testField", false, false);
        findLocalVariable("content", false, false);
        findLiteral("100000", "IntegerLiteral", false, false);
        findLiteral("3.13F", "FloatLiteral", false, false);
        findLiteral("true", "BooleanLiteral", false, false);
        findLiteral("}", "CharLiteral", false, false);
        findLiteral("Hello, Lucene!", "StringLiteral", true, false);
    }
}
