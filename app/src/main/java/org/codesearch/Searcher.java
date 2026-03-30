package org.codesearch;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.MMapDirectory;

import org.codesearch.Units.Unit;
import org.json.JSONObject;

public class Searcher {
    private static Logger logger = LogManager.getLogger();

    public List<Unit> runQuery(Path indexPath, List<String> keys, boolean isFuzzy, int numTopDocs) 
        throws IOException {
        try (MMapDirectory dir = new MMapDirectory(indexPath);
             IndexReader reader = DirectoryReader.open(dir)) {
        
            IndexSearcher searcher = new IndexSearcher(reader);
            BooleanQuery.Builder query = new BooleanQuery.Builder();
            for (String key: keys) {
                Term term = new Term("key", key);
                query.add(isFuzzy ? 
                    new FuzzyQuery(term, 2) : 
                    new TermQuery(term), BooleanClause.Occur.MUST);
            }

            TopDocs results = searcher.search(query.build(), numTopDocs);
            List<Unit> res = new ArrayList<>();
            
            for (ScoreDoc scoreDoc: results.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                try {
                    res.add(new Unit(
                        doc.get("file"), 
                        Integer.parseInt(doc.get("line")),
                        Integer.parseInt(doc.get("position")),
                        new JSONObject(doc.get("json")),
                        List.of(doc.getValues("keys")))
                    );
                } catch (Exception e) {
                    logger.error("unit initialization error on doc", e);
                }
            }

            return res;
        }
        catch (IOException e) {
            logger.error("index initialization error", e);
            throw e;
        }
        catch (IndexSearcher.TooManyClauses e) {
            logger.error("query initialization error", e);
            throw e;
        }
    }
}
