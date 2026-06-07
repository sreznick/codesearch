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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.MMapDirectory;
import org.codesearch.core.EntityKind;
import org.codesearch.core.SearchQuery;
import org.codesearch.core.SearchResult;
import org.codesearch.core.SearchTarget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class JavaSearchService {
    public record SearchResponse(long totalHits, List<SearchResult> results) {}
    private static final int MAX_RESULTS_TO_SCAN = 100000;

    private final Path indexPath;
    private final JavaLanguageModule languageModule;

    public JavaSearchService(Path indexPath) {
        this(indexPath, new JavaLanguageModule());
    }

    JavaSearchService(Path indexPath, JavaLanguageModule languageModule) {
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
                SearchResult result = new SearchResult(JavaDocumentMapper.toCodeEntity(document), scoreDoc.score);
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
                .add(new TermQuery(new Term("type", kind.legacyJavaType())), BooleanClause.Occur.MUST)
                .build();
    }

    private Query targetQuery(SearchQuery searchQuery) {
        String value = searchQuery.caseSensitive() ? searchQuery.text() : searchQuery.text().toLowerCase();

        if (searchQuery.target() == SearchTarget.CONTENT) {
            return contentQuery(resolveField(searchQuery), value, searchQuery.fuzzy());
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(1)
                .add(contentQuery(resolveDeclaredTypeField(searchQuery), value, searchQuery.fuzzy()), BooleanClause.Occur.SHOULD)
                .add(contentQuery(resolveLegacyDeclaredTypeField(searchQuery), value, searchQuery.fuzzy()), BooleanClause.Occur.SHOULD);
        return builder.build();
    }

    private String resolveField(SearchQuery searchQuery) {
        return switch (searchQuery.target()) {
            case CONTENT -> searchQuery.caseSensitive() ? "content" : "content_lowercase";
            case DECLARED_TYPE -> resolveDeclaredTypeField(searchQuery);
        };
    }

    private String resolveDeclaredTypeField(SearchQuery searchQuery) {
        return searchQuery.caseSensitive() ? "declaredType" : "declaredType_lowercase";
    }

    private String resolveLegacyDeclaredTypeField(SearchQuery searchQuery) {
        return searchQuery.caseSensitive() ? "varType" : "varType_lowercase";
    }

    private Query contentQuery(String field, String value, boolean fuzzy) {
        Term term = new Term(field, value);
        return fuzzy ? new FuzzyQuery(term, 2) : new TermQuery(term);
    }

    private void validateQuery(SearchQuery searchQuery) {
        if (searchQuery.kind() == null) {
            throw new IllegalArgumentException("Java search requires entity kind");
        }
        if (!languageModule.supportedEntityKinds().contains(searchQuery.kind())) {
            throw new IllegalArgumentException("Java search does not support entity kind: " + searchQuery.kind());
        }
        if (searchQuery.target() == SearchTarget.DECLARED_TYPE
                && searchQuery.kind() != EntityKind.FIELD
                && searchQuery.kind() != EntityKind.LOCAL_VARIABLE
                && searchQuery.kind() != EntityKind.METHOD) {
            throw new IllegalArgumentException("Declared type search is supported only for fields, local variables and methods");
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
