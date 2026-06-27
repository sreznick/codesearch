package org.codesearch.java;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.MMapDirectory;
import org.codesearch.core.EntityKind;
import org.codesearch.core.LanguageModule;
import org.codesearch.core.SearchQuery;
import org.codesearch.core.SearchResult;
import org.codesearch.core.SearchTarget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class JavaSearchService {
    public record SearchResponse(long totalHits, List<SearchResult> results) {}
    public record IndexStats(long totalEntities, long totalFiles, Map<EntityKind, Long> entitiesByKind) {}
    private static final int MAX_RESULTS_TO_SCAN = 100000;

    private final Path indexPath;
    private final LanguageModule languageModule;

    public JavaSearchService(Path indexPath) {
        this(indexPath, new JavaLanguageModule());
    }

    public JavaSearchService(Path indexPath, LanguageModule languageModule) {
        this.indexPath = indexPath;
        this.languageModule = languageModule;
    }

    public List<SearchResult> search(SearchQuery searchQuery) throws IOException {
        return searchWithMetadata(searchQuery).results();
    }

    public SearchResponse searchWithMetadata(SearchQuery searchQuery) throws IOException {
        validateQuery(searchQuery);

        if (searchQuery.language() != null && !languageModule.language().equals(searchQuery.language())) {
            return new SearchResponse(0, List.of());
        }

        validateIndexPath();

        try (MMapDirectory directory = new MMapDirectory(indexPath);
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(buildQuery(searchQuery), MAX_RESULTS_TO_SCAN);

            List<SearchResult> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                SearchResult result = new SearchResult(JavaDocumentMapper.toCodeEntity(document, languageModule.language()), scoreDoc.score);
                if (matchesLanguage(result) && matchesPathFilter(result, searchQuery.pathFilter())) {
                    results.add(result);
                }
            }

            return new SearchResponse(results.size(), results.stream().limit(searchQuery.limit()).toList());
        } catch (IndexNotFoundException e) {
            throw new IndexUnavailableException("Индекс пуст или поврежден: " + indexPath, e);
        } catch (CorruptIndexException e) {
            throw new IndexUnavailableException("Индекс поврежден: " + indexPath, e);
        } catch (IOException e) {
            throw new IndexUnavailableException("Не удалось прочитать индекс, возможно, он поврежден: " + indexPath, e);
        }
    }

    public SearchResponse searchContaining(String text, EntityKind kind, String language, boolean caseSensitive, int limit, String pathFilter) throws IOException {
        validateContainsQuery(text, kind, language, limit);

        if (language != null && !languageModule.language().equals(language)) {
            return new SearchResponse(0, List.of());
        }

        validateIndexPath();

        String needle = caseSensitive ? text.trim() : text.trim().toLowerCase();
        try (MMapDirectory directory = new MMapDirectory(indexPath);
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(new MatchAllDocsQuery(), MAX_RESULTS_TO_SCAN);

            List<SearchResult> matches = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                SearchResult result = new SearchResult(JavaDocumentMapper.toCodeEntity(document, languageModule.language()), scoreDoc.score);
                if (matchesLanguage(result)
                        && matchesKind(result, kind)
                        && matchesPathFilter(result, pathFilter)
                        && containsText(result, needle, caseSensitive)) {
                    matches.add(result);
                }
            }

            return new SearchResponse(matches.size(), matches.stream().limit(limit).toList());
        } catch (IndexNotFoundException e) {
            throw new IndexUnavailableException("Индекс пуст или поврежден: " + indexPath, e);
        } catch (CorruptIndexException e) {
            throw new IndexUnavailableException("Индекс поврежден: " + indexPath, e);
        } catch (IOException e) {
            throw new IndexUnavailableException("Не удалось прочитать индекс, возможно, он поврежден: " + indexPath, e);
        }
    }

    public SearchResponse searchAssignableVariables(String type, String language, boolean caseSensitive, int limit, String pathFilter) throws IOException {
        validateAssignableTypeQuery(type, language, limit);

        if (language != null && !languageModule.language().equals(language)) {
            return new SearchResponse(0, List.of());
        }

        validateIndexPath();

        String searchableType = JavaTypeResolver.searchableTypeName(type);
        String value = caseSensitive ? searchableType : searchableType.toLowerCase();
        String field = caseSensitive ? JavaIndexFields.ASSIGNABLE_TYPE : JavaIndexFields.ASSIGNABLE_TYPE_LOWERCASE;

        Query query = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(field, value)), BooleanClause.Occur.MUST)
                .add(variableKindQuery(), BooleanClause.Occur.MUST)
                .build();

        try (MMapDirectory directory = new MMapDirectory(indexPath);
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(query, MAX_RESULTS_TO_SCAN);

            List<SearchResult> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                SearchResult result = new SearchResult(JavaDocumentMapper.toCodeEntity(document, languageModule.language()), scoreDoc.score);
                if (matchesLanguage(result) && matchesPathFilter(result, pathFilter)) {
                    results.add(result);
                }
            }

            return new SearchResponse(results.size(), results.stream().limit(limit).toList());
        } catch (IndexNotFoundException e) {
            throw new IndexUnavailableException("Индекс пуст или поврежден: " + indexPath, e);
        } catch (CorruptIndexException e) {
            throw new IndexUnavailableException("Индекс поврежден: " + indexPath, e);
        } catch (IOException e) {
            throw new IndexUnavailableException("Не удалось прочитать индекс, возможно, он поврежден: " + indexPath, e);
        }
    }

    public IndexStats stats(String pathFilter) throws IOException {
        validateIndexPath();

        try (MMapDirectory directory = new MMapDirectory(indexPath);
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(new MatchAllDocsQuery(), MAX_RESULTS_TO_SCAN);

            Set<String> files = new HashSet<>();
            Map<EntityKind, Long> counts = new EnumMap<>(EntityKind.class);
            long totalEntities = 0;

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                String language = document.get(JavaIndexFields.LANGUAGE);
                String documentLanguage = language == null || language.isBlank() ? JavaLanguageModule.LANGUAGE : language;
                if (!languageModule.language().equals(documentLanguage)) {
                    continue;
                }
                String file = document.get(JavaIndexFields.FILE);
                if (pathFilter != null && (file == null || !file.contains(pathFilter))) {
                    continue;
                }

                totalEntities++;
                if (file != null && !file.isBlank()) {
                    files.add(file);
                }

                EntityKind kind = EntityKind.fromValue(document.get(JavaIndexFields.TYPE));
                counts.merge(kind, 1L, Long::sum);
            }

            return new IndexStats(totalEntities, files.size(), Map.copyOf(counts));
        } catch (IndexNotFoundException e) {
            throw new IndexUnavailableException("Индекс пуст или поврежден: " + indexPath, e);
        } catch (CorruptIndexException e) {
            throw new IndexUnavailableException("Индекс поврежден: " + indexPath, e);
        } catch (IOException e) {
            throw new IndexUnavailableException("Не удалось прочитать индекс, возможно, он поврежден: " + indexPath, e);
        }
    }

    private Query variableKindQuery() {
        return new BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(1)
                .add(new TermQuery(new Term(JavaIndexFields.TYPE, EntityKind.FIELD.legacyJavaType())), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term(JavaIndexFields.TYPE, EntityKind.LOCAL_VARIABLE.legacyJavaType())), BooleanClause.Occur.SHOULD)
                .build();
    }

    private boolean matchesKind(SearchResult result, EntityKind kind) {
        return kind == null || result.entity().kind() == kind;
    }

    private boolean matchesLanguage(SearchResult result) {
        return languageModule.language().equals(result.entity().language());
    }

    private boolean containsText(SearchResult result, String needle, boolean caseSensitive) {
        String haystack = result.entity().content();
        if (!caseSensitive) {
            haystack = haystack.toLowerCase();
        }
        return haystack.contains(needle);
    }

    private boolean matchesPathFilter(SearchResult result, String pathFilter) {
        return pathFilter == null || result.entity().location().filePath().contains(pathFilter);
    }

    private void validateIndexPath() throws IOException {
        if (!Files.exists(indexPath)) {
            throw new IndexUnavailableException("Индекс не найден: " + indexPath);
        }
        if (!Files.isDirectory(indexPath)) {
            throw new IndexUnavailableException("Путь индекса не является директорией: " + indexPath);
        }
        if (isDirectoryEmpty(indexPath)) {
            throw new IndexUnavailableException("Индекс пуст: " + indexPath);
        }
    }

    private boolean isDirectoryEmpty(Path path) throws IndexUnavailableException {
        try (Stream<Path> files = Files.list(path)) {
            return files.findAny().isEmpty();
        } catch (IOException e) {
            throw new IndexUnavailableException("Не удалось прочитать директорию индекса: " + path, e);
        }
    }

    private Query buildQuery(SearchQuery searchQuery) {
        EntityKind kind = searchQuery.kind();

        return new BooleanQuery.Builder()
                .add(targetQuery(searchQuery), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term(JavaIndexFields.TYPE, kind.legacyJavaType())), BooleanClause.Occur.MUST)
                .build();
    }

    private Query targetQuery(SearchQuery searchQuery) {
        String value = searchQuery.caseSensitive() ? searchQuery.text() : searchQuery.text().toLowerCase();

        if (searchQuery.target() == SearchTarget.CONTENT) {
            return contentQuery(resolveField(searchQuery), value, searchQuery.fuzzy());
        }
        if (searchQuery.target() != SearchTarget.DECLARED_TYPE) {
            throw new IllegalArgumentException("Unsupported search target for metadata search: " + searchQuery.target());
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(1)
                .add(contentQuery(resolveDeclaredTypeField(searchQuery), value, searchQuery.fuzzy()), BooleanClause.Occur.SHOULD)
                .add(contentQuery(resolveLegacyDeclaredTypeField(searchQuery), value, searchQuery.fuzzy()), BooleanClause.Occur.SHOULD);
        return builder.build();
    }

    private String resolveField(SearchQuery searchQuery) {
        return switch (searchQuery.target()) {
            case CONTENT -> searchQuery.caseSensitive() ? JavaIndexFields.CONTENT : JavaIndexFields.CONTENT_LOWERCASE;
            case DECLARED_TYPE -> resolveDeclaredTypeField(searchQuery);
            case ASSIGNABLE_TYPE -> searchQuery.caseSensitive() ? JavaIndexFields.ASSIGNABLE_TYPE : JavaIndexFields.ASSIGNABLE_TYPE_LOWERCASE;
        };
    }

    private String resolveDeclaredTypeField(SearchQuery searchQuery) {
        return searchQuery.caseSensitive() ? JavaIndexFields.DECLARED_TYPE : JavaIndexFields.DECLARED_TYPE_LOWERCASE;
    }

    private String resolveLegacyDeclaredTypeField(SearchQuery searchQuery) {
        return searchQuery.caseSensitive() ? JavaIndexFields.LEGACY_VAR_TYPE : JavaIndexFields.LEGACY_VAR_TYPE_LOWERCASE;
    }

    private Query contentQuery(String field, String value, boolean fuzzy) {
        Term term = new Term(field, value);
        return fuzzy ? new FuzzyQuery(term, 2) : new TermQuery(term);
    }

    private void validateQuery(SearchQuery searchQuery) {
        if (searchQuery.kind() == null) {
            throw new IllegalArgumentException(languageModule.language() + " search requires entity kind");
        }
        if (searchQuery.target() == SearchTarget.ASSIGNABLE_TYPE) {
            throw new IllegalArgumentException("Assignable type search uses a dedicated variable search");
        }
        if (!languageModule.supportedEntityKinds().contains(searchQuery.kind())) {
            throw new IllegalArgumentException(languageModule.language() + " search does not support entity kind: " + searchQuery.kind());
        }
        if (searchQuery.target() == SearchTarget.DECLARED_TYPE
                && searchQuery.kind() != EntityKind.FIELD
                && searchQuery.kind() != EntityKind.LOCAL_VARIABLE
                && searchQuery.kind() != EntityKind.METHOD) {
            throw new IllegalArgumentException("Declared type search is supported only for fields, local variables and methods");
        }
    }

    private void validateContainsQuery(String text, EntityKind kind, String language, int limit) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Query text must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        if (language != null && !languageModule.language().equals(language)) {
            return;
        }
        if (kind != null && !languageModule.supportedEntityKinds().contains(kind)) {
            throw new IllegalArgumentException(languageModule.language() + " search does not support entity kind: " + kind);
        }
    }

    private void validateAssignableTypeQuery(String type, String language, int limit) {
        if (!JavaLanguageModule.LANGUAGE.equals(languageModule.language())) {
            throw new IllegalArgumentException("Assignable type search is supported only for java");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Assignable type must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        if (language != null && !languageModule.language().equals(language)) {
            return;
        }
    }

    public static class IndexUnavailableException extends IOException {
        public IndexUnavailableException(String message) {
            super(message);
        }

        public IndexUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
