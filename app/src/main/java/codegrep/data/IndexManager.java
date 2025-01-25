package codegrep.data;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class IndexManager {
    
    private final Directory storeDirectory;
    private final Directory metaDirectory;
    public final Analyzer analyzer = new StandardAnalyzer();
    private final IndexWriterConfig config = new IndexWriterConfig(analyzer).setOpenMode(OpenMode.CREATE_OR_APPEND);
    private final IndexWriterConfig metaConfig = new IndexWriterConfig(analyzer).setOpenMode(OpenMode.CREATE_OR_APPEND);

    private IndexReader reader;
    private final IndexReader metaReader;
    private final IndexWriter writer;
    private final IndexWriter metaWriter;
    private IndexSearcher searcher;
    private final IndexSearcher metaSearcher;

    private IndexManager(Directory store, Directory meta) throws IOException {
        storeDirectory = store;
        metaDirectory = meta;

        writer = new IndexWriter(storeDirectory, config);
        metaWriter = new IndexWriter(metaDirectory, metaConfig);
        writer.commit();
        metaWriter.commit();

        metaReader = DirectoryReader.open(metaDirectory);
        metaSearcher = new IndexSearcher(metaReader);
        reader = DirectoryReader.open(storeDirectory);
        searcher = new IndexSearcher(reader);
    }

    public IndexManager(Path cachePath) throws IOException {
        this(FSDirectory.open(cachePath.resolve("code")), FSDirectory.open(cachePath.resolve("metadata")));
    }

    private List<Document> resolveDocs(IndexSearcher which, Query q, int limit) throws IOException {
        TopDocs docs = which.search(q, limit);
        List<Document> documents = new ArrayList<>();
        for (ScoreDoc scoreDoc : docs.scoreDocs) {
            documents.add(which.storedFields().document(scoreDoc.doc));
        }
        return documents;
    }

    public long getUpdateTime(Path path) throws IOException {
        Query q = new TermQuery(new Term("path", path.toString()));
        // there should only be 1 document with this path at most
        // TODO: this does not work for some reason!!
        List<Document> doc = resolveDocs(metaSearcher, q, 1);
        if (doc.isEmpty()) {
            // File was not yet cached.
            return -1;
        }
        return doc.get(0).getField("timestamp").numericValue().longValue();
    }

    public void setDocuments(Path path, long newTime, List<Document> data) throws IOException {
        Query q = new TermQuery(new Term("path", path.toString()));
        Document newDoc = new Document();
        newDoc.add(new StringField("path", path.toString(), Store.YES));
        newDoc.add(new LongField("timestamp", newTime, Store.YES));
        metaWriter.deleteDocuments(q);
        metaWriter.addDocument(newDoc);

        // the same query !!
        writer.deleteDocuments(q);
        writer.addDocuments(data);
    }

    // TODO: method to delete files from the cache

    public List<Document> getDocuments(Query q) throws IOException {
        if (reader.maxDoc() == 0) return new ArrayList<>();
        return resolveDocs(searcher, q, reader.maxDoc());
    }

    public void save() throws IOException {
        writer.commit();
        writer.close();
        metaWriter.commit();
        metaWriter.close();
        reader = DirectoryReader.open(storeDirectory);
        searcher = new IndexSearcher(reader);
    }

}
