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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JavaSearchService {
    public record SearchResponse(long totalHits, List<SearchResult> results) {}

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
            TopDocs topDocs = searcher.search(buildQuery(searchQuery), searchQuery.limit());

            List<SearchResult> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = searcher.storedFields().document(scoreDoc.doc);
                results.add(new SearchResult(JavaDocumentMapper.toCodeEntity(document), scoreDoc.score));
            }

            return new SearchResponse(topDocs.totalHits.value(), results);
        }
    }

    private Query buildQuery(SearchQuery searchQuery) {
        EntityKind kind = searchQuery.kind();
        String field = searchQuery.caseSensitive() ? "content" : "content_lowercase";
        String value = searchQuery.caseSensitive() ? searchQuery.text() : searchQuery.text().toLowerCase();

        return new BooleanQuery.Builder()
                .add(contentQuery(field, value, searchQuery.fuzzy()), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term("type", kind.legacyJavaType())), BooleanClause.Occur.MUST)
                .build();
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
    }
}
