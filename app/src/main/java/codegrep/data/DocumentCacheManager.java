package codegrep.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.util.QueryBuilder;

import codegrep.util.FileUtils;
import grammars.CodeGrepGrammarFacade;
import grammars.ExtendedParseTreeListener;

public class DocumentCacheManager {

    public static final String CACHE_PATH = ".codegrep";

    private final IndexManager index;
    private final Map<Path, Long> invalidPaths = new HashMap<>();

    public DocumentCacheManager() {
        IndexManager mgr = null;
        try {
            mgr = new IndexManager(Path.of(CACHE_PATH));
        } catch (IOException e) {
            System.err.println("Fatal Error: Unable to create an index manager");
            e.printStackTrace();
            System.exit(1);
        }

        this.index = mgr;
    }

    public void revalidate(Path p, CodeGrepGrammarFacade grammar) {
        FileUtils.processFiles(p, e -> grammar.isValidFile(e) && !e.toString().contains(CACHE_PATH), this::revalidateFile);
        if (invalidPaths.isEmpty()) return;

        System.err.println("Revalidating cache...");

        ExtendedParseTreeListener listener = grammar.createListener();
        for (Path revalid : invalidPaths.keySet()) {
            listener.setPath(revalid);
            try {
                ParseTreeWalker walker = new ParseTreeWalker();
                walker.walk(listener, grammar.parse(revalid.toFile()));
            } catch (IOException e) {
                System.err.printf("Unable to read the file %s%n", revalid);
            }
        }

        Map<Path, List<Document>> docs = listener.getProjectContents().getDocuments();
        for (var doc : docs.entrySet()) {
            try {
                index.setDocuments(doc.getKey(), invalidPaths.get(doc.getKey()), doc.getValue());
            } catch (IOException e) {
                System.err.printf("Unable to update the cache for the file %s%n", doc.getKey());
            }
        }

        try {
            index.save();
        } catch (IOException e) {
            System.err.println("Unable to save the index...");
        }
        
        System.err.println("Cache done!");
    }

    private void revalidateFile(Path p) {
        long t = 0;
        try {
            t = Files.getLastModifiedTime(p).toMillis();
            long cachedTime = index.getUpdateTime(p);
            // If the modification date is earlier than the cached time, we don't need to regen the cache
            if (t < cachedTime) return;
        } catch (IOException e) {
            System.err.println("Cannot read the file " + p.toString());
            return;
        }

        invalidPaths.put(p, t);
    }

    public Map<Path, List<FileReference>> search(String s) {
        
        Query q1 = new QueryBuilder(index.analyzer).createPhraseQuery("content", s);
        Query q2 = new TermQuery(new Term("type", ObjectType.STRING_LITERAL.toString()));
        Query combo = new BooleanQuery.Builder().add(q1, Occur.MUST).add(q2, Occur.MUST).build();
        List<Document> docs = null;
        try {
            docs = index.getDocuments(combo);
        } catch (IOException e) {
            System.err.println("Cannot do a search :(");
            return new HashMap<>();
        }

        return docs.stream().map(ProjectContents::convertToFileRef).collect(Collectors.groupingBy(FileReference::path));
    }

}
