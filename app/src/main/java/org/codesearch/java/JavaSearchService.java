package org.codesearch.java;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        }
    }

    private boolean matchesPathFilter(SearchResult result, String pathFilter) {
        return pathFilter == null || result.entity().location().filePath().contains(pathFilter);
    }

    private Query buildQuery(SearchQuery searchQuery) {
        EntityKind kind = searchQuery.kind();
        String field = resolveField(searchQuery);
        String value = searchQuery.caseSensitive() ? searchQuery.text() : searchQuery.text().toLowerCase();

        return new BooleanQuery.Builder()
                .add(contentQuery(field, value, searchQuery.fuzzy()), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term("type", kind.legacyJavaType())), BooleanClause.Occur.MUST)
                .build();
    }

    private String resolveField(SearchQuery searchQuery) {
        return switch (searchQuery.target()) {
            case CONTENT -> searchQuery.caseSensitive() ? "content" : "content_lowercase";
            case DECLARED_TYPE -> searchQuery.caseSensitive() ? "varType" : "varType_lowercase";
        };
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
                && searchQuery.kind() != EntityKind.LOCAL_VARIABLE) {
            throw new IllegalArgumentException("Declared type search is supported only for fields and local variables");
        }
    }
}
