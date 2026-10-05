package org.codesearch.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.util.Bits;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.MMapDirectory;
import org.codesearch.core.CodeEntity;
import org.codesearch.core.EntityKind;
import org.codesearch.core.IndexStats;
import org.codesearch.core.IndexUnavailableException;
import org.codesearch.core.MatchMode;
import org.codesearch.core.SearchRequest;
import org.codesearch.core.SearchResponse;
import org.codesearch.core.SearchResult;
import org.codesearch.core.SearchTarget;
import org.codesearch.index.IndexDocumentMapper;
import org.codesearch.index.IndexFields;
import org.codesearch.plugin.LanguagePlugin;
import org.codesearch.plugin.LanguageSearchCapabilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class LanguageSearchService {
    private static final int FUZZY_MAX_EDITS = 2;
    private static final float EXACT_MATCH_BOOST = 10f;

    private final Path indexPath;
    private final LanguagePlugin plugin;

    public LanguageSearchService(Path indexPath, LanguagePlugin plugin) {
        this.indexPath = indexPath;
        this.plugin = plugin;
    }

    public static void validate(LanguagePlugin plugin, SearchRequest request) {
        LanguageSearchCapabilities capabilities = plugin.searchCapabilities();
        String language = plugin.language();
        EntityKind kind = request.kind();

        if (kind != null && !plugin.supportedEntityKinds().contains(kind)) {
            throw new IllegalArgumentException(
                    "Язык " + language + " не поддерживает вид сущности: " + kind.key());
        }
        if (request.target() == SearchTarget.DECLARED_TYPE && !capabilities.declaredTypeKinds().contains(kind)) {
            throw new IllegalArgumentException(
                    "Язык " + language + " не поддерживает поиск по объявленному типу для: " + kind.key());
        }
        if (request.target() == SearchTarget.ASSIGNABLE_TYPE) {
            if (capabilities.assignableKinds().isEmpty()) {
                throw new IllegalArgumentException(
                        "Язык " + language + " не поддерживает поиск совместимых типов");
            }
            if (kind != null && !capabilities.assignableKinds().contains(kind)) {
                throw new IllegalArgumentException(
                        "Язык " + language + " не поддерживает поиск совместимых типов для: " + kind.key());
            }
        }
        if (request.target() == SearchTarget.SUPERTYPE) {
            if (capabilities.subtypeKinds().isEmpty()) {
                throw new IllegalArgumentException(
                        "Язык " + language + " не поддерживает поиск наследников");
            }
            if (kind != null && !capabilities.subtypeKinds().contains(kind)) {
                throw new IllegalArgumentException(
                        "Язык " + language + " не поддерживает поиск наследников для: " + kind.key());
            }
        }
    }

    public static boolean supports(LanguagePlugin plugin, SearchRequest request) {
        try {
            validate(plugin, request);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public SearchResponse search(SearchRequest request) throws IOException {
        validate(plugin, request);
        Query query = buildQuery(request);

        return withReader(reader -> {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(query, Math.max(1, reader.maxDoc()));
            StoredFields storedFields = searcher.storedFields();

            List<SearchResult> matches = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                CodeEntity entity = IndexDocumentMapper.toEntity(storedFields.document(scoreDoc.doc));
                if (matchesPathFilter(entity, request.pathFilter())) {
                    matches.add(new SearchResult(entity, scoreDoc.score));
                }
            }
            matches.sort(SearchResponse.RESULT_ORDER);
            return new SearchResponse(matches.size(), matches.stream().limit(request.limit()).toList());
        });
    }

    public IndexStats stats(String pathFilter) throws IOException {
        return withReader(reader -> {
            StoredFields storedFields = reader.storedFields();
            Bits liveDocs = MultiBits.getLiveDocs(reader);
            Set<String> files = new HashSet<>();
            Map<EntityKind, Long> counts = new EnumMap<>(EntityKind.class);
            long totalEntities = 0;

            for (int docId = 0; docId < reader.maxDoc(); docId++) {
                if (liveDocs != null && !liveDocs.get(docId)) {
                    continue;
                }
                Document document = storedFields.document(docId);
                String file = document.get(IndexFields.FILE);
                if (pathFilter != null && (file == null || !file.contains(pathFilter))) {
                    continue;
                }
                totalEntities++;
                files.add(file);
                counts.merge(EntityKind.fromValue(document.get(IndexFields.KIND)), 1L, Long::sum);
            }
            return new IndexStats(totalEntities, files.size(), counts);
        });
    }

    private Query buildQuery(SearchRequest request) {
        BooleanQuery.Builder query = new BooleanQuery.Builder();

        if (request.listsAllOfKind()) {
            query.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        } else {
            query.add(textQuery(request), BooleanClause.Occur.MUST);
        }

        if (request.kind() != null) {
            query.add(kindQuery(request.kind()), BooleanClause.Occur.FILTER);
        } else if (request.target() == SearchTarget.ASSIGNABLE_TYPE) {
            query.add(anyKindQuery(plugin.searchCapabilities().assignableKinds()), BooleanClause.Occur.FILTER);
        } else if (request.target() == SearchTarget.SUPERTYPE) {
            query.add(anyKindQuery(plugin.searchCapabilities().subtypeKinds()), BooleanClause.Occur.FILTER);
        }
        if (request.containerFilter() != null) {
            String container = IndexDocumentMapper.lower(request.containerFilter());
            query.add(new WildcardQuery(new Term(IndexFields.CONTAINER_NAME_LOWERCASE, "*" + escapeWildcard(container) + "*")),
                    BooleanClause.Occur.FILTER);
        }
        return query.build();
    }

    private Query textQuery(SearchRequest request) {
        String field = switch (request.target()) {
            case CONTENT -> IndexFields.CONTENT;
            case DECLARED_TYPE -> IndexFields.DECLARED_TYPE;
            case ASSIGNABLE_TYPE -> IndexFields.ASSIGNABLE_TYPE;
            case SUPERTYPE -> IndexFields.SUPERTYPE;
        };
        boolean typeRelation = request.target() == SearchTarget.ASSIGNABLE_TYPE || request.target() == SearchTarget.SUPERTYPE;
        String value = typeRelation ? plugin.searchCapabilities().normalizeType(request.text()) : request.text();
        if (!request.caseSensitive()) {
            field = IndexFields.lowercase(field);
            value = IndexDocumentMapper.lower(value);
        }

        MatchMode matchMode = typeRelation ? MatchMode.EXACT : request.matchMode();
        Term term = new Term(field, value);
        return switch (matchMode) {
            case EXACT -> new TermQuery(term);
            case FUZZY -> new FuzzyQuery(term, FUZZY_MAX_EDITS);
            case SUBSTRING -> new BooleanQuery.Builder()
                    .add(new WildcardQuery(new Term(field, "*" + escapeWildcard(value) + "*")), BooleanClause.Occur.MUST)
                    .add(new BoostQuery(new TermQuery(term), EXACT_MATCH_BOOST), BooleanClause.Occur.SHOULD)
                    .build();
        };
    }

    private static Query kindQuery(EntityKind kind) {
        return new TermQuery(new Term(IndexFields.KIND, kind.key()));
    }

    private static Query anyKindQuery(Set<EntityKind> kinds) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder().setMinimumNumberShouldMatch(1);
        kinds.forEach(kind -> builder.add(kindQuery(kind), BooleanClause.Occur.SHOULD));
        return builder.build();
    }

    private static String escapeWildcard(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char ch : value.toCharArray()) {
            if (ch == '*' || ch == '?' || ch == '\\') {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }

    private static boolean matchesPathFilter(CodeEntity entity, String pathFilter) {
        return pathFilter == null || entity.location().filePath().contains(pathFilter);
    }

    private <T> T withReader(ReaderCallback<T> callback) throws IOException {
        validateIndexPath();
        try (MMapDirectory directory = new MMapDirectory(indexPath);
             IndexReader reader = DirectoryReader.open(directory)) {
            return callback.apply(reader);
        } catch (IndexNotFoundException e) {
            throw new IndexUnavailableException("Индекс пуст или поврежден: " + indexPath, e);
        } catch (CorruptIndexException e) {
            throw new IndexUnavailableException("Индекс поврежден: " + indexPath, e);
        } catch (IOException e) {
            throw new IndexUnavailableException("Не удалось прочитать индекс, возможно, он поврежден: " + indexPath, e);
        }
    }

    private void validateIndexPath() throws IndexUnavailableException {
        if (!Files.exists(indexPath)) {
            throw new IndexUnavailableException("Индекс не найден: " + indexPath);
        }
        if (!Files.isDirectory(indexPath)) {
            throw new IndexUnavailableException("Путь индекса не является директорией: " + indexPath);
        }
        try (Stream<Path> files = Files.list(indexPath)) {
            if (files.findAny().isEmpty()) {
                throw new IndexUnavailableException("Индекс пуст: " + indexPath);
            }
        } catch (IndexUnavailableException e) {
            throw e;
        } catch (IOException e) {
            throw new IndexUnavailableException("Не удалось прочитать директорию индекса: " + indexPath, e);
        }
    }

    @FunctionalInterface
    private interface ReaderCallback<T> {
        T apply(IndexReader reader) throws IOException;
    }
}
