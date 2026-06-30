package org.codesearch.core;

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
import org.codesearch.plugin.LanguagePlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class LanguageSearchService {
    public record SearchResponse(long totalHits, List<SearchResult> results) {}

    private static final int MAX_RESULTS_TO_SCAN = 100000;

    private final Path indexPath;
    private final LanguagePlugin plugin;

    public LanguageSearchService(Path indexPath, LanguagePlugin plugin) {
        this.indexPath = indexPath;
        this.plugin = plugin;
    }

    public SearchResponse searchWithMetadata(SearchQuery searchQuery) throws IOException {
        validateQuery(searchQuery);

        if (searchQuery.language() != null && !plugin.language().equals(searchQuery.language())) {
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
                SearchResult result = new SearchResult(
                        DocumentMapper.toCodeEntity(document, plugin.language()),
                        scoreDoc.score
                );
                if (matchesPathFilter(result, searchQuery.pathFilter())) {
                    results.add(result);
                }
            }

            long totalHits = searchQuery.pathFilter() == null ? topDocs.totalHits.value() : results.size();
            return new SearchResponse(totalHits, results.stream().limit(searchQuery.limit()).toList());
        } catch (IndexNotFoundException e) {
            throw new IndexUnavailableException("Индекс пуст или поврежден: " + indexPath, e);
        } catch (CorruptIndexException e) {
            throw new IndexUnavailableException("Индекс поврежден: " + indexPath, e);
        } catch (IOException e) {
            throw new IndexUnavailableException("Не удалось прочитать индекс, возможно, он поврежден: " + indexPath, e);
        }
    }

    public SearchResponse searchContaining(
            String text,
            EntityKind kind,
            String language,
            boolean caseSensitive,
            int limit,
            String pathFilter
    ) throws IOException {
        validateContainsQuery(text, kind, language, limit);

        if (language != null && !plugin.language().equals(language)) {
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
                SearchResult result = new SearchResult(
                        DocumentMapper.toCodeEntity(document, plugin.language()),
                        scoreDoc.score
                );
                if (matchesKind(result, kind)
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

    public SearchResponse searchAssignableVariables(
            String type,
            String language,
            boolean caseSensitive,
            int limit,
            String pathFilter
    ) throws IOException {
        LanguageSearchCapabilities.AssignableTypeSearch assignable = plugin.searchCapabilities().assignableTypeSearch();
        if (assignable == null) {
            throw new IllegalArgumentException(
                    "Language '" + plugin.language() + "' does not support assignable type search"
            );
        }

        validateAssignableTypeQuery(type, language, limit);

        if (language != null && !plugin.language().equals(language)) {
            return new SearchResponse(0, List.of());
        }

        validateIndexPath();

        String searchableType = assignable.normalizeType(type);
        String value = caseSensitive ? searchableType : searchableType.toLowerCase();
        String field = caseSensitive ? IndexFields.ASSIGNABLE_TYPE : IndexFields.ASSIGNABLE_TYPE_LOWERCASE;

        Query query = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(field, value)), BooleanClause.Occur.MUST)
                .add(variableKindQuery(assignable.variableKinds()), BooleanClause.Occur.MUST)
                .build();

        try (MMapDirectory directory = new MMapDirectory(indexPath);
             IndexReader reader = DirectoryReader.open(directory)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(query, MAX_RESULTS_TO_SCAN);

            List<SearchResult> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                SearchResult result = new SearchResult(
                        DocumentMapper.toCodeEntity(document, plugin.language()),
                        scoreDoc.score
                );
                if (matchesPathFilter(result, pathFilter)) {
                    results.add(result);
                }
            }

            long totalHits = pathFilter == null ? topDocs.totalHits.value() : results.size();
            return new SearchResponse(totalHits, results.stream().limit(limit).toList());
        } catch (IndexNotFoundException e) {
            throw new IndexUnavailableException("Индекс пуст или поврежден: " + indexPath, e);
        } catch (CorruptIndexException e) {
            throw new IndexUnavailableException("Индекс поврежден: " + indexPath, e);
        } catch (IOException e) {
            throw new IndexUnavailableException("Не удалось прочитать индекс, возможно, он поврежден: " + indexPath, e);
        }
    }

    private Query variableKindQuery(Set<EntityKind> kinds) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder().setMinimumNumberShouldMatch(1);
        for (EntityKind kind : kinds) {
            builder.add(new TermQuery(new Term(IndexFields.TYPE, kind.legacyJavaType())), BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    private boolean matchesKind(SearchResult result, EntityKind kind) {
        return kind == null || result.entity().kind() == kind;
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
                .add(new TermQuery(new Term(IndexFields.TYPE, kind.legacyJavaType())), BooleanClause.Occur.MUST)
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

        return new BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(1)
                .add(contentQuery(resolveDeclaredTypeField(searchQuery), value, searchQuery.fuzzy()), BooleanClause.Occur.SHOULD)
                .add(contentQuery(resolveLegacyDeclaredTypeField(searchQuery), value, searchQuery.fuzzy()), BooleanClause.Occur.SHOULD)
                .build();
    }

    private String resolveField(SearchQuery searchQuery) {
        return switch (searchQuery.target()) {
            case CONTENT -> searchQuery.caseSensitive() ? IndexFields.CONTENT : IndexFields.CONTENT_LOWERCASE;
            case DECLARED_TYPE -> resolveDeclaredTypeField(searchQuery);
            case ASSIGNABLE_TYPE -> searchQuery.caseSensitive() ? IndexFields.ASSIGNABLE_TYPE : IndexFields.ASSIGNABLE_TYPE_LOWERCASE;
        };
    }

    private String resolveDeclaredTypeField(SearchQuery searchQuery) {
        return searchQuery.caseSensitive() ? IndexFields.DECLARED_TYPE : IndexFields.DECLARED_TYPE_LOWERCASE;
    }

    private String resolveLegacyDeclaredTypeField(SearchQuery searchQuery) {
        return searchQuery.caseSensitive() ? IndexFields.LEGACY_VAR_TYPE : IndexFields.LEGACY_VAR_TYPE_LOWERCASE;
    }

    private Query contentQuery(String field, String value, boolean fuzzy) {
        Term term = new Term(field, value);
        return fuzzy ? new FuzzyQuery(term, 2) : new TermQuery(term);
    }

    private void validateQuery(SearchQuery searchQuery) {
        LanguageSearchCapabilities capabilities = plugin.searchCapabilities();

        if (searchQuery.kind() == null) {
            throw new IllegalArgumentException(plugin.language() + " search requires entity kind");
        }
        if (searchQuery.target() == SearchTarget.ASSIGNABLE_TYPE) {
            throw new IllegalArgumentException("Assignable type search uses a dedicated variable search");
        }
        if (!plugin.supportedEntityKinds().contains(searchQuery.kind())) {
            throw new IllegalArgumentException(
                    plugin.language() + " search does not support entity kind: " + searchQuery.kind()
            );
        }
        if (searchQuery.target() == SearchTarget.DECLARED_TYPE
                && !capabilities.declaredTypeKinds().contains(searchQuery.kind())) {
            throw new IllegalArgumentException(
                    "Declared type search is not supported for kind: " + searchQuery.kind()
            );
        }
    }

    private void validateContainsQuery(String text, EntityKind kind, String language, int limit) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Query text must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        if (kind != null && !plugin.supportedEntityKinds().contains(kind)) {
            throw new IllegalArgumentException(
                    plugin.language() + " search does not support entity kind: " + kind
            );
        }
    }

    private void validateAssignableTypeQuery(String type, String language, int limit) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Assignable type must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
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
